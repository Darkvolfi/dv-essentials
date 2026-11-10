package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvmap.DVMap;
import com.darkvolfi.dvmap.MapConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages 256x256-block region textures for both the surface map (persisted as PNGs per world/dimension)
 * and the underground cave slice map.
 */
public final class MapData {
    public static final int REGION_SHIFT = 8; // 256x256 blocks per region (16x16 chunks)
    public static final int REGION_SIZE = 1 << REGION_SHIFT;
    public static final int REGION_MASK = REGION_SIZE - 1;

    private static final Map<Long, RegionTile> SURFACE_TILES = new HashMap<>();
    private static final Map<Long, RegionTile> CAVE_TILES = new HashMap<>();
    private static final Map<BlockState, int[]> BLOCK_COLORS = new HashMap<>();
    private static final Map<ResourceLocation, Integer> FLUID_COLORS = new HashMap<>();
    private static String currentWorldId = "";
    private static ResourceKey<Level> currentDimension;
    private static int scanCursor;
    private static int lastCaveBand = Integer.MIN_VALUE;

    private MapData() {}

    public static final class RegionTile implements AutoCloseable {
        public final int regionX;
        public final int regionZ;
        public final NativeImage image;
        public final DynamicTexture texture;
        public final ResourceLocation location;
        public boolean dirtyImage;
        public boolean dirtyDisk;

        RegionTile(int regionX, int regionZ, String prefix, NativeImage initialImage) {
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.image = initialImage != null ? initialImage : new NativeImage(NativeImage.Format.RGBA, REGION_SIZE, REGION_SIZE, true);
            this.texture = new DynamicTexture(this.image);
            this.location = Minecraft.getInstance().getTextureManager()
                    .register(DVMap.MOD_ID + "_" + prefix + "_" + regionX + "_" + regionZ, this.texture);
            this.texture.upload();
        }

        /**
         * Where torches, lava and the like light the ground: a warm glow drawn over the map at night only, so lit
         * places stand out from the dark. Made on first use; not saved, it fills in again as chunks are scanned.
         */
        private NativeImage lightImage;
        private DynamicTexture lightTexture;
        public ResourceLocation lightLocation;
        boolean dirtyLight;

        void setLight(int px, int pz, int abgr) {
            if (lightImage == null) {
                if (abgr == 0) return;
                lightImage = new NativeImage(NativeImage.Format.RGBA, REGION_SIZE, REGION_SIZE, true);
                lightTexture = new DynamicTexture(lightImage);
                lightLocation = Minecraft.getInstance().getTextureManager()
                        .register(DVMap.MOD_ID + "_light_" + regionX + "_" + regionZ, lightTexture);
            }
            if (lightImage.getPixelRGBA(px, pz) != abgr) {
                lightImage.setPixelRGBA(px, pz, abgr);
                dirtyLight = true;
            }
        }

        public void uploadIfNeeded() {
            if (dirtyImage) {
                dirtyImage = false;
                texture.upload();
            }
            if (dirtyLight) {
                dirtyLight = false;
                lightTexture.upload();
            }
        }

        @Override
        public void close() {
            Minecraft.getInstance().getTextureManager().release(location);
            texture.close();
            if (lightTexture != null) {
                Minecraft.getInstance().getTextureManager().release(lightLocation);
                lightTexture.close();
            }
        }
    }

    public static void ensureSession(Minecraft mc) {
        if (mc.level == null) return;
        String worldId = resolveWorldId(mc);
        ResourceKey<Level> dim = mc.level.dimension();
        if (!worldId.equals(currentWorldId) || dim != currentDimension) {
            saveAndClearAll();
            currentWorldId = worldId;
            currentDimension = dim;
            scanCursor = 0;
            lastCaveBand = Integer.MIN_VALUE;
            WaypointManager.load(worldId);
            StructureMarkers.load(worldId);
            RailLabels.load(worldId);
        }
    }

    public static String getCurrentWorldId() {
        return currentWorldId;
    }

