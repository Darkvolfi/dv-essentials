package com.darkvolfi.dvmusic.client;

import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Set;

/**
 * One song. {@code sound} is the sound file without "sounds/" and ".ogg" (minecraft:music/game/sweden), or a
 * made-up dvmusic:custom/... id for the player's files, whose {@code file} is set. {@code artist} may be empty.
 * {@code situations} are the song's own places (empty: anywhere); categories can add more, see {@link Categories}.
 */
public record Track(ResourceLocation sound, String title, String artist, Path file, String key, Set<Situation> situations, float volume) {
    public boolean custom() {
        return file != null;
    }

    public boolean playsIn(Situation situation) {
        return situations.isEmpty() || situations.contains(situation);
    }
}
