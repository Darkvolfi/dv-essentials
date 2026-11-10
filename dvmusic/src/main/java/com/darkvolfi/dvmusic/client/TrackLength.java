package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvmusic.client.audio.AudioDecoders;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A song's length in seconds, read in the background: for Ogg Vorbis the sample rate from the identification
 * header and the sample count (granule position) of the last page; other files from their own headers.
 */
final class TrackLength {
    /** -1 while reading or when the file can't be read. */
    private static final Map<ResourceLocation, Integer> SECONDS = new ConcurrentHashMap<>();

    private TrackLength() {}

    static int seconds(Track track) {
        Integer known = SECONDS.get(track.sound());
        if (known != null) return known;
        SECONDS.put(track.sound(), -1);
        Util.backgroundExecutor().execute(() -> SECONDS.put(track.sound(), read(track)));
        return -1;
    }

    private static int read(Track track) {
        if (track.custom() && !AudioDecoders.extension(track.file()).equals("ogg")) return AudioDecoders.seconds(track.file());
        try (InputStream in = open(track)) {
            if (in == null) return -1;
            byte[] data = in.readAllBytes();
            int rate = 0;
            for (int i = 0; i + 16 < data.length && i < 4096; i++) {
                if (data[i] == 1 && data[i + 1] == 'v' && data[i + 2] == 'o' && data[i + 3] == 'r' && data[i + 4] == 'b'
                        && data[i + 5] == 'i' && data[i + 6] == 's') {
                    rate = littleEndianInt(data, i + 12);
                    break;
                }
            }
            for (int i = data.length - 14; i >= 0 && rate > 0; i--) {
                if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S') {
                    long samples = littleEndianInt(data, i + 6) & 0xFFFFFFFFL | (long) littleEndianInt(data, i + 10) << 32;
                    return (int) (samples / rate);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // No length: the screen just shows the time played.
        }
        return -1;
    }

    private static InputStream open(Track track) throws IOException {
        if (track.custom()) {
            return Files.newInputStream(track.file());
        }
        ResourceLocation path = new ResourceLocation(track.sound().getNamespace(), "sounds/" + track.sound().getPath() + ".ogg");
        var resource = Minecraft.getInstance().getResourceManager().getResource(path);
        return resource.isPresent() ? resource.get().open() : null;
    }

    private static int littleEndianInt(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8 | (b[i + 2] & 0xFF) << 16 | (b[i + 3] & 0xFF) << 24;
    }
}
