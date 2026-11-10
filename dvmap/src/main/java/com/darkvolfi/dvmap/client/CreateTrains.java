package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVColorPopup;
import com.darkvolfi.dvmap.MapConfig;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Create's tracks and trains on the world map, in the chunks the client has loaded. They are recognised by registry
 * name only (track blocks "create:*track*", trains the "create:carriage_contraption" entity): no Create classes are
 * touched, and without Create nothing here runs.
 */
final class CreateTrains {
    private static final String CREATE = "create";
    private static final int CHUNKS_PER_TICK = 8;

    /** Track columns of each loaded chunk: 256 bits, bit (z * 16 + x). */
    private static final Map<Long, long[]> TRACKS = new HashMap<>();
    private static Boolean installed;
    private static Set<Block> trackBlocks;
    private static EntityType<?> carriage;
    private static ResourceKey<Level> dimension;
    private static int cursor;

    private CreateTrains() {}

    static boolean active() {
        return installed() && MapConfig.SHOW_CREATE_TRAINS.get();
    }

    static boolean installed() {
        if (installed == null) installed = ModList.get().isLoaded(CREATE);
        return installed;
    }

    /** Scans a few loaded chunks per tick for track blocks, cycling through the render distance. */
    static void tick(Minecraft mc) {
        if (!active() || mc.level == null || mc.player == null) {
            if (!TRACKS.isEmpty()) tracksVersion++;
            TRACKS.clear();
            TRAIN_IDS.clear();
            return;
        }
        if (trackBlocks == null) findTypes();
        ClientLevel level = mc.level;
        if (level.dimension() != dimension) {
            dimension = level.dimension();
            if (!TRACKS.isEmpty()) tracksVersion++;
            TRACKS.clear();
            TRAIN_IDS.clear();
            cursor = 0;
        }
        int r = mc.options.getEffectiveRenderDistance() + 1;
        int side = 2 * r + 1;
        int total = side * side;
        ChunkPos center = mc.player.chunkPosition();
        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            int idx = (cursor + i) % total;
            scan(level, center.x + idx % side - r, center.z + idx / side - r);
        }
        int next = (cursor + CHUNKS_PER_TICK) % total;
        if (next < cursor) {
            // A full round: forget chunks that have been unloaded since.
            if (TRACKS.keySet().removeIf(key -> !level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)))) tracksVersion++;
        }
        cursor = next;
    }

    private static void findTypes() {
        Set<Block> blocks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<ResourceKey<Block>, Block> e : BuiltInRegistries.BLOCK.entrySet()) {
            ResourceLocation id = e.getKey().location();
            // The rails themselves only: stations, signals and observers stand beside the track and would blot the line.
            String path = id.getPath();
            if (id.getNamespace().equals(CREATE) && path.contains("track")
                    && !path.contains("station") && !path.contains("signal") && !path.contains("observer")) blocks.add(e.getValue());
        }
        trackBlocks = blocks;
        carriage = BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(CREATE, "carriage_contraption")).orElse(null);
    }

    private static void scan(ClientLevel level, int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null || chunk.isEmpty() || trackBlocks.isEmpty()) {
            if (TRACKS.remove(key) != null) tracksVersion++;
            return;
        }
        long[] bits = null;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir() || !section.maybeHas(s -> trackBlocks.contains(s.getBlock()))) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!trackBlocks.contains(section.getBlockState(x, y, z).getBlock())) continue;
                        if (bits == null) bits = new long[4];
                        int i = z * 16 + x;
                        bits[i >> 6] |= 1L << (i & 63);
                    }
                }
            }
        }
        long[] old = bits == null ? TRACKS.remove(key) : TRACKS.put(key, bits);
        if (!Arrays.equals(old, bits)) tracksVersion++;
    }

    // --- Named lines: every track block joined to a labelled anchor takes the label's name and colour ---

    private static final int MAX_LINE_BLOCKS = 50000;
    private static final Map<Long, RailLabels.Line> LINE_OF = new HashMap<>();
    private static int tracksVersion;
    private static int linesBuiltFor = -1;
    private static int labelsBuiltFor = -1;

    private static long cell(int bx, int bz) {
        return ((long) bx << 32) | (bz & 0xFFFFFFFFL);
    }

    /** The named line this track block belongs to, or null. */
    static RailLabels.Line lineAt(int bx, int bz) {
        rebuildLines();
        return LINE_OF.get(cell(bx, bz));
    }

    /** A track block at or right next to this block (tracks are drawn wider than one block), or null. */
    static int[] trackNear(int bx, int bz) {
        if (!active()) return null;
        if (isTrack(bx, bz)) return new int[] {bx, bz};
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (isTrack(bx + dx, bz + dz)) return new int[] {bx + dx, bz + dz};
            }
        }
        return null;
    }

    private static void rebuildLines() {
        if (linesBuiltFor == tracksVersion && labelsBuiltFor == RailLabels.version()) return;
        linesBuiltFor = tracksVersion;
        labelsBuiltFor = RailLabels.version();
        LINE_OF.clear();
        if (dimension == null) return;
        String dim = dimension.location().toString();
        for (RailLabels.Line line : RailLabels.lines()) {
            if (!dim.equals(line.dimension) || !isTrack(line.x, line.z) || LINE_OF.containsKey(cell(line.x, line.z))) continue;
            // Flood through touching track blocks (diagonals too), the same joins the map draws.
            ArrayDeque<long[]> todo = new ArrayDeque<>();
            todo.add(new long[] {line.x, line.z});
            LINE_OF.put(cell(line.x, line.z), line);
            int count = 0;
            while (!todo.isEmpty() && count++ < MAX_LINE_BLOCKS) {
                long[] p = todo.poll();
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = (int) p[0] + dx, nz = (int) p[1] + dz;
                        if (!isTrack(nx, nz) || LINE_OF.containsKey(cell(nx, nz))) continue;
                        LINE_OF.put(cell(nx, nz), line);
                        todo.add(new long[] {nx, nz});
                    }
                }
            }
        }
    }

    /** Forgets everything of the world just left, so its tracks and trains never show in the next one. */
    static void reset() {
        TRACKS.clear();
        TRAIN_IDS.clear();
        dimension = null;
        cursor = 0;
        tracksVersion++;
    }

    static int trackColor() {
        int rgb = DVColorPopup.parseHex(MapConfig.CREATE_TRACK_COLOR.get());
        return 0xFF000000 | (rgb < 0 ? 0xFFAA33 : rgb);
    }

    /** Neighbours a track block is joined to on the map: east, south and both diagonals below (each pair drawn once). */
    private static final int[][] LINKS = {{1, 0}, {0, 1}, {1, 1}, {-1, 1}};

    /**
     * Tracks as a thick line on their own layer: neighbouring track blocks are joined, first with a dark outline, then
     * in the track colour on top, so the line stands out from the terrain at every zoom.
     */
    static void renderTracks(GuiGraphics g, WorldMapScreen screen, ClientLevel level, int minCx, int minCz, int maxCx, int maxCz, float zoom) {
        if (!active() || TRACKS.isEmpty()) return;
        rebuildLines();
        int width = Math.max(2, Math.round(zoom * 0.4f));
        renderTrackPass(g, screen, level, minCx, minCz, maxCx, maxCz, width + 2, 0xFF1A1420, false);
        renderTrackPass(g, screen, level, minCx, minCz, maxCx, maxCz, width, trackColor(), true);
    }

    /** With {@code byLine}, blocks of a named line are drawn in that line's colour instead of {@code color}. */
    private static void renderTrackPass(GuiGraphics g, WorldMapScreen screen, ClientLevel level,
                                        int minCx, int minCz, int maxCx, int maxCz, int width, int defaultColor, boolean byLine) {
        for (Map.Entry<Long, long[]> e : TRACKS.entrySet()) {
            int cx = ChunkPos.getX(e.getKey());
            int cz = ChunkPos.getZ(e.getKey());
            if (cx < minCx || cx > maxCx || cz < minCz || cz > maxCz || !level.hasChunk(cx, cz)) continue;
            long[] bits = e.getValue();
            for (int i = 0; i < 256; i++) {
                if ((bits[i >> 6] & (1L << (i & 63))) == 0) continue;
                int bx = (cx << 4) + (i & 15);
                int bz = (cz << 4) + (i >> 4);
                RailLabels.Line line = byLine ? LINE_OF.get(cell(bx, bz)) : null;
                int color = line != null ? 0xFF000000 | line.color : defaultColor;
                float sx = screen.worldToScreenX(bx + 0.5);
                float sy = screen.worldToScreenY(bz + 0.5);
                // Off screen (the chunk test above is coarse): nothing to draw.
                if (sx < -16 || sy < -16 || sx > screen.width + 16 || sy > screen.height + 16) continue;
                square(g, sx, sy, width, color);
                for (int[] link : LINKS) {
                    if (!isTrack(bx + link[0], bz + link[1])) continue;
                    // A diagonal next to a straight joint would fill in a corner and bulge the line: skip it.
                    if (link[0] != 0 && link[1] != 0 && (isTrack(bx + link[0], bz) || isTrack(bx, bz + link[1]))) continue;
                    float ex = screen.worldToScreenX(bx + link[0] + 0.5);
                    float ey = screen.worldToScreenY(bz + link[1] + 0.5);
                    // Squares along the joint, at most a pixel apart, so diagonals stay as thick as straight runs.
                    int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(ex - sx), Math.abs(ey - sy))));
                    for (int s = 1; s <= steps; s++) {
                        float t = s / (float) steps;
                        square(g, sx + (ex - sx) * t, sy + (ey - sy) * t, width, color);
                    }
                }
            }
        }
    }

    private static void square(GuiGraphics g, float x, float y, int size, int color) {
        int x0 = Math.round(x - size / 2f);
        int y0 = Math.round(y - size / 2f);
        g.fill(x0, y0, x0 + size, y0 + size, color);
    }

    private static boolean isTrack(int bx, int bz) {
        long[] bits = TRACKS.get(ChunkPos.asLong(bx >> 4, bz >> 4));
        if (bits == null) return false;
        int i = (bz & 15) * 16 + (bx & 15);
        return (bits[i >> 6] & (1L << (i & 63))) != 0;
    }

    /**
     * Each carriage as the area it covers: a dark box with a lighter inside, like a wagon seen from above. When the
     * map is zoomed out too far for that, a train icon instead. Returns the carriage under the mouse, or null.
     */
    static Entity renderTrains(GuiGraphics g, WorldMapScreen screen, ClientLevel level, float partialTick, int mouseX, int mouseY) {
        if (!active() || carriage == null) return null;
        Entity hovered = null;
        for (Entity e : level.entitiesForRendering()) {
            if (e.getType() != carriage) continue;
            double ox = Mth.lerp(partialTick, e.xo, e.getX()) - e.getX();
            double oz = Mth.lerp(partialTick, e.zo, e.getZ()) - e.getZ();
            AABB box = e.getBoundingBox();
            RailLabels.Train label = RailLabels.train(trainId(e));
            float sx = screen.worldToScreenX(box.getCenter().x + ox);
            float sy = screen.worldToScreenY(box.getCenter().z + oz);
            float perBlock = screen.worldToScreenX(1) - screen.worldToScreenX(0);
            // The box Create gives a carriage is padded and axis-aligned, far too big for a wagon. Draw the wagon at its
            // real size (from its blocks), turned the way the track under it runs.
            CarriageInfo info = info(e);
            double angle = trackAngle(e, box.getCenter().x, box.getCenter().z);
            // Never smaller than a readable little wagon, also when zoomed out.
            float halfLen = Math.max((float) (info.length() * perBlock / 2), 4f);
            float halfWid = Math.max((float) (info.width() * perBlock / 2), 2.5f);
            int body = label != null ? 0xFF000000 | label.color : 0xFF4A4852;
            g.pose().pushPose();
            g.pose().translate(sx, sy, 0);
            g.pose().mulPose(Axis.ZP.rotation((float) angle));
            int l = Math.round(halfLen), w = Math.round(halfWid);
            g.fill(-l - 1, -w - 1, l + 1, w + 1, 0xFF0E0C12);
            g.fill(-l + 1, -w + 1, l - 1, w - 1, body);
            g.fill(-l + 1, -w + 1, l - 1, -w + 2, 0x40FFFFFF); // light edge
            g.pose().popPose();
            // Hover in the wagon's own frame.
            double mx = mouseX - sx, my = mouseY - sy;
            double along = mx * Math.cos(angle) + my * Math.sin(angle);
            double across = -mx * Math.sin(angle) + my * Math.cos(angle);
            if (hovered == null && Math.abs(along) <= halfLen && Math.abs(across) <= halfWid) hovered = e;
        }
        return hovered;
    }

    /** The name shown for a carriage: the one the player gave its train, else the game's own. */
    static Component trainName(Entity carriage) {
        RailLabels.Train label = RailLabels.train(trainId(carriage));
        return label != null && label.name != null ? Component.literal(label.name) : carriage.getDisplayName();
    }

    /** What the map needs of a carriage, read once from its saved data: its train, and its length and width in blocks. */
    private record CarriageInfo(String trainId, double length, double width, boolean complete, long retryAt) {}

    private static final Map<Integer, CarriageInfo> TRAIN_IDS = new HashMap<>();
    /** A carriage that has only just appeared may not have its data yet: read again this often (ms) until it does. */
    private static final long RETRY_MS = 2000;

    static String trainId(Entity carriage) {
        return info(carriage).trainId();
    }

    /**
     * Create saves a carriage's train as "TrainId", and its blocks under Contraption/Blocks/BlockList, each with a
     * packed "Pos" relative to the carriage. The blocks give the wagon's real size, which the entity's padded box does
     * not. Anything unreadable falls back: the carriage's own id, a wagon of 4 by 1.5 blocks.
     */
    private static CarriageInfo info(Entity carriage) {
        CarriageInfo known = TRAIN_IDS.get(carriage.getId());
        long now = System.currentTimeMillis();
        if (known != null && (known.complete() || now < known.retryAt())) return known;

        String train = carriage.getUUID().toString();
        double length = 4, width = 1.5;
        boolean hasTrain = false, hasBlocks = false;
        try {
            CompoundTag tag = carriage.saveWithoutId(new CompoundTag());
            if (tag.hasUUID("TrainId")) {
                train = tag.getUUID("TrainId").toString();
                hasTrain = true;
            }
            ListTag blocks = tag.getCompound("Contraption").getCompound("Blocks").getList("BlockList", Tag.TAG_COMPOUND);
            if (!blocks.isEmpty()) {
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (int i = 0; i < blocks.size(); i++) {
                    BlockPos p = BlockPos.of(blocks.getCompound(i).getLong("Pos"));
                    minX = Math.min(minX, p.getX());
                    maxX = Math.max(maxX, p.getX());
                    minZ = Math.min(minZ, p.getZ());
                    maxZ = Math.max(maxZ, p.getZ());
                }
                int a = maxX - minX + 1, b = maxZ - minZ + 1;
                length = Math.max(a, b);
                width = Mth.clamp(Math.min(a, b), 1, 3);
                hasBlocks = true;
            }
        } catch (RuntimeException ignored) {
            // Not readable on the client: keep the fallbacks.
        }
        // Fallbacks are kept only for a while, never for good: a name must not be saved under a stand-in id.
        CarriageInfo read = new CarriageInfo(train, length, width, hasTrain && hasBlocks, now + RETRY_MS);
        TRAIN_IDS.put(carriage.getId(), read);
        return read;
    }

    /**
     * Which way the track runs under a carriage, as a screen angle (0 = along +x): the main direction of the track
     * blocks around its middle. Without track there, the way it moves; else along +x.
     */
    private static double trackAngle(Entity e, double cx, double cz) {
        int bx = Mth.floor(cx), bz = Mth.floor(cz);
        double n = 0, sx = 0, sz = 0, sxx = 0, szz = 0, sxz = 0;
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (!isTrack(bx + dx, bz + dz)) continue;
                n++;
                sx += dx;
                sz += dz;
                sxx += dx * dx;
                szz += dz * dz;
                sxz += dx * dz;
            }
        }
        if (n >= 3) {
            double mx = sx / n, mz = sz / n;
            double cxx = sxx / n - mx * mx, czz = szz / n - mz * mz, cxz = sxz / n - mx * mz;
            return 0.5 * Math.atan2(2 * cxz, cxx - czz);
        }
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
        return dx * dx + dz * dz > 1.0E-4 ? Math.atan2(dz, dx) : 0;
    }
}