    public static boolean isCaveModeActive(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        MapConfig.LayerMode mode = MapConfig.LAYER_MODE.get();
        if (mode == MapConfig.LayerMode.SURFACE) return false;
        if (mode == MapConfig.LayerMode.CAVE) return true;
        if (mc.level.dimensionType().hasCeiling()) return true;
        BlockPos pos = mc.player.blockPosition();
        if (mc.level.canSeeSky(pos.above())) return false;
        int surfaceY = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (surfaceY <= pos.getY() + 5) return false;
        // Standing under a tall tree is not being underground: only a roof of something other than leaves and logs counts.
        BlockPos.MutableBlockPos up = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 2; y < surfaceY; y++) {
            BlockState state = mc.level.getBlockState(up.set(pos.getX(), y, pos.getZ()));
            if (state.blocksMotion() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) return true;
        }
        return false;
    }

    public static RegionTile getTile(int regionX, int regionZ, boolean cave) {
        long key = ChunkPos.asLong(regionX, regionZ);
        if (cave) {
            return CAVE_TILES.get(key);
        }
        RegionTile tile = SURFACE_TILES.get(key);
        if (tile == null && !currentWorldId.isEmpty() && currentDimension != null) {
            Path file = regionFile(currentWorldId, currentDimension, regionX, regionZ);
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    NativeImage loaded = NativeImage.read(in);
                    if (loaded.getWidth() == REGION_SIZE && loaded.getHeight() == REGION_SIZE) {
                        tile = new RegionTile(regionX, regionZ, "surf", loaded);
                    } else {
                        loaded.close();
                    }
                } catch (IOException ignored) {
                }
            }
            if (tile != null) {
                SURFACE_TILES.put(key, tile);
            }
        }
        return tile;
    }

    private static RegionTile getOrCreateTile(int regionX, int regionZ, boolean cave) {
        RegionTile existing = getTile(regionX, regionZ, cave);
        if (existing != null) return existing;
        long key = ChunkPos.asLong(regionX, regionZ);
        RegionTile created = new RegionTile(regionX, regionZ, cave ? "cave" : "surf", null);
        if (cave) CAVE_TILES.put(key, created);
        else SURFACE_TILES.put(key, created);
        return created;
    }

    public static void tickScan(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        ensureSession(mc);

        ClientLevel level = mc.level;
        ChunkPos center = mc.player.chunkPosition();
        boolean cave = isCaveModeActive(mc);
        int playerY = mc.player.getBlockY();

        if (cave) {
            int band = playerY >> 2;
            if (band != lastCaveBand) {
                lastCaveBand = band;
                // Re-scan immediate chunks when vertical band changes significantly
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        scanChunk(level, center.x + dx, center.z + dz, true, playerY);
                    }
                }
            }
        }

        // Scan a few chunks per tick in a square around the player: as far as the setting says, or as far as the
        // game draws the world. A larger square gets more chunks per tick, so a full round stays around ten seconds.
        int gridRadius = MapConfig.MAP_RADIUS.get() > 0 ? MapConfig.MAP_RADIUS.get() : mc.options.getEffectiveRenderDistance();
        int side = gridRadius * 2 + 1;
        int total = side * side;
        int perTick = Mth.clamp(total / 180, 4, 12);
        for (int i = 0; i < perTick; i++) {
            int idx = (scanCursor + i) % total;
            int dx = (idx % side) - gridRadius;
            int dz = (idx / side) - gridRadius;
            scanChunk(level, center.x + dx, center.z + dz, false, playerY);
            if (cave && Math.abs(dx) <= 4 && Math.abs(dz) <= 4) {
                scanChunk(level, center.x + dx, center.z + dz, true, playerY);
            }
        }
        scanCursor = (scanCursor + perTick) % total;

        // Upload dirty textures for loaded tiles
        for (RegionTile tile : SURFACE_TILES.values()) tile.uploadIfNeeded();
        if (cave) {
            for (RegionTile tile : CAVE_TILES.values()) tile.uploadIfNeeded();
        }
    }

    private static void scanChunk(ClientLevel level, int chunkX, int chunkZ, boolean cave, int refY) {
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null || chunk.isEmpty()) return;

        int regionX = chunkX >> 4;
        int regionZ = chunkZ >> 4;
        RegionTile tile = getOrCreateTile(regionX, regionZ, cave);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int localBaseX = baseX & REGION_MASK;
        int localBaseZ = baseZ & REGION_MASK;

        boolean shadeHeight = MapConfig.HEIGHT_SHADING.get();
        boolean blendBiomes = MapConfig.BIOME_BLENDING.get();
        boolean shadeLight = MapConfig.BLOCK_LIGHT_SHADING.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            int wz = baseZ + lz;
            for (int lx = 0; lx < 16; lx++) {
                int wx = baseX + lx;
                int abgr = cave
                        ? sampleCaveColumn(level, chunk, pos, wx, wz, refY, shadeHeight, shadeLight)
                        : sampleSurfaceColumn(level, chunk, pos, wx, wz, shadeHeight, blendBiomes, shadeLight);
                int px = localBaseX + lx;
                int pz = localBaseZ + lz;
                if (!cave) {
                    pos.set(wx, lastSurfaceY + 1, wz);
                    tile.setLight(px, pz, shadeLight ? lit(abgr, level.getBrightness(LightLayer.BLOCK, pos)) : 0);
                }
                if (tile.image.getPixelRGBA(px, pz) != abgr) {
                    tile.image.setPixelRGBA(px, pz, abgr);
                    tile.dirtyImage = true;
                    if (!cave) tile.dirtyDisk = true;
                }
            }
        }
    }

    /** Y of the block the last surface column showed; scanning is single-threaded, so the caller can read it right after. */
    private static int lastSurfaceY;

    /**
     * The night-light pixel for a map pixel: the same colour at full brightness, a touch warmer, as opaque as the
     * block light is strong. Drawn over the darkened map at night it works like the game's own light map: a lit
     * block keeps its colour and simply isn't dark, fading out with distance from the lamp.
     */
    private static int lit(int abgr, int blockLight) {
        if (blockLight <= 0) return 0;
        float t = blockLight / 15.0f;
        int alpha = Math.round(255 * (float) Math.pow(t, 1.3));
        int r = Math.min(255, Math.round((abgr & 0xFF) * 1.06f));
        int g = (abgr >> 8) & 0xFF;
        int b = Math.round(((abgr >> 16) & 0xFF) * 0.9f);
        return (alpha << 24) | (b << 16) | (g << 8) | r;
    }

    /** Grass, ferns and the like: plant-coloured, replaceable by placing a block, nothing to stand on. */
    private static boolean isGroundCover(BlockState state, ClientLevel level, BlockPos pos) {
        return state.getMapColor(level, pos) == MapColor.PLANT && state.canBeReplaced() && state.getFluidState().isEmpty()
                && !state.blocksMotion();
    }

    private static int sampleSurfaceColumn(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos,
                                           int wx, int wz, boolean shadeHeight, boolean blendBiomes, boolean shadeLight) {
        int minY = level.getMinBuildHeight();
        int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15) + 1;
        if (level.dimensionType().hasCeiling()) {
            topY = Math.min(topY, 122);
        }

        int y = topY;
        BlockState state = Blocks.AIR.defaultBlockState();
        while (y > minY) {
            y--;
            pos.set(wx, y, wz);
            state = chunk.getBlockState(pos);
            // Short grass and ferns are skipped: the ground under them is what the map shows. They cover the land
            // at random, and drawing them speckles every meadow. Flowers stay, as dots of colour.
            if (state.getMapColor(level, pos) != MapColor.NONE && !isGroundCover(state, level, pos)) {
                break;
            }
        }
        lastSurfaceY = y;
        if (y <= minY && state.getMapColor(level, pos) == MapColor.NONE) {
            return 0xFF14121C;
        }

        int rgb = resolveBlockRgb(level, pos, state, blendBiomes);
        float brightness = 1.0f;
        FluidState fluid = state.getFluidState();

        if (fluid.is(FluidTags.WATER)) {
            // See-through water: the floor shows in the shallows, deep water turns dark blue.
            int depth = 0;
            int fy = y;
            BlockState floor = null;
            while (fy > minY && depth < 24) {
                fy--;
                pos.set(wx, fy, wz);
                BlockState below = chunk.getBlockState(pos);
                if (below.getFluidState().isEmpty() && below.getMapColor(level, pos) != MapColor.NONE) {
                    floor = below;
                    break;
                }
                depth++;
            }
            if (shadeHeight && floor != null) {
                int floorRgb = resolveBlockRgb(level, pos, floor, blendBiomes);
                // Even a puddle one block deep must read as water, so the floor only tints it.
                rgb = mixRgb(floorRgb, rgb, Mth.clamp(0.72f + depth * 0.045f, 0.72f, 0.92f));
            }
            // Deeper water is clearly darker, so the shape of the bottom shows through the surface.
            if (shadeHeight) brightness = Mth.clamp(1.05f - depth * 0.04f, 0.5f, 1.05f);
            pos.set(wx, y, wz);
        } else if (shadeHeight && !level.dimensionType().hasCeiling()) {
            // Light from the north-west: a step up towards it is bright, a step down dark. Already a single block of
            // difference is a clear edge (the game's own paper maps shade a step by about 15%), then it grows slowly,
            // so stairs and terraces show as crisp lines and cliffs don't turn black.
            // Heights are those of the ground you can stand on, not of the flower or torch on it: otherwise every
            // plant is a bump and the relief drowns in speckle.
            int h = Math.min(y, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, wx & 15, wz & 15) - 1);
            int northY = topBlockY(level, wx, wz - 1, h);
            int westY = topBlockY(level, wx - 1, wz, h);
            int slope = Mth.clamp(2 * h - northY - westY, -6, 6);
            // A step down is a dark line, a step up only a faint light one: on flat ground of one colour the dark
            // lines trace every ledge like the contour lines of a map, and that is what makes depth readable.
            if (slope < 0) brightness = 1.0f - (0.24f + 0.05f * (-slope - 1));
            else if (slope > 0) brightness = 1.0f + 0.07f + 0.02f * (slope - 1);
            // Contact shadow: higher blocks to the south and east (a wall, a trunk, the rim of a pit) darken the foot
            // of it a little, so every raised thing is outlined all the way round, not only on its lit side.
            int southY = topBlockY(level, wx, wz + 1, h);
            int eastY = topBlockY(level, wx + 1, wz, h);
            int over = Math.min(Math.max(southY - h, 0) + Math.max(eastY - h, 0), 4);
            brightness -= over * 0.03f;
            // Terrain depth: higher ground a little brighter. Kept small: tree tops sit well above the ground, and a
            // large lift would push their colour to white and flatten the canopy.
            brightness *= 1.0f + Mth.clamp((h - level.getSeaLevel()) * 0.004f, -0.15f, 0.08f);
            brightness = Mth.clamp(brightness, 0.45f, 1.3f);
        }

        rgb = polish(rgb);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        if (shadeLight) {
            pos.set(wx, y + 1, wz);
            int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
            if (blockLight > 3) {
                float glow = (blockLight - 3) / 12.0f;
                r = Math.min(255, (int) (r + glow * 34));
                g = Math.min(255, (int) (g + glow * 22));
                b = Math.min(255, (int) (b + glow * 6));
            }
        }

        // Brightening multiplies as far as the strongest channel allows, which keeps the colour as rich as it was;
        // only what is left over moves towards white, so light colours (pale leaves, snow, sand) still step up
        // instead of hitting 255 and going flat.
        float gain = brightness;
        float toWhite = 0;
        int strongest = Math.max(r, Math.max(g, b));
        if (brightness > 1.0f && strongest * brightness > 255) {
            gain = Math.max(1.0f, 255.0f / Math.max(strongest, 1));
            toWhite = (brightness - gain) * 0.6f;
        }
        r = shade(r, gain, toWhite);
        g = shade(g, gain, toWhite);
        b = shade(b, gain, toWhite);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static int shade(int channel, float gain, float toWhite) {
        float c = Math.min(255, channel * gain);
        return Mth.clamp(Math.round(c + (255 - c) * toWhite), 0, 255);
    }

    private static int sampleCaveColumn(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos,
                                        int wx, int wz, int refY, boolean shadeHeight, boolean shadeLight) {
        int floorY = caveFloorY(level, chunk, pos, wx, wz, refY);
        if (floorY == Integer.MIN_VALUE) {
            // Solid rock in this slice: plain black, so the caves stand out as shapes.
            return 0xFF000000;
        }

        pos.set(wx, floorY, wz);
        BlockState floorState = chunk.getBlockState(pos);
        int rgb = polish(resolveBlockRgb(level, pos, floorState, true));
        // Caves are dark: a dim base, so that light (torches, lava, glow lichen) has room to show.
        float brightness = 0.6f;
        if (shadeHeight && floorState.getFluidState().isEmpty()) {
            brightness += Mth.clamp(floorY - refY, -12, 12) * 0.012f;
            // The same contour lines as on the surface, from the cave floor next door (rock walls draw no line).
            int northY = caveFloorAt(level, pos, wx, wz - 1, refY);
            int westY = caveFloorAt(level, pos, wx - 1, wz, refY);
            int slope = 0;
            if (northY != Integer.MIN_VALUE) slope += floorY - northY;
            if (westY != Integer.MIN_VALUE) slope += floorY - westY;
            slope = Mth.clamp(slope, -6, 6);
            if (slope < 0) brightness *= 1.0f - (0.24f + 0.05f * (-slope - 1));
            else if (slope > 0) brightness *= 1.0f + 0.07f + 0.02f * (slope - 1);
        }
        if (shadeLight) {
            // Lit places come up to full brightness, like on the night map.
            pos.set(wx, floorY + 1, wz);
            float light = (float) Math.pow(level.getBrightness(LightLayer.BLOCK, pos) / 15.0f, 1.3);
            brightness = Mth.lerp(light, brightness, Math.max(brightness, 1.05f));
        }

        int r = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) * brightness), 0, 255);
        int g = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) * brightness), 0, 255);
        int b = Mth.clamp(Math.round((rgb & 0xFF) * brightness), 0, 255);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /**
     * Y of what a cave column shows around {@code refY}: the floor under the first open space, or the surface of
     * water or lava (flooded caves have no air above them, and would otherwise look like rock). MIN_VALUE for solid rock.
     */
    private static int caveFloorY(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int wx, int wz, int refY) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int searchTop = Mth.clamp(refY + 10, minY + 1, maxY);
        int searchBottom = Mth.clamp(refY - 18, minY, maxY - 1);
        boolean sawAir = false;
        for (int y = searchTop; y >= searchBottom; y--) {
            pos.set(wx, y, wz);
            BlockState s = chunk.getBlockState(pos);
            if (!s.getFluidState().isEmpty()) return y;
            if (s.isAir() || !s.blocksMotion()) {
                sawAir = true;
            } else if (sawAir && s.getMapColor(level, pos) != MapColor.NONE) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** {@link #caveFloorY} for any column; MIN_VALUE also when its chunk isn't loaded. */
    private static int caveFloorAt(ClientLevel level, BlockPos.MutableBlockPos pos, int wx, int wz, int refY) {
        LevelChunk chunk = level.getChunkSource().getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
        return chunk == null ? Integer.MIN_VALUE : caveFloorY(level, chunk, pos, wx, wz, refY);
    }

    /** How bright unlit ground stays at midnight: dark, but the terrain is still readable. */
    private static final float NIGHT_LIGHT = 0.38f;

    /** {@link #NIGHT_LIGHT} at night to 1.0 at day, for dimming the map with the sky; 1.0 when turned off or without a sky. */
    public static float dayLight(Minecraft mc, float partialTick) {
        if (!MapConfig.DAY_NIGHT.get() || mc.level == null || !mc.level.dimensionType().hasSkyLight()) return 1.0f;
        float sky = (mc.level.getSkyDarken(partialTick) - 0.2f) / 0.8f;
        return NIGHT_LIGHT + (1.0f - NIGHT_LIGHT) * Mth.clamp(sky, 0.0f, 1.0f);
    }

    /**
     * Draws the night glow of lit places over a tile already on screen, as strong as the night is dark: nothing by
     * day, full at midnight.
     */
    public static void drawGlow(GuiGraphics g, RegionTile tile, float dayLight, int x, int y, int size) {
        drawGlow(g, tile, dayLight, x, y, size, size);
    }

    public static void drawGlow(GuiGraphics g, RegionTile tile, float dayLight, int x, int y, int width, int height) {
        float night = Mth.clamp((1.0f - dayLight) / (1.0f - NIGHT_LIGHT), 0.0f, 1.0f);
        if (night <= 0.01f || tile.lightLocation == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, night);
        g.blit(tile.lightLocation, x, y, width, height, 0, 0, REGION_SIZE, REGION_SIZE, REGION_SIZE, REGION_SIZE);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * The block's real colour: the average of its top texture (so mods' blocks get their own colours too),
     * tinted like in the world (grass, leaves, water by biome). Falls back to the vanilla map colour.
     */
    private static int resolveBlockRgb(ClientLevel level, BlockPos pos, BlockState state, boolean blendBiomes) {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
            ResourceLocation still = ext.getStillTexture(fluid, level, pos);
            int base = still == null ? -1 : FLUID_COLORS.computeIfAbsent(still, t -> averageColor(
                    Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(t)));
            if (base < 0) return state.getMapColor(level, pos).col;
            int tint = blendBiomes ? ext.getTintColor(fluid, level, pos) : ext.getTintColor();
            return multiplyRgb(base, tint);
        }

        int[] info = BLOCK_COLORS.computeIfAbsent(state, MapData::textureColor);
        if (info[0] < 0) return state.getMapColor(level, pos).col;
        if (info[1] < 0) return info[0];
        int tint = blendBiomes
                ? Minecraft.getInstance().getBlockColors().getColor(state, level, pos, info[1])
                : Minecraft.getInstance().getBlockColors().getColor(state, null, null, info[1]);
        return tint == -1 ? info[0] : multiplyRgb(info[0], tint);
    }

    /** {average rgb or -1, tint index or -1} of the face seen from above. */
    private static int[] textureColor(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> quads = model.getQuads(state, Direction.UP, random, ModelData.EMPTY, null);
        if (quads.isEmpty()) quads = model.getQuads(state, null, random, ModelData.EMPTY, null);
        TextureAtlasSprite sprite = quads.isEmpty() ? model.getParticleIcon(ModelData.EMPTY) : quads.get(0).getSprite();
        int tintIndex = quads.isEmpty() ? -1 : quads.get(0).getTintIndex();
        return new int[]{averageColor(sprite), tintIndex};
    }

    private static int averageColor(TextureAtlasSprite sprite) {
        if (sprite == null || MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name())) return -1;
        long r = 0, g = 0, b = 0, weight = 0;
        int w = sprite.contents().width();
        int h = sprite.contents().height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = sprite.getPixelRGBA(0, x, y);
                int a = abgr >>> 24;
                if (a == 0) continue;
                r += (long) (abgr & 0xFF) * a;
                g += (long) ((abgr >> 8) & 0xFF) * a;
                b += (long) ((abgr >> 16) & 0xFF) * a;
                weight += a;
            }
        }
        if (weight == 0) return -1;
        return (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
    }

    private static int multiplyRgb(int rgb, int tint) {
        int r = ((rgb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((rgb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (rgb & 0xFF) * (tint & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }

    /** Texture averages come out a bit grey and dark: lift them and add some colour. */
    private static int polish(int rgb) {
        float r = (rgb >> 16) & 0xFF;
        float g = (rgb >> 8) & 0xFF;
        float b = rgb & 0xFF;
        float luma = 0.3f * r + 0.59f * g + 0.11f * b;
        // The blocks' own colours, a little richer and a touch deeper, so grass, sand and leaves stay well apart.
        r = (luma + (r - luma) * 1.2f) * 0.93f;
        g = (luma + (g - luma) * 1.2f) * 0.93f;
        b = (luma + (b - luma) * 1.2f) * 0.93f;
        return Mth.clamp(Math.round(r), 0, 255) << 16 | Mth.clamp(Math.round(g), 0, 255) << 8 | Mth.clamp(Math.round(b), 0, 255);
    }

    /** Y of the top block at (wx, wz), or {@code fallback} when its chunk is not loaded. */
    private static int topBlockY(ClientLevel level, int wx, int wz, int fallback) {
        LevelChunk chunk = level.getChunkSource().getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
        if (chunk == null) return fallback;
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, wx & 15, wz & 15) - 1;
    }

    private static int mixRgb(int rgbA, int rgbB, float ratioB) {
        float ratioA = 1.0f - ratioB;
        int r = Math.round(((rgbA >> 16) & 0xFF) * ratioA + ((rgbB >> 16) & 0xFF) * ratioB);
        int g = Math.round(((rgbA >> 8) & 0xFF) * ratioA + ((rgbB >> 8) & 0xFF) * ratioB);
        int b = Math.round((rgbA & 0xFF) * ratioA + (rgbB & 0xFF) * ratioB);
        return (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
    }

    public static boolean isLoaded(ClientLevel level, int wx, int wz) {
        return level.getChunkSource().getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false) != null;
    }

    public static int sampleSurfaceHeight(ClientLevel level, int wx, int wz) {
        LevelChunk chunk = level.getChunkSource().getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
        if (chunk == null) return 64;
        // MOTION_BLOCKING_NO_LEAVES is server-only: the client never receives it and it reads as the world bottom.
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, wx & 15, wz & 15);
    }

    public static void saveDirtyRegions() {
        if (currentWorldId.isEmpty() || currentDimension == null) return;
        for (RegionTile tile : SURFACE_TILES.values()) {
            if (!tile.dirtyDisk) continue;
            tile.dirtyDisk = false;
            Path file = regionFile(currentWorldId, currentDimension, tile.regionX, tile.regionZ);
            try {
                Files.createDirectories(file.getParent());
                tile.image.writeToFile(file);
            } catch (IOException ignored) {
            }
        }
    }

    public static void saveAndClearAll() {
        saveDirtyRegions();
        for (RegionTile tile : SURFACE_TILES.values()) tile.close();
        for (RegionTile tile : CAVE_TILES.values()) tile.close();
        SURFACE_TILES.clear();
        CAVE_TILES.clear();
    }

    private static Path regionFile(String worldId, ResourceKey<Level> dimension, int rx, int rz) {
        return dimensionFolder(worldId, dimension).resolve("r." + rx + "." + rz + ".png");
    }

    /** Where the surface region PNGs (r.X.Z.png) of a dimension are saved. */
    static Path dimensionFolder(String worldId, ResourceKey<Level> dimension) {
        String dimFolder = sanitize(dimension.location().getNamespace() + "_" + dimension.location().getPath());
        return FMLPaths.GAMEDIR.get().resolve("dvmap").resolve(worldId).resolve(dimFolder);
    }

    /**
     * A single-player world is told apart by its save folder and its seed, not its name: worlds often share a name ("New
     * World"), and a deleted world's folder name is given to the next one made with that name.
     */
    public static String resolveWorldId(Minecraft mc) {
        IntegratedServer local = mc.getSingleplayerServer();
        if (local != null) {
            Path folder = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
            long seed = local.getWorldData().worldGenOptions().seed();
            return "sp_" + sanitize(folder == null ? local.getWorldData().getLevelName() : folder.toString())
                    + "_" + Long.toHexString(seed);
        }
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            return "mp_" + sanitize(server.ip);
        }
        return "default";
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "world";
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
