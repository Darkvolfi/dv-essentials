package com.darkvolfi.dvlight.client;

import com.darkvolfi.dvlight.mixin.LevelRendererAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The moving lights the world is drawn with. The client tick publishes a new set of lights; chunk meshes near
 * the ones that changed are rebuilt, and every light lookup (blocks, also on Embeddium's builder threads, and
 * entities) reads the same immutable snapshot, so neighbouring chunks never disagree.
 */
public final class DynamicLights {
    /**
     * One light, centred at (x, y, z): a block centre in Fast mode, finer in Fancy so blocks light up one by
     * one as it moves instead of all at once. A cone shines toward (dx, dy, dz), a unit vector.
     */
    public record Light(float x, float y, float z, int level, boolean cone, float dx, float dy, float dz) {
        int blockX() { return Mth.floor(x); }
        int blockY() { return Mth.floor(y); }
        int blockZ() { return Mth.floor(z); }
    }

    private record Snapshot(Light[] lights, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                            int coneRange, double cosEdge, double cosFull, boolean fine) {}

    private static final Snapshot EMPTY = new Snapshot(new Light[0], 1, 1, 1, 0, 0, 0, 0, 1, 1, false);
    private static volatile Snapshot snapshot = EMPTY;
    private static Map<Integer, Light> published = new HashMap<>();
    private static int publishedConeRange, publishedConeAngle;
    private static boolean publishedFine;

    private DynamicLights() {}

    /** Packed light with the moving light at {@code pos} added to its block light. */
    public static int apply(int packed, BlockPos pos) {
        Snapshot s = snapshot;
        if (s.lights.length == 0) return packed;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (x < s.minX || x > s.maxX || y < s.minY || y > s.maxY || z < s.minZ || z > s.maxZ) return packed;
        // The low 16 bits hold block light in 16ths of a level (0-240), the lightmap's texture coordinate.
        // Fancy uses the in-between steps too, so brightness changes smoothly instead of a level at a time.
        int block = packed & 0xFFFF;
        if (block >= 240) return packed;
        double level = level(s, x, y, z);
        int moving = s.fine ? (int) Math.round(level * 16) : (int) Math.round(level) << 4;
        return moving > block ? (packed & 0xFFFF0000) | Math.min(moving, 240) : packed;
    }

    private static double level(Snapshot s, int x, int y, int z) {
        double best = 0;
        for (Light l : s.lights) {
            double ax = x + 0.5 - l.x, ay = y + 0.5 - l.y, az = z + 0.5 - l.z;
            double reach = (l.cone ? s.coneRange : l.level) + 1;
            if (Math.abs(ax) > reach || Math.abs(ay) > reach || Math.abs(az) > reach) continue;
            // Like a placed light block: one level less per block step, so a held torch reaches as far as a placed one
            double steps = Math.abs(ax) + Math.abs(ay) + Math.abs(az);
            double v;
            if (!l.cone) {
                v = l.level - steps;
            } else {
                // A little light around the holder, and a beam that fades with distance and toward its edge
                double dist = Math.sqrt(ax * ax + ay * ay + az * az);
                v = l.level - dist * 2;
                if (dist > 0) {
                    double cos = (ax * l.dx + ay * l.dy + az * l.dz) / dist;
                    if (cos > s.cosEdge) {
                        double edge = cos >= s.cosFull ? 1 : (cos - s.cosEdge) / (s.cosFull - s.cosEdge);
                        v = Math.max(v, l.level * (1 - dist / s.coneRange) * edge);
                    }
                }
            }
            if (v > best) {
                best = v;
                if (best >= 15) return 15;
            }
        }
        return best;
    }

    /**
     * Makes {@code wanted} (keyed by entity id) the lights the world is drawn with, and rebuilds the chunk
     * meshes around every light that appeared, went out or changed.
     */
    static void publish(Map<Integer, Light> wanted, int coneAngle, int coneRange, boolean fine) {
        Minecraft mc = Minecraft.getInstance();
        LongSet sections = new LongOpenHashSet();
        boolean coneChanged = coneRange != publishedConeRange || coneAngle != publishedConeAngle;
        boolean fineChanged = fine != publishedFine;
        Set<Integer> keys = new HashSet<>(published.keySet());
        keys.addAll(wanted.keySet());
        for (Integer key : keys) {
            Light before = published.get(key), after = wanted.get(key);
            boolean coneMoved = coneChanged && (before != null && before.cone || after != null && after.cone);
            if (Objects.equals(before, after) && !coneMoved && !fineChanged) continue;
            if (before != null) addSections(sections, before, publishedConeRange);
            if (after != null) addSections(sections, after, coneRange);
        }
        if (sections.isEmpty() && !coneChanged && !fineChanged) return;

        published = new HashMap<>(wanted);
        publishedConeRange = coneRange;
        publishedConeAngle = coneAngle;
        publishedFine = fine;
        snapshot = build(wanted, coneAngle, coneRange, fine);
        if (mc.levelRenderer != null) {
            LevelRendererAccessor renderer = (LevelRendererAccessor) mc.levelRenderer;
            sections.forEach((long section) -> renderer.dvlight$setSectionDirty(
                    SectionPos.x(section), SectionPos.y(section), SectionPos.z(section), true));
        }
    }

    private static Snapshot build(Map<Integer, Light> lights, int coneAngle, int coneRange, boolean fine) {
        if (lights.isEmpty()) return EMPTY;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Light l : lights.values()) {
            int r = reach(l, coneRange) + 1;
            minX = Math.min(minX, l.blockX() - r); maxX = Math.max(maxX, l.blockX() + r);
            minY = Math.min(minY, l.blockY() - r); maxY = Math.max(maxY, l.blockY() + r);
            minZ = Math.min(minZ, l.blockZ() - r); maxZ = Math.max(maxZ, l.blockZ() + r);
        }
        double half = Math.toRadians(coneAngle / 2.0);
        return new Snapshot(lights.values().toArray(new Light[0]), minX, minY, minZ, maxX, maxY, maxZ,
                coneRange, Math.cos(half), Math.cos(half * 0.6), fine);
    }

    private static int reach(Light l, int coneRange) {
        return l.cone ? coneRange : l.level;
    }

    private static void addSections(LongSet sections, Light l, int coneRange) {
        int r = reach(l, coneRange) + 2;
        int x = l.blockX(), y = l.blockY(), z = l.blockZ();
        for (int sx = SectionPos.blockToSectionCoord(x - r); sx <= SectionPos.blockToSectionCoord(x + r); sx++)
            for (int sy = SectionPos.blockToSectionCoord(y - r); sy <= SectionPos.blockToSectionCoord(y + r); sy++)
                for (int sz = SectionPos.blockToSectionCoord(z - r); sz <= SectionPos.blockToSectionCoord(z + r); sz++)
                    sections.add(SectionPos.asLong(sx, sy, sz));
    }

    /** Drops every light without touching chunk meshes (the world is gone or being replaced). */
    static void forget() {
        published = new HashMap<>();
        publishedConeRange = publishedConeAngle = 0;
        publishedFine = false;
        snapshot = EMPTY;
    }

    /** A setting changed: turn every light off now; the next tick lights them again with the new settings. */
    public static void settingsChanged() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level == null) forget();
            else publish(Map.of(), 0, 0, false);
        });
    }
}
