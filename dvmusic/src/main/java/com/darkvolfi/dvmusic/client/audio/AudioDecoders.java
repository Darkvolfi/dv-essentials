package com.darkvolfi.dvmusic.client.audio;

import com.darkvolfi.dvmusic.lib.jaad.aac.Decoder;
import com.darkvolfi.dvmusic.lib.jaad.aac.SampleBuffer;
import com.darkvolfi.dvmusic.lib.jaad.adts.ADTSDemultiplexer;
import com.darkvolfi.dvmusic.lib.jaad.mp4.MP4Container;
import com.darkvolfi.dvmusic.lib.jaad.mp4.api.AudioTrack;
import com.darkvolfi.dvmusic.lib.jaad.mp4.api.Movie;
import com.darkvolfi.dvmusic.lib.jaad.mp4.api.Track;
import com.darkvolfi.dvmusic.lib.jaad.mp4.api.Type;
import com.darkvolfi.dvmusic.lib.mp3.Sound;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the music files Minecraft can't read itself (mp3, wav, aiff, aac, m4a, mp4) into 16-bit little-endian
 * PCM, piece by piece, with at most two channels (what OpenAL streams). No Minecraft classes here.
 */
public final class AudioDecoders {
    /** Extensions DV Music plays, Ogg included (Minecraft reads that one itself). */
    public static final Set<String> PLAYABLE = Set.of("ogg", "mp3", "wav", "m4a", "mp4", "aac", "aif", "aiff", "au");

    private AudioDecoders() {}

    /** Decoded audio, read piece by piece. */
    public interface Source extends Closeable {
        /** Always 16-bit signed little-endian, one or two channels. */
        AudioFormat format();

        /** The next piece of samples (may be empty), or null at the end. */
        byte[] next() throws IOException;
    }

    public static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static Source open(Path file) throws IOException {
        return switch (extension(file)) {
            case "mp3" -> new Mp3(file);
            case "m4a", "mp4" -> new Mp4(file);
            case "aac" -> new Adts(file);
            case "wav", "aif", "aiff", "au" -> sampled(file);
            default -> throw new IOException("Not a supported audio file: " + file.getFileName());
        };
    }

