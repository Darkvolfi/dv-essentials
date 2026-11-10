package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVColorPopup;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvmap.MapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraftforge.fml.ModList;

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
            TRACKS.clear();
            return;
        }
        if (trackBlocks == null) findTypes();
        ClientLevel level = mc.level;
        if (level.dimension() != dimension) {
            dimension = level.dimension();
            TRACKS.clear();
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
            TRACKS.keySet().removeIf(key -> !level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)));
        }
        cursor = next;
    }

    private static void findTypes() {
        Set<Block> blocks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<ResourceKey<Block>, Block> e : BuiltInRegistries.BLOCK.entrySet()) {
            ResourceLocation id = e.getKey().location();
            if (id.getNamespace().equals(CREATE) && id.getPath().contains("track")) blocks.add(e.getValue());
        }
        trackBlocks = blocks;
        carriage = BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(CREATE, "carriage_contraption")).orElse(null);
    }

    private static void scan(ClientLevel level, int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null || chunk.isEmpty() || trackBlocks.isEmpty()) {
            TRACKS.remove(key);
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
        if (bits == null) TRACKS.remove(key);
        else TRACKS.put(key, bits);
    }

    static int trackColor() {
        int rgb = DVColorPopup.parseHex(MapConfig.CREATE_TRACK_COLOR.get());
        return 0xFF000000 | (rgb < 0 ? 0xFFAA33 : rgb);
    }

    /** Tracks as a thin line: a dot in the middle of each track block, half a block wide. */
    static void renderTracks(GuiGraphics g, WorldMapScreen screen, ClientLevel level, int minCx, int minCz, int maxCx, int maxCz, float zoom) {
        if (!active() || TRACKS.isEmpty()) return;
        int color = trackColor();
        int dot = Math.max(1, Math.round(zoom * 0.5f));
        for (Map.Entry<Long, long[]> e : TRACKS.entrySet()) {
            int cx = ChunkPos.getX(e.getKey());
            int cz = ChunkPos.getZ(e.getKey());
            if (cx < minCx || cx > maxCx || cz < minCz || cz > maxCz || !level.hasChunk(cx, cz)) continue;
            long[] bits = e.getValue();
            for (int i = 0; i < 256; i++) {
                if ((bits[i >> 6] & (1L << (i & 63))) == 0) continue;
                double wx = (cx << 4) + (i & 15) + 0.5;
                double wz = (cz << 4) + (i >> 4) + 0.5;
                int sx = Math.round(screen.worldToScreenX(wx)) - dot / 2;
                int sy = Math.round(screen.worldToScreenY(wz)) - dot / 2;
                g.fill(sx, sy, sx + dot, sy + dot, color);
            }
        }
    }

    /** Draws a train icon on every carriage; returns the carriage under the mouse, or null. */
    static Entity renderTrains(GuiGraphics g, WorldMapScreen screen, ClientLevel level, float partialTick, int mouseX, int mouseY) {
        if (!active() || carriage == null) return null;
        Entity hovered = null;
        for (Entity e : level.entitiesForRendering()) {
            if (e.getType() != carriage) continue;
            int sx = Math.round(screen.worldToScreenX(Mth.lerp(partialTick, e.xo, e.getX())));
            int sy = Math.round(screen.worldToScreenY(Mth.lerp(partialTick, e.zo, e.getZ())));
            DVUi.icon(g, MapIcons.TRAIN, sx, sy, 1, 0xFFFFFFFF);
            if (hovered == null && Math.abs(mouseX - sx) <= 5 && Math.abs(mouseY - sy) <= 5) hovered = e;
        }
        return hovered;
    }
}
