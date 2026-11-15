package com.darkvolfi.dvskin;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

/**
 * Server-side picture check with plain Java (a dedicated server has no NativeImage): a real PNG of a standard size
 * that decodes completely. The size is read from the header before decoding, so a huge picture is never unpacked.
 */
public final class SkinCheck {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private SkinCheck() {}

    public static boolean isPng(byte[] png) {
        if (png == null || png.length < SIGNATURE.length || png.length > SkinData.MAX_PNG) return false;
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (png[i] != SIGNATURE[i]) return false;
        }
        return true;
    }

    /** Width and height from the PNG header, or null when it isn't a PNG. Nothing is decoded. */
    public static int[] pngSize(byte[] png) {
        if (png == null || png.length < 24) return null;
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (png[i] != SIGNATURE[i]) return null;
        }
        // The first chunk must be IHDR: length, "IHDR", then width and height (big-endian)
        if (png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') return null;
        return new int[] {readInt(png, 16), readInt(png, 20)};
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }

    /** True for a skin of 64×64 or legacy 64×32, or a cape/elytra of 64×32. */
    public static boolean isValid(byte[] png, int slot) {
        int[] size = isPng(png) ? pngSize(png) : null;
        if (size == null || size[0] != 64 || !(size[1] == 32 || slot == SkinData.SKIN && size[1] == 64)) return false;
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                boolean sizeOk = w == 64 && (h == 32 || slot == SkinData.SKIN && h == 64);
                return sizeOk && reader.read(0) != null;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
