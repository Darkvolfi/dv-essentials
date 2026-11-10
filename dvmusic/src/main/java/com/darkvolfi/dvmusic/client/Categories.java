package com.darkvolfi.dvmusic.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The player's categories, made in game: a name, songs (Minecraft's or their own) and the places they play.
 * Kept in config/dvmusic-categories.json, so the music folder holds nothing but songs.
 * <ul>
 *   <li>A category with places makes its songs play there too; the player's own songs in such a category play
 *       only in its places.</li>
 *   <li>The active category (picked in the "Songs" menu) is the only one that plays, everywhere.</li>
 * </ul>
 */
public final class Categories {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Category {
        private String name;
        /** Empty: the songs keep their own places. */
        public final Set<Situation> places = EnumSet.noneOf(Situation.class);
        /** {@link Track#key()}s, in the order they were added. */
        public final Set<String> songs = new LinkedHashSet<>();

        private Category(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public boolean contains(Track track) {
            return songs.contains(track.key());
        }
    }

    private static List<Category> list;
    private static String active = "";

    private Categories() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("dvmusic-categories.json");
    }

    public static List<Category> all() {
        if (list == null) load();
        return list;
    }

    public static Category get(String name) {
        for (Category c : all()) if (c.name.equalsIgnoreCase(name)) return c;
        return null;
    }

    /** The category that alone plays, or null. */
    public static Category active() {
        return active.isEmpty() ? null : get(active);
    }

    public static void setActive(Category category) {
        active = category == null ? "" : category.name;
        save();
    }

    /** A new category with a free name based on {@code name}. */
    public static Category create(String name) {
        String base = name.isBlank() ? "?" : name.strip();
        String free = base;
        for (int i = 2; get(free) != null; i++) free = base + " " + i;
        Category c = new Category(free);
        all().add(c);
        save();
        return c;
    }

    /** Renames unless another category already has the name; returns whether it did. */
    public static boolean rename(Category category, String name) {
        String n = name.strip();
        if (n.isEmpty()) return false;
        Category other = get(n);
        if (other != null && other != category) return false;
        if (active.equalsIgnoreCase(category.name)) active = n;
        category.name = n;
        save();
        return true;
    }

    public static void delete(Category category) {
        all().remove(category);
        if (active.equalsIgnoreCase(category.name)) active = "";
        save();
    }

    public static void toggle(Category category, Track track) {
        if (!category.songs.remove(track.key())) category.songs.add(track.key());
        save();
    }

    /** Where a song may play outside an active category: its own places plus those of its categories. */
    public static boolean playsIn(Track track, Situation situation) {
        boolean placed = false;
        for (Category c : all()) {
            if (c.places.isEmpty() || !c.contains(track)) continue;
            if (c.places.contains(situation)) return true;
            placed = true;
        }
        return !(placed && track.custom()) && track.playsIn(situation);
    }

    /** The places shown for a song; empty means everywhere. */
    public static Set<Situation> places(Track track) {
        Set<Situation> extra = EnumSet.noneOf(Situation.class);
        for (Category c : all()) if (c.contains(track)) extra.addAll(c.places);
        if (track.custom() && !extra.isEmpty()) return extra;
        if (track.situations().isEmpty()) return Set.of();
        extra.addAll(track.situations());
        return extra;
    }

    private static void load() {
        list = new ArrayList<>();
        Path file = file();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            active = root.has("active") ? root.get("active").getAsString() : "";
            for (JsonElement e : root.getAsJsonArray("categories")) {
                JsonObject o = e.getAsJsonObject();
                Category c = new Category(o.get("name").getAsString());
                if (o.has("places")) {
                    for (JsonElement p : o.getAsJsonArray("places")) {
                        Situation s = Situation.ofFolder(p.getAsString());
                        if (s != null) c.places.add(s);
                    }
                }
                if (o.has("songs")) for (JsonElement s : o.getAsJsonArray("songs")) c.songs.add(s.getAsString());
                if (get(c.name) == null) list.add(c);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("[DV Music] Could not read {}: {}", file, e.toString());
        }
    }

    /** Saves after a category's places or songs were changed directly. */
    public static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("active", active);
        JsonArray categories = new JsonArray();
        for (Category c : all()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", c.name);
            JsonArray places = new JsonArray();
            for (Situation s : c.places) places.add(s.folder());
            o.add("places", places);
            JsonArray songs = new JsonArray();
            for (String s : c.songs) songs.add(s);
            o.add("songs", songs);
            categories.add(o);
        }
        root.add("categories", categories);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOG.warn("[DV Music] Could not save {}: {}", file, e.toString());
        }
    }
}
