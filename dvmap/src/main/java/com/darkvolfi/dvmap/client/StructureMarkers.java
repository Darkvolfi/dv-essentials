package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvmap.MapConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Structures the player has been inside (reported by DV Map on the server), saved per world like waypoints. */
public final class StructureMarkers {
    /** A little flag; '#' is filled. */
    private static final String[] FLAG = {"##.....", "####...", "######.", "####...", "##.....", "#......", "#......"};
    private static final int COLOR = 0xE8D8A0;
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {}.getType();
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static String loadedWorldId = "";

    public static final class Entry {
        public String id;
        public String dimension;
        public int x;
        public int z;

        Entry() {}

        Entry(String id, String dimension, int x, int z) {
            this.id = id;
            this.dimension = dimension;
            this.x = x;
            this.z = z;
        }

        public Component name() {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return Component.literal(id);
            String key = "dvmap.structure." + rl.getNamespace() + "." + rl.getPath();
            if (I18n.exists(key)) return Component.translatable(key);
            String words = rl.getPath().replace('_', ' ').replace('/', ' ');
            return Component.literal(words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1));
        }
    }

    private StructureMarkers() {}

    static void load(String worldId) {
        loadedWorldId = worldId;
        ENTRIES.clear();
        Path file = file(worldId);
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Entry> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded != null) ENTRIES.addAll(loaded);
        } catch (Exception ignored) {
        }
    }

    public static void discovered(ResourceLocation structure, ResourceLocation dimension, int x, int z) {
        Minecraft mc = Minecraft.getInstance();
        MapData.ensureSession(mc);
        if (loadedWorldId.isEmpty()) return;
        for (Entry e : ENTRIES) {
            if (e.id.equals(structure.toString()) && e.dimension.equals(dimension.toString()) && e.x == x && e.z == z) return;
        }
        Entry entry = new Entry(structure.toString(), dimension.toString(), x, z);
        ENTRIES.add(entry);
        save();
        if (MapConfig.SHOW_STRUCTURES.get() && mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("dvmap.structure.found", entry.name()), true);
        }
    }

    public static List<Entry> forDimension(ResourceKey<Level> dim) {
        return forDimension(dim.location().toString());
    }

    public static List<Entry> forDimension(String name) {
        List<Entry> result = new ArrayList<>();
        if (!MapConfig.SHOW_STRUCTURES.get()) return result;
        for (Entry e : ENTRIES) {
            if (e.dimension.equals(name)) result.add(e);
        }
        return result;
    }

    /** Every dimension a structure has been found in. */
    public static Set<String> dimensions() {
        Set<String> result = new LinkedHashSet<>();
        for (Entry e : ENTRIES) result.add(e.dimension);
        return result;
    }

    /** Draws the flag with its pole at (x, y), {@code px} screen pixels per icon pixel, with a dark outline. */
    public static void draw(GuiGraphics g, int x, int y, int px) {
        int x0 = x - px / 2;
        int y0 = y - FLAG.length * px;
        for (int pass = 0; pass < 2; pass++) {
            int color = pass == 0 ? 0xFF000000 : 0xFF000000 | COLOR;
            int grow = pass == 0 ? 1 : 0;
            for (int r = 0; r < FLAG.length; r++) {
                for (int c = 0; c < FLAG[r].length(); c++) {
                    if (FLAG[r].charAt(c) != '#') continue;
                    g.fill(x0 + c * px - grow, y0 + r * px - grow, x0 + (c + 1) * px + grow, y0 + (r + 1) * px + grow, color);
                }
            }
        }
    }

    public static int color() {
        return COLOR;
    }

    private static void save() {
        Path file = file(loadedWorldId);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(ENTRIES, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static Path file(String worldId) {
        return FMLPaths.GAMEDIR.get().resolve("dvmap").resolve(worldId).resolve("structures.json");
    }
}
