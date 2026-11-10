package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvmusic.DVMusic;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

import com.darkvolfi.dvmusic.client.audio.AudioDecoders;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every song DV Music knows: Minecraft's from the music entries of sounds.json (all resource packs),
 * and the player's files (ogg, mp3, wav, m4a, mp4...) from the dvmusic folder in the game directory.
 */
public final class TrackLibrary {
    private static final Logger LOG = LogUtils.getLogger();
    /** Audio files DV Music can't play yet: counted, so the screen can say why they are missing. */
    private static final Set<String> OTHER_AUDIO = Set.of("flac", "wma", "opus", "webm", "mkv", "ape", "alac", "mka");

    /**
     * File name → {official title, composer}, as the Minecraft Wiki lists them (1.20.3 renamed the old files to
     * these titles). Unknown files (resource packs) get a title made from their name.
     */
    private static final Map<String, String[]> KNOWN = new HashMap<>();

    private static void known(String composer, String... fileTitle) {
        for (int i = 0; i < fileTitle.length; i += 2) KNOWN.put(fileTitle[i], new String[]{fileTitle[i + 1], composer});
    }

    static {
        known("C418",
                "calm1", "Minecraft", "calm2", "Clark", "calm3", "Sweden",
                "hal1", "Subwoofer Lullaby", "hal2", "Living Mice", "hal3", "Haggstrom", "hal4", "Danny",
                "nuance1", "Key", "nuance2", "Oxygène",
                "piano1", "Dry Hands", "piano2", "Wet Hands", "piano3", "Mice on Venus",
                "menu1", "Mutation", "menu2", "Moog City 2", "menu3", "Beginning 2", "menu4", "Floating Trees",
                "creative1", "Biome Fest", "creative2", "Blind Spots", "creative3", "Haunt Muskie",
                "creative4", "Aria Math", "creative5", "Dreiton", "creative6", "Taswell",
                "nether1", "Concrete Halls", "nether2", "Dead Voxel", "nether3", "Warmth", "nether4", "Ballad of the Cats",
                "boss", "Boss", "end", "The End", "credits", "Alpha",
                "axolotl", "Axolotl", "dragon_fish", "Dragon Fish", "shuniji", "Shuniji");
        known("Lena Raine",
                "aerie", "Aerie", "ancestry", "Ancestry", "chrysopoeia", "Chrysopoeia", "firebugs", "Firebugs",
                "infinite_amethyst", "Infinite Amethyst", "labyrinthine", "Labyrinthine", "left_to_bloom", "Left to Bloom",
                "one_more_day", "One More Day", "rubedo", "Rubedo", "so_below", "So Below", "stand_tall", "Stand Tall",
                "wending", "Wending");
        known("Kumi Tanioka",
                "an_ordinary_day", "An Ordinary Day", "comforting_memories", "Comforting Memories", "floating_dream", "Floating Dream");
        known("Aaron Cherof",
                "a_familiar_room", "A Familiar Room", "bromeliad", "Bromeliad", "crescent_dunes", "Crescent Dunes",
                "echo_in_the_wind", "Echo in the Wind");
    }

    private static List<Track> vanilla = List.of();
    private static List<Track> custom = List.of();
    /** Audio files in the folder that Minecraft can't play (not .ogg). */
    private static int unplayable;

    private TrackLibrary() {}

    public static List<Track> vanilla() {
        return vanilla;
    }

    public static List<Track> custom() {
        return custom;
    }

