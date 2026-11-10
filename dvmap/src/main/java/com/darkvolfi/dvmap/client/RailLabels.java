package com.darkvolfi.dvmap.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Names and colours the player gives to Create rail lines and trains on their own map, saved per world in
 * dvmap/&lt;world&gt;/rails.json. A line is found again by one of its track blocks (its anchor); a train by Create's
 * train id, or the carriage's entity id when that can't be read.
 */
final class RailLabels {
    static final class Line {
        String dimension;
        int x;
        int z;
        String name;
        int color;

        Line(String dimension, int x, int z, String name, int color) {
            this.dimension = dimension;
            this.x = x;
            this.z = z;
            this.name = name;
            this.color = color;
        }
    }

    static final class Train {
        String id;
        String name;
        int color;

        Train(String id, String name, int color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }
    }

    private static final class Data {
        List<Line> lines = new ArrayList<>();
        List<Train> trains = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Data>() {}.getType();

    private static Data data = new Data();
    private static String worldId;
    /** Bumped on every change, so cached line colours are rebuilt. */
    private static int version;

    private RailLabels() {}

    static void load(String id) {
        worldId = id;
        data = new Data();
        version++;
        if (id == null || id.isEmpty()) return;
        Path file = file(id);
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Data read = GSON.fromJson(reader, DATA_TYPE);
            if (read != null) {
                if (read.lines != null) data.lines = read.lines;
                if (read.trains != null) data.trains = read.trains;
            }
        } catch (IOException | RuntimeException ignored) {
            // An unreadable file is left as it is; the map just shows no names.
        }
    }

    static void save() {
        version++;
        if (worldId == null || worldId.isEmpty()) return;
        Path file = file(worldId);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, DATA_TYPE, writer);
            }
        } catch (IOException ignored) {
        }
    }

    static int version() {
        return version;
    }

    static List<Line> lines() {
        return data.lines;
    }

    static void addLine(Line line) {
        data.lines.add(line);
        save();
    }

    static void removeLine(Line line) {
        data.lines.remove(line);
        save();
    }

    static Train train(String id) {
        for (Train t : data.trains) if (id.equals(t.id)) return t;
        return null;
    }

    static void putTrain(String id, String name, int color) {
        Train t = train(id);
        if (t == null) data.trains.add(new Train(id, name, color));
        else {
            t.name = name;
            t.color = color;
        }
        save();
    }

    static void removeTrain(String id) {
        data.trains.removeIf(t -> id.equals(t.id));
        save();
    }

    private static Path file(String id) {
        return FMLPaths.GAMEDIR.get().resolve("dvmap").resolve(id).resolve("rails.json");
    }
}
