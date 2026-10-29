package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvmusic.DVMusic;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * Plays one exact song. Vanilla music goes through a sound event that picks a random file; this resolves
 * straight to the chosen file, streamed like music.
 */
final class TrackInstance extends AbstractSoundInstance {
    private static final ResourceLocation EVENT = new ResourceLocation(DVMusic.MOD_ID, "track");

    final Track track;

    TrackInstance(Track track) {
        super(EVENT, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.track = track;
        this.looping = false;
        this.delay = 0;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        WeighedSoundEvents events = new WeighedSoundEvents(EVENT, null);
        sound = new Sound(track.sound().toString(), ConstantFloat.of(track.volume()), ConstantFloat.of(1f), 1,
                Sound.Type.FILE, true, false, 16);
        events.addSound(sound);
        return events;
    }
}