    public static int unplayable() {
        return unplayable;
    }

    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(DVMusic.MOD_ID);
    }

    /** Re-reads both lists; called after resources load and from the screen's refresh button. */
    public static void reload() {
        vanilla = readVanilla();
        custom = readCustom();
    }

    private static List<Track> readVanilla() {
        Map<String, Set<Situation>> where = new LinkedHashMap<>();
        Map<String, Float> volumes = new HashMap<>();
        for (Resource resource : Minecraft.getInstance().getResourceManager().getResourceStack(new ResourceLocation("minecraft", "sounds.json"))) {
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    Situation situation = Situation.ofEvent(e.getKey());
                    if (situation == null || !e.getValue().isJsonObject()) continue;
                    for (JsonElement sound : GsonHelper.getAsJsonArray(e.getValue().getAsJsonObject(), "sounds", new com.google.gson.JsonArray())) {
                        String name;
                        float volume = 1f;
                        if (sound.isJsonObject()) {
                            JsonObject o = sound.getAsJsonObject();
                            if (GsonHelper.getAsString(o, "type", "file").equals("event")) continue;
                            name = GsonHelper.getAsString(o, "name");
                            volume = GsonHelper.getAsFloat(o, "volume", 1f);
                        } else {
                            name = sound.getAsString();
                        }
                        String id = new ResourceLocation(name).toString();
                        where.computeIfAbsent(id, k -> EnumSet.noneOf(Situation.class)).add(situation);
                        volumes.put(id, volume);
                    }
                }
            } catch (IOException | RuntimeException ex) {
                LOG.warn("[DV Music] Could not read {}: {}", resource.sourcePackId(), ex.toString());
            }
        }
        List<Track> tracks = new ArrayList<>();
        for (Map.Entry<String, Set<Situation>> e : where.entrySet()) {
            ResourceLocation sound = new ResourceLocation(e.getKey());
            String file = sound.getPath().substring(sound.getPath().lastIndexOf('/') + 1);
            String[] known = KNOWN.get(file);
            String title = known != null ? known[0] : title(file);
            String artist = known != null ? known[1] : "";
            tracks.add(new Track(sound, title, artist, null, sound.toString(), Set.copyOf(e.getValue()), volumes.get(e.getKey())));
        }
        tracks.sort(Comparator.comparing(Track::title));
        return List.copyOf(tracks);
    }

    private static List<Track> readCustom() {
        Path root = folder();
        List<Track> tracks = new ArrayList<>();
        int other = 0;
        try {
            Files.createDirectories(root);
            removeEmptyOldFolders(root);
            // Depth 2: songs put in the old per-place folders (dvmusic/nether/...) still play there.
            try (Stream<Path> paths = Files.walk(root, 2)) {
                for (Path path : (Iterable<Path>) paths::iterator) {
                    if (!Files.isRegularFile(path)) continue;
                    String name = path.getFileName().toString();
                    String ext = AudioDecoders.extension(path);
                    if (!AudioDecoders.PLAYABLE.contains(ext)) {
                        if (OTHER_AUDIO.contains(ext)) other++;
                        continue;
                    }
                    Path parent = root.relativize(path).getParent();
                    Situation situation = parent == null ? null : Situation.ofFolder(parent.toString());
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    String base = name.substring(0, name.length() - ext.length() - 1);
                    String id = "custom/" + slug(base) + "_" + Integer.toHexString(relative.hashCode());
                    Set<Situation> where = situation == null ? Set.of() : Set.of(situation);
                    // "Artist - Title.mp3" gives both; otherwise the whole name is the title.
                    int dash = base.indexOf(" - ");
                    String title = dash > 0 ? base.substring(dash + 3).trim() : base;
                    String artist = dash > 0 ? base.substring(0, dash).trim() : "";
                    tracks.add(new Track(new ResourceLocation(DVMusic.MOD_ID, id), title, artist, path, "file:" + relative, where, 1f));
                }
            }
        } catch (IOException e) {
            LOG.warn("[DV Music] Could not read the music folder {}: {}", root, e.toString());
        }
        unplayable = other;
        tracks.sort(Comparator.comparing(t -> t.title().toLowerCase(Locale.ROOT)));
        return List.copyOf(tracks);
    }

    /** Older versions made a folder per place; empty ones are removed so the music folder holds only songs. */
    private static void removeEmptyOldFolders(Path root) {
        for (Situation s : Situation.values()) {
            Path dir = root.resolve(s.folder());
            try (Stream<Path> inside = Files.isDirectory(dir) ? Files.list(dir) : Stream.of(dir)) {
                if (Files.isDirectory(dir) && inside.findAny().isEmpty()) Files.delete(dir);
            } catch (IOException ignored) {
                // Left as it is.
            }
        }
    }

    /** What dropping files on the music screen did. {@code added} are the new songs' keys. */
    public record Import(List<String> added, int skipped) {}

    /** Copies the playable files among {@code files} into the music folder (never over an existing file), then reloads. */
    public static Import importFiles(List<Path> files) {
        Path root = folder();
        List<String> added = new ArrayList<>();
        int skipped = 0;
        for (Path file : files) {
            Path target = root.resolve(file.getFileName().toString());
            if (!Files.isRegularFile(file) || !AudioDecoders.PLAYABLE.contains(AudioDecoders.extension(file))) {
                skipped++;
                continue;
            }
            try {
                Files.createDirectories(root);
                if (!Files.exists(target)) Files.copy(file, target);
                added.add("file:" + file.getFileName());
            } catch (IOException e) {
                LOG.warn("[DV Music] Could not copy {}: {}", file, e.toString());
                skipped++;
            }
        }
        if (!added.isEmpty()) custom = readCustom();
        return new Import(added, skipped);
    }

    /** A file name made safe for a resource path: lower case ASCII, digits, '_' (Locale.ROOT, so "I" stays "i"). */
    private static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "song" : s.length() > 40 ? s.substring(0, 40) : s;
    }

    /** "music/game/swamp/aerie" → "Aerie", "an_ordinary_day" → "An Ordinary Day". */
    private static String title(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        StringBuilder b = new StringBuilder();
        for (String word : last.split(" ")) {
            if (word.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return b.toString();
    }
}
