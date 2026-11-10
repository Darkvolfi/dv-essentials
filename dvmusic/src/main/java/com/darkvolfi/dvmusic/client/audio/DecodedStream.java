package com.darkvolfi.dvmusic.client.audio;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Feeds a decoded file to Minecraft's sound engine the way it streams music (an empty buffer at the end). */
public final class DecodedStream implements AudioStream {
    private final AudioDecoders.Source source;
    private byte[] rest = new byte[0];
    private int restPos;
    private boolean ended;

    public DecodedStream(AudioDecoders.Source source) {
        this.source = source;
    }

    @Override
    public AudioFormat getFormat() {
        return source.format();
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        int frame = source.format().getFrameSize();
        size -= size % frame;
        ByteBuffer out = BufferUtils.createByteBuffer(size);
        while (out.hasRemaining()) {
            if (restPos >= rest.length) {
                if (ended) break;
                byte[] next = source.next();
                if (next == null) {
                    ended = true;
                    break;
                }
                rest = next;
                restPos = 0;
                continue;
            }
            int n = Math.min(out.remaining(), rest.length - restPos);
            out.put(rest, restPos, n);
            restPos += n;
        }
        out.flip();
        return out;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    /** A song that could not be opened: no sound, so the channel stops at once and the player moves on. */
    public static AudioStream silent() {
        AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
        return new AudioStream() {
            @Override
            public AudioFormat getFormat() {
                return format;
            }

            @Override
            public ByteBuffer read(int size) {
                return BufferUtils.createByteBuffer(0);
            }

            @Override
            public void close() {}
        };
    }
}
