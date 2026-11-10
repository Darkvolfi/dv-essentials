package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvmusic.DVMusic;
import com.darkvolfi.dvmusic.client.audio.AudioDecoders;
import com.darkvolfi.dvmusic.client.audio.DecodedStream;
import com.mojang.blaze3d.audio.OggAudioStream;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Plays one exact song. Vanilla music goes through a sound event that picks a random file; this resolves
 * straight to the chosen file, streamed like music. The player's own files are read straight from the music
 * folder, decoded by DV Music when they aren't Ogg.
 */
final class TrackInstance extends AbstractSoundInstance {
    private static final Logger LOG = LogUtils.getLogger();
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

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        Path file = track.file();
        if (file == null) return buffers.getStream(sound.getPath(), looping);
        return CompletableFuture.supplyAsync(() -> open(file), Util.backgroundExecutor());
    }

    private static AudioStream open(Path file) {
        try {
            if (AudioDecoders.extension(file).equals("ogg")) {
                return new OggAudioStream(new BufferedInputStream(Files.newInputStream(file)));
            }
            return new DecodedStream(AudioDecoders.open(file));
        } catch (Exception | LinkageError e) {
            LOG.warn("[DV Music] Could not play {}: {}", file.getFileName(), e.toString());
            return DecodedStream.silent();
        }
    }
}
