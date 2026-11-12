package com.darkvolfi.dvmap.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import com.darkvolfi.dvmap.DVMap;
import com.darkvolfi.dvmap.MapConfig;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
import java.util.Locale;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = DVMap.MOD_ID, value = Dist.CLIENT)
public final class WaypointManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Waypoint>>() {}.getType();
    private static final Type CATEGORY_LIST_TYPE = new TypeToken<List<WaypointCategory>>() {}.getType();
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static final List<WaypointCategory> CATEGORIES = new ArrayList<>(WaypointCategory.defaults());
    private static String loadedWorldId = "";

    private WaypointManager() {}

    /**
     * Reads both file layouts: 1.0.0 saved a bare array of waypoints (categories were fixed then), 1.0.1 saves
     * {"categories": [...], "waypoints": [...]}.
     */
    public static void load(String worldId) {
        loadedWorldId = worldId;
        WAYPOINTS.clear();
        CATEGORIES.clear();
        List<WaypointCategory> categories = null;
        if (worldId != null && !worldId.isEmpty()) {
            Path file = waypointFile(worldId);
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement root = JsonParser.parseReader(reader);
                    JsonElement waypoints = root;
                    if (root.isJsonArray()) {
                        // The next save rewrites it in the new layout, which 1.0.0 can't read: keep the old one.
                        backup(file, "waypoints-1.0.0.json");
                    } else if (root.isJsonObject()) {
                        waypoints = root.getAsJsonObject().get("waypoints");
                        categories = GSON.fromJson(root.getAsJsonObject().get("categories"), CATEGORY_LIST_TYPE);
                    }
                    List<Waypoint> loaded = waypoints == null ? null : GSON.fromJson(waypoints, LIST_TYPE);
                    if (loaded != null) {
                        for (Waypoint wp : loaded) if (wp != null) WAYPOINTS.add(wp);
                    }
                } catch (Exception e) {
                    // Unreadable: the next save would overwrite it, so keep a copy.
                    backup(file, "waypoints-unreadable.json");
                }
            }
        }
        if (categories != null) {
            for (WaypointCategory c : categories) {
                if (c == null || c.id == null || c.id.isBlank()) continue;
                c.id = c.id.toLowerCase(Locale.ROOT);
                if (findCategory(c.id) == null) CATEGORIES.add(c);
            }
        }
        if (CATEGORIES.isEmpty()) CATEGORIES.addAll(WaypointCategory.defaults());
        if (findCategory(WaypointCategory.GENERAL) == null) CATEGORIES.add(0, WaypointCategory.defaults().get(0));
        for (Waypoint wp : WAYPOINTS) {
            if (wp.category == null) continue;
            String id = wp.category.toLowerCase(Locale.ROOT);
            wp.category = findCategory(id) != null ? id : WaypointCategory.GENERAL;
        }
        // Older versions gave every world eight categories; the empty, untouched ones go.
        CATEGORIES.removeIf(c -> c.isOldBuiltIn() && WAYPOINTS.stream().noneMatch(wp -> !wp.deathPoint && c.id.equals(wp.category)));
    }

    public static void save() {
        if (loadedWorldId == null || loadedWorldId.isEmpty()) return;
        Path file = waypointFile(loadedWorldId);
        JsonObject root = new JsonObject();
        root.add("categories", GSON.toJsonTree(CATEGORIES, CATEGORY_LIST_TYPE));
        root.add("waypoints", GSON.toJsonTree(WAYPOINTS, LIST_TYPE));
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static List<WaypointCategory> categories() {
        return Collections.unmodifiableList(CATEGORIES);
    }

    /** The category with this id, or General when there is none. */
    public static WaypointCategory category(String id) {
        WaypointCategory found = id == null ? null : findCategory(id);
        if (found != null) return found;
        WaypointCategory general = findCategory(WaypointCategory.GENERAL);
        return general != null ? general : CATEGORIES.get(0);
    }

    private static WaypointCategory findCategory(String id) {
        for (WaypointCategory c : CATEGORIES) if (c.id.equals(id)) return c;
        return null;
    }

    /** A new category with a fresh id, added at the end of the list. */
    public static WaypointCategory addCategory(String name, int color, Waypoint.Shape icon) {
        String id;
        do {
            id = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        } while (findCategory(id) != null);
        WaypointCategory c = new WaypointCategory(id, name, color, icon);
        CATEGORIES.add(c);
        save();
        return c;
    }

    /** Deletes a category (never General); its waypoints move to General. */
    public static void removeCategory(WaypointCategory c) {
        if (c.isGeneral()) return;
        CATEGORIES.remove(c);
        for (Waypoint wp : WAYPOINTS) {
            if (c.id.equals(wp.category)) wp.category = WaypointCategory.GENERAL;
        }
        save();
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
        // Exactly who may run /tp: creative mode alone doesn't allow it on a server (nor in a world without cheats).
        return mc.player != null && mc.player.hasPermissions(2);
    }

    public static void teleportTo(Minecraft mc, Waypoint wp) {
        teleport(mc, wp.dimension, wp.x, String.valueOf(wp.y), wp.z);
    }

    /** Teleports into {@code dimension} (which may not be the current one); {@code y} may be "~". */
    public static void teleport(Minecraft mc, String dimension, int x, String y, int z) {
        if (mc.player == null || mc.getConnection() == null) return;
        if (!MapConfig.TELEPORT_MESSAGE.get()) hideTeleportMessageUntil = System.currentTimeMillis() + 3000;
        mc.getConnection().sendUnsignedCommand("execute in " + dimension + " run tp @s " + x + " " + y + " " + z);
    }

    /** Until when the answer to the map's own teleport is kept out of chat (0: not waiting for one). */
    private static long hideTeleportMessageUntil;

    /** Hides the one "Teleported ..." message our teleport causes; a /tp typed by hand still shows. */
    @SubscribeEvent
    public static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (hideTeleportMessageUntil == 0) return;
        if (System.currentTimeMillis() > hideTeleportMessageUntil) {
            hideTeleportMessageUntil = 0;
            return;
        }
        if (event.getMessage().getContents() instanceof TranslatableContents t && t.getKey().startsWith("commands.teleport.success")) {
            hideTeleportMessageUntil = 0;
            event.setCanceled(true);
        }
    }

    /** Copies the file next to itself under {@code name}, once: an existing copy is kept. */
    private static void backup(Path file, String name) {
        Path copy = file.resolveSibling(name);
        if (Files.exists(copy)) return;
        try {
            Files.copy(file, copy);
        } catch (IOException ignored) {
        }
    }

    private static Path waypointFile(String worldId) {
        return FMLPaths.GAMEDIR.get().resolve("dvmap").resolve(worldId).resolve("waypoints.json");
    }
}
