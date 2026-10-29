package com.darkvolfi.dvmusic.client;

import net.minecraft.sounds.Music;

import java.util.Locale;

/**
 * Where the player is, as far as music goes. A custom song in dvmusic/&lt;name&gt;/ plays only there;
 * one directly in dvmusic/ plays everywhere.
 */
public enum Situation {
    MENU, GAME, CREATIVE, UNDERWATER, NETHER, END;

    public String folder() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "dvmusic.situation." + folder();
    }

    /** The situation of a vanilla music event ("music.nether.basalt_deltas" → NETHER), or null if it isn't music. */
    public static Situation ofEvent(String path) {
        if (!path.startsWith("music.")) return null;
        String rest = path.substring("music.".length());
        if (rest.equals("menu")) return MENU;
        if (rest.equals("creative")) return CREATIVE;
        if (rest.equals("under_water")) return UNDERWATER;
        if (rest.startsWith("nether")) return NETHER;
        if (rest.equals("end") || rest.equals("dragon") || rest.equals("credits")) return END;
        return GAME; // music.game and the overworld biome tracks
    }

    public static Situation of(Music music) {
        return music.getEvent().unwrapKey()
                .map(key -> ofEvent(key.location().getPath()))
                .orElse(GAME);
    }

    public static Situation ofFolder(String name) {
        for (Situation s : values()) if (s.folder().equals(name.toLowerCase(Locale.ROOT))) return s;
        return null;
    }
}