    /** Length in seconds from the file's headers, without decoding it, or -1. Reads the file: not on the main thread. */
    public static int seconds(Path file) {
        try {
            double seconds = switch (extension(file)) {
                case "mp3" -> mp3Seconds(Files.readAllBytes(file));
                case "aac" -> adtsSeconds(Files.readAllBytes(file));
                case "m4a", "mp4" -> {
                    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                        yield new MP4Container(raf).getMovie().getDuration();
                    }
                }
                case "wav", "aif", "aiff", "au" -> {
                    AudioFileFormat f = AudioSystem.getAudioFileFormat(file.toFile());
                    yield f.getFrameLength() < 0 ? -1 : f.getFrameLength() / f.getFormat().getFrameRate();
                }
                default -> -1;
            };
            return seconds < 0 ? -1 : (int) seconds;
        } catch (IOException | UnsupportedAudioFileException | RuntimeException e) {
            return -1;
        }
    }

    private static AudioFormat pcm16(float rate, int channels) {
        return new AudioFormat(rate, 16, Math.min(channels, 2), true, false);
    }

    /**
     * {@code length} bytes of {@code bits}-bit samples with {@code channels} channels → a new array of 16-bit
     * little-endian samples with at most two channels (the first two are kept). 8-bit samples are unsigned.
     */
    static byte[] to16(byte[] in, int length, int bits, int channels, boolean bigEndian) {
        int width = bits / 8;
        if (width == 2 && !bigEndian && channels <= 2) return Arrays.copyOf(in, length); // callers reuse `in`
        int outChannels = Math.min(channels, 2);
        int frames = length / (width * channels);
        byte[] out = new byte[frames * outChannels * 2];
        int o = 0;
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < outChannels; c++) {
                int i = (f * channels + c) * width;
                int hi, lo;
                if (width == 1) {
                    hi = (in[i] & 0xFF) - 128;
                    lo = 0;
                } else if (bigEndian) {
                    hi = in[i];
                    lo = in[i + 1] & 0xFF;
                } else {
                    hi = in[i + width - 1];
                    lo = in[i + width - 2] & 0xFF;
                }
                out[o++] = (byte) lo;
                out[o++] = (byte) hi;
            }
        }
        return out;
    }

    /** Size of an ID3v2 tag (title, cover art...) at the start of mp3 data, or 0. */
    private static int id3Size(byte[] h) {
        if (h.length < 10 || h[0] != 'I' || h[1] != 'D' || h[2] != '3') return 0;
        int size = (h[6] & 0x7F) << 21 | (h[7] & 0x7F) << 14 | (h[8] & 0x7F) << 7 | (h[9] & 0x7F);
        return size + 10 + ((h[5] & 0x10) != 0 ? 10 : 0); // header, footer
    }

    private static final int[] MP3_RATES = {44100, 48000, 32000};
    private static final int[] MP3_KBPS_V1 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
    private static final int[] MP3_KBPS_V2 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};

    /** Adds up the length of every MPEG layer III frame, read from the frame headers. */
    static double mp3Seconds(byte[] d) {
        double seconds = 0;
        int i = id3Size(Arrays.copyOf(d, Math.min(10, d.length)));
        while (i + 4 <= d.length) {
            if ((d[i] & 0xFF) == 0xFF && (d[i + 1] & 0xE0) == 0xE0) {
                int version = (d[i + 1] >> 3) & 3; // 3: MPEG 1, 2: MPEG 2, 0: MPEG 2.5
                int layer = (d[i + 1] >> 1) & 3;   // 1: layer III
                int bitrate = (d[i + 2] >> 4) & 15;
                int rateIndex = (d[i + 2] >> 2) & 3;
                if (version != 1 && layer == 1 && bitrate != 0 && bitrate != 15 && rateIndex != 3) {
                    boolean v1 = version == 3;
                    int rate = MP3_RATES[rateIndex] / (v1 ? 1 : version == 2 ? 2 : 4);
                    int kbps = (v1 ? MP3_KBPS_V1 : MP3_KBPS_V2)[bitrate];
                    seconds += (v1 ? 1152 : 576) / (double) rate;
                    i += (v1 ? 144000 : 72000) * kbps / rate + ((d[i + 2] >> 1) & 1);
                    continue;
                }
            }
            i++;
        }
        return seconds;
    }

    private static final int[] AAC_RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};

    /** Counts the ADTS frames of a raw AAC file; each holds 1024 samples. */
    static double adtsSeconds(byte[] d) {
        double seconds = 0;
        int i = 0;
        while (i + 7 <= d.length) {
            if ((d[i] & 0xFF) == 0xFF && (d[i + 1] & 0xF6) == 0xF0) {
                int rateIndex = (d[i + 2] >> 2) & 15;
                int length = (d[i + 3] & 0x03) << 11 | (d[i + 4] & 0xFF) << 3 | (d[i + 5] & 0xE0) >> 5;
                if (length >= 7 && rateIndex < AAC_RATES.length) {
                    seconds += 1024.0 / AAC_RATES[rateIndex];
                    i += length;
                    continue;
                }
            }
            i++;
        }
        return seconds;
    }

    private static IOException wrap(Exception e) {
        return e instanceof IOException io ? io : new IOException(e);
    }

    private static final class Mp3 implements Source {
        private final Sound sound;
        private final AudioFormat format;
        private final byte[] buffer = new byte[16384];

        Mp3(Path file) throws IOException {
            InputStream in = new BufferedInputStream(Files.newInputStream(file), 65536);
            try {
                in.mark(10);
                int skip = id3Size(in.readNBytes(10));
                in.reset();
                in.skipNBytes(skip);
                sound = new Sound(in);
            } catch (IOException | RuntimeException e) {
                in.close();
                throw wrap(e);
            }
            format = pcm16(sound.getSamplingFrequency(), sound.isStereo() ? 2 : 1);
        }

        @Override
        public AudioFormat format() {
            return format;
        }

        @Override
        public byte[] next() throws IOException {
            int n = sound.readNBytes(buffer, 0, buffer.length);
            if (n <= 0) return null;
            return Arrays.copyOf(buffer, n - n % (format.getChannels() * 2));
        }

        @Override
        public void close() throws IOException {
            sound.close();
        }
    }

    /** Decodes AAC frames with JAAD; a damaged frame becomes a moment of silence, not the end of the song. */
    private abstract static class Aac implements Source {
        private final SampleBuffer samples = new SampleBuffer();
        private Decoder decoder;
        private AudioFormat format;
        private byte[] first;

        /** Sets up the decoder and reads the first frame, which gives the real output format (HE-AAC doubles the rate). */
        void start(byte[] config) throws IOException {
            decoder = new Decoder(config);
            samples.setBigEndian(false);
            first = decodeNext();
            if (first == null) throw new IOException("No sound frames");
            format = pcm16(samples.getSampleRate(), samples.getChannels());
        }

        /** The next encoded frame, or null at the end. */
        abstract byte[] nextFrame() throws IOException;

        private byte[] decodeNext() throws IOException {
            byte[] frame = nextFrame();
            if (frame == null) return null;
            try {
                decoder.decodeFrame(frame, samples);
            } catch (IOException | RuntimeException e) {
                return new byte[0];
            }
            byte[] data = samples.getData();
            return to16(data, data.length, samples.getBitsPerSample(), samples.getChannels(), samples.isBigEndian());
        }

        @Override
        public AudioFormat format() {
            return format;
        }

        @Override
        public byte[] next() throws IOException {
            if (first != null) {
                byte[] f = first;
                first = null;
                return f;
            }
            return decodeNext();
        }
    }

    /** AAC in an MP4 container: .m4a, and the sound of .mp4 videos. */
    private static final class Mp4 extends Aac {
        private final RandomAccessFile raf;
        private Track track;

        Mp4(Path file) throws IOException {
            raf = new RandomAccessFile(file.toFile(), "r");
            try {
                Movie movie = new MP4Container(raf).getMovie();
                for (Track t : movie.getTracks(Type.AUDIO)) {
                    if (t.getCodec() == AudioTrack.AudioCodec.AAC) {
                        track = t;
                        break;
                    }
                }
                if (track == null) throw new IOException("No AAC sound in " + file.getFileName());
                start(track.getDecoderSpecificInfo());
            } catch (IOException | RuntimeException e) {
                raf.close();
                throw wrap(e);
            }
        }

        @Override
        byte[] nextFrame() throws IOException {
            return track.hasMoreFrames() ? track.readNextFrame().getData() : null;
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }
    }

    /** A raw .aac file: AAC frames, each with an ADTS header. */
    private static final class Adts extends Aac {
        private final InputStream in;
        private final ADTSDemultiplexer adts;

        Adts(Path file) throws IOException {
            in = new BufferedInputStream(Files.newInputStream(file), 65536);
            try {
                adts = new ADTSDemultiplexer(in);
                start(adts.getDecoderSpecificInfo());
            } catch (IOException | RuntimeException e) {
                in.close();
                throw wrap(e);
            }
        }

        @Override
        byte[] nextFrame() throws IOException {
            try {
                return adts.readNextFrame();
            } catch (EOFException e) {
                return null;
            }
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    /** wav, aiff and au, through Java's own sound reader. */
    private static Source sampled(Path file) throws IOException {
        AudioInputStream in;
        try {
            in = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(file), 65536));
        } catch (UnsupportedAudioFileException e) {
            throw new IOException(e);
        }
        AudioFormat src = in.getFormat();
        AudioFormat.Encoding enc = src.getEncoding();
        if (!(enc.equals(AudioFormat.Encoding.PCM_SIGNED) && src.getSampleSizeInBits() >= 16)
                && !(enc.equals(AudioFormat.Encoding.PCM_UNSIGNED) && src.getSampleSizeInBits() == 8)) {
            // float, a-law, mu-law...: let Java turn it into 16-bit PCM
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
                    src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
            try {
                in = AudioSystem.getAudioInputStream(target, in);
            } catch (IllegalArgumentException e) {
                in.close();
                throw new IOException(e);
            }
            src = target;
        }
        AudioInputStream stream = in;
        AudioFormat source = src;
        AudioFormat format = pcm16(src.getSampleRate(), src.getChannels());
        int frame = src.getFrameSize();
        return new Source() {
            private final byte[] buffer = new byte[frame * 4096];

            @Override
            public AudioFormat format() {
                return format;
            }

            @Override
            public byte[] next() throws IOException {
                int n = stream.readNBytes(buffer, 0, buffer.length);
                if (n <= 0) return null;
                return to16(buffer, n - n % frame, source.getSampleSizeInBits(), source.getChannels(), source.isBigEndian());
            }

            @Override
            public void close() throws IOException {
                stream.close();
            }
        };
    }
}
