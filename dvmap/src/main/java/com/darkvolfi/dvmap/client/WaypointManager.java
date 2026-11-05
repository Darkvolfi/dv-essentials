package com.darkvolfi.dvmap.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WaypointManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Waypoint>>() {}.getType();
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static String loadedWorldId = "";

    private WaypointManager() {}

    public static void load(String worldId) {
        loadedWorldId = worldId;
        WAYPOINTS.clear();
        if (worldId == null || worldId.isEmpty()) return;
        Path file = waypointFile(worldId);
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Waypoint> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded != null) {
                WAYPOINTS.addAll(loaded);
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        if (loadedWorldId == null || loadedWorldId.isEmpty()) return;
        Path file = waypointFile(loadedWorldId);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(WAYPOINTS, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static List<Waypoint> all() {
        return Collections.unmodifiableList(WAYPOINTS);
    }

    public static List<Waypoint> forDimension(ResourceKey<Level> dim) {
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint wp : WAYPOINTS) {
            if (wp.isInDimension(dim)) result.add(wp);
        }
        return result;
    }

    public static void add(Waypoint wp) {
        WAYPOINTS.add(wp);
        save();
    }

    public static void remove(Waypoint wp) {
        WAYPOINTS.remove(wp);
        save();
    }

    public static void recordDeath(ResourceKey<Level> dim, BlockPos pos) {
        String dimStr = dim.location().toString();
        WAYPOINTS.removeIf(w -> w.deathPoint && dimStr.equals(w.dimension));
        String title = I18n.exists("dvmap.waypoint.last_death")
                ? I18n.get("dvmap.waypoint.last_death")
                : "Last Death";
        WAYPOINTS.add(new Waypoint(title, pos.getX(), pos.getY(), pos.getZ(), dimStr, 0xFF5555, true, true));
        save();
    }

    public static boolean canTeleport(Minecraft mc) {
        return mc.player != null && (mc.player.hasPermissions(2) || mc.player.isCreative());
    }

    public static void teleportTo(Minecraft mc, Waypoint wp) {
        teleport(mc, wp.dimension, wp.x, String.valueOf(wp.y), wp.z);
    }

    /** Teleports into {@code dimension} (which may not be the current one); {@code y} may be "~". */
    public static void teleport(Minecraft mc, String dimension, int x, String y, int z) {
        if (mc.player == null || mc.getConnection() == null) return;
        mc.getConnection().sendUnsignedCommand("execute in " + dimension + " run tp @s " + x + " " + y + " " + z);
    }

    private static Path waypointFile(String worldId) {
        return FMLPaths.GAMEDIR.get().resolve("dvmap").resolve(worldId).resolve("waypoints.json");
    }
}
