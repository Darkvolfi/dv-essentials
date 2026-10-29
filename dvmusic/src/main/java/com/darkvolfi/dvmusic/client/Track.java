package com.darkvolfi.dvmusic.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * One song. {@code sound} is the sound file without "sounds/" and ".ogg" (minecraft:music/game/sweden,
 * dvmusic:custom/...). {@code artist} may be empty. {@code situations} empty means it may play anywhere.
 */
public record Track(ResourceLocation sound, String title, String artist, boolean custom, Set<Situation> situations, float volume) {
    public boolean playsIn(Situation situation) {
        return situations.isEmpty() || situations.contains(situation);
    }
}
