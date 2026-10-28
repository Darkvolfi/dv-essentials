package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvmap.DVMap;
import com.darkvolfi.dvmap.MapConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
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

        public void uploadIfNeeded() {
            if (dirtyImage) {
                dirtyImage = false;
                texture.upload();
            }
        }

        @Override
        public void close() {
            Minecraft.getInstance().getTextureManager().release(location);
            texture.close();
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
        return surfaceY > pos.getY() + 5;
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
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        scanChunk(level, center.x + dx, center.z + dz, true, playerY);
                    }
                }
            }
        }

        // Scan 4 chunks per tick in a 15x15 grid around the player
        int gridRadius = 7;
        int side = gridRadius * 2 + 1;
        int total = side * side;
        for (int i = 0; i < 4; i++) {
            int idx = (scanCursor + i) % total;
            int dx = (idx % side) - gridRadius;
            int dz = (idx / side) - gridRadius;
            scanChunk(level, center.x + dx, center.z + dz, false, playerY);
            if (cave && Math.abs(dx) <= 4 && Math.abs(dz) <= 4) {
                scanChunk(level, center.x + dx, center.z + dz, true, playerY);
            }
        }
        scanCursor = (scanCursor + 4) % total;

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
                if (tile.image.getPixelRGBA(px, pz) != abgr) {
                    tile.image.setPixelRGBA(px, pz, abgr);
                    tile.dirtyImage = true;
                    if (!cave) tile.dirtyDisk = true;
                }
            }
        }
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
            if (state.getMapColor(level, pos) != MapColor.NONE) {
                break;
            }
        }
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
                rgb = mixRgb(floorRgb, rgb, Mth.clamp(0.5f + depth * 0.07f, 0.5f, 0.9f));
            }
            if (shadeHeight) brightness = Mth.clamp(1.1f - depth * 0.022f, 0.68f, 1.1f);
            pos.set(wx, y, wz);
        } else if (shadeHeight && !level.dimensionType().hasCeiling()) {
            // Light from the north-west: slopes facing it are brighter, the far side darker.
            int northY = topBlockY(level, wx, wz - 1, y);
            int westY = topBlockY(level, wx - 1, wz, y);
            int slope = Mth.clamp(2 * y - northY - westY, -8, 8);
            brightness = 1.0f + slope * 0.04f;
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

        r = Mth.clamp(Math.round(r * brightness), 0, 255);
        g = Mth.clamp(Math.round(g * brightness), 0, 255);
        b = Mth.clamp(Math.round(b * brightness), 0, 255);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static int sampleCaveColumn(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos,
                                        int wx, int wz, int refY, boolean shadeHeight, boolean shadeLight) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int searchTop = Mth.clamp(refY + 10, minY + 1, maxY);
        int searchBottom = Mth.clamp(refY - 18, minY, maxY - 1);

        // Find an air/passable space near refY and then the floor beneath it
        int floorY = Integer.MIN_VALUE;
        BlockState floorState = Blocks.AIR.defaultBlockState();
        boolean sawAir = false;
        for (int y = searchTop; y >= searchBottom; y--) {
            pos.set(wx, y, wz);
            BlockState s = chunk.getBlockState(pos);
            if (s.isAir() || !s.blocksMotion() && s.getFluidState().isEmpty()) {
                sawAir = true;
            } else if (sawAir && s.getMapColor(level, pos) != MapColor.NONE) {
                floorY = y;
                floorState = s;
                break;
            }
        }

        if (floorY == Integer.MIN_VALUE) {
            // Solid rock wall in this slice
            return 0xFF16141F;
        }

        pos.set(wx, floorY, wz);
        int rgb = polish(resolveBlockRgb(level, pos, floorState, true));
        float brightness = 0.92f;
        if (shadeHeight) {
            int rel = Mth.clamp(floorY - refY, -12, 12);
            brightness = 0.90f + rel * 0.022f;
        }

        int r = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) * brightness), 0, 255);
        int g = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) * brightness), 0, 255);
        int b = Mth.clamp(Math.round((rgb & 0xFF) * brightness), 0, 255);

        if (shadeLight) {
            pos.set(wx, floorY + 1, wz);
            int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
            if (blockLight > 0) {
                float glow = blockLight / 15.0f;
                r = Mth.clamp((int) (r + glow * 45), 0, 255);
                g = Mth.clamp((int) (g + glow * 28), 0, 255);
                b = Mth.clamp((int) (b + glow * 10), 0, 255);
            }
        }
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /** 0.5 at night to 1.0 at day, for dimming the map with the sky; 1.0 when turned off or without a sky. */
    public static float dayLight(Minecraft mc, float partialTick) {
        if (!MapConfig.DAY_NIGHT.get() || mc.level == null || !mc.level.dimensionType().hasSkyLight()) return 1.0f;
        float sky = (mc.level.getSkyDarken(partialTick) - 0.2f) / 0.8f;
        return 0.5f + 0.5f * Mth.clamp(sky, 0.0f, 1.0f);
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
        r = (luma + (r - luma) * 1.25f) * 1.08f;
        g = (luma + (g - luma) * 1.25f) * 1.08f;
        b = (luma + (b - luma) * 1.25f) * 1.08f;
        return Mth.clamp(Math.round(r), 0, 255) << 16 | Mth.clamp(Math.round(g), 0, 255) << 8 | Mth.clamp(Math.round(b), 0, 255);
    }

    /** Y of the top block at (wx, wz), or {@code fallback} when its chunk is not loaded. */
    private static int topBlockY(ClientLevel level, int wx, int wz, int fallback) {
        LevelChunk chunk = level.getChunkSource().getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
        if (chunk == null) return fallback;
        return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15) - 1;
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

    public static String resolveWorldId(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) {
            return "sp_" + sanitize(mc.getSingleplayerServer().getWorldData().getLevelName());
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
