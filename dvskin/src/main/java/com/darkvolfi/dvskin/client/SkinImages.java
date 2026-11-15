package com.darkvolfi.dvskin.client;

import com.darkvolfi.dvskin.SkinCheck;
import com.darkvolfi.dvskin.SkinData;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Turns the PNGs players pick or receive into standard pictures: 64×64 skins, 64×32 capes and elytra. */
public final class SkinImages {
    /** Largest picture accepted from a file or a link, before it is re-encoded. */
    public static final int MAX_INPUT = 256 * 1024;

    private SkinImages() {}

    /** Reads, checks and converts a picture; the caller closes the result. */
    public static NativeImage read(byte[] png, int slot) throws SkinException {
        if (png.length > MAX_INPUT) throw new SkinException("too_big");
        int[] size = SkinCheck.pngSize(png);
        if (size == null) throw new SkinException("invalid");
        boolean sizeOk = size[0] == 64 && (size[1] == 32 || slot == SkinData.SKIN && size[1] == 64);
        if (!sizeOk) throw new SkinException("size");
        NativeImage image;
        try {
            image = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(png));
        } catch (IOException | RuntimeException e) {
            throw new SkinException("invalid");
        }
        if (image.getWidth() != size[0] || image.getHeight() != size[1]) {
            image.close();
            throw new SkinException("invalid");
        }
        if (slot != SkinData.SKIN) return image;
        if (image.getHeight() == 32) image = toModern(image);
        // The base layer is always solid, as in vanilla: a see-through skin can't make a player invisible
        solid(image, 0, 0, 32, 16);
        solid(image, 0, 16, 64, 32);
        solid(image, 16, 48, 48, 64);
        return image;
    }

    /** A clean, metadata-free PNG of the standard picture, small enough to send. */
    public static byte[] normalize(byte[] png, int slot) throws SkinException {
        try (NativeImage image = read(png, slot)) {
            byte[] out = image.asByteArray();
            if (out.length > SkinData.MAX_PNG) throw new SkinException("too_big");
            return out;
        } catch (IOException e) {
            throw new SkinException("invalid");
        }
    }

    private static void solid(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) | 0xFF000000);
        }
    }

    /** A 64×32 skin has one leg and one arm drawn; the left ones mirror them in the 64×64 layout. */
    private static NativeImage toModern(NativeImage old) {
        NativeImage image = new NativeImage(64, 64, true);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) image.setPixelRGBA(x, y, old.getPixelRGBA(x, y));
        }
        old.close();
        mirrorLimb(image, 0, 16, 16, 48);   // right leg -> left leg
        mirrorLimb(image, 40, 16, 32, 48);  // right arm -> left arm
        return image;
    }

    /** Copies a 16×16 limb block mirrored: top and bottom flip, the outer and inner sides swap. */
    private static void mirrorLimb(NativeImage image, int sx, int sy, int dx, int dy) {
        mirror(image, sx + 4, sy, 4, 4, dx + 4, dy);           // top
        mirror(image, sx + 8, sy, 4, 4, dx + 8, dy);           // bottom
        mirror(image, sx, sy + 4, 4, 12, dx + 8, dy + 4);      // outer side becomes the inner one
        mirror(image, sx + 4, sy + 4, 4, 12, dx + 4, dy + 4);  // front
        mirror(image, sx + 8, sy + 4, 4, 12, dx, dy + 4);      // inner side becomes the outer one
        mirror(image, sx + 12, sy + 4, 4, 12, dx + 12, dy + 4); // back
    }

    private static void mirror(NativeImage image, int sx, int sy, int w, int h, int dx, int dy) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) image.setPixelRGBA(dx + w - 1 - x, dy + y, image.getPixelRGBA(sx + x, sy + y));
        }
    }
}
