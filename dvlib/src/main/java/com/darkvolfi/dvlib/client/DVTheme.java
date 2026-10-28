package com.darkvolfi.dvlib.client;

/**
 * Shared GUI colours. DV Dark switches {@link #dark} on and sets how dark and which tint; screens drawn with fills
 * (no texture to recolour) pass their colours through {@link #shade(int)}.
 */
public final class DVTheme {
    private static final int FLOOR = 12;       // black ends up here

    public static boolean dark;
    /** White ends up at FLOOR + 255 * scale. */
    public static float scale = 0.27f;
    /** RGB the dark greys lean towards, or -1 for plain grey. */
    public static int tint = -1;

    private DVTheme() {}

    /** The ARGB colour as-is, or its dark-mode counterpart while DV Dark is on. */
    public static int shade(int argb) {
        if (!dark) return argb;
        return (argb & 0xFF000000) | darkRgb(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF);
    }

    /** Maps a colour to dark mode as 0xRRGGBB, keeping the order of shades. */
    public static int darkRgb(int r, int g, int b) {
        return darkChannel(r, 16) << 16 | darkChannel(g, 8) << 8 | darkChannel(b, 0);
    }

    private static int darkChannel(int channel, int shift) {
        float v = FLOOR + channel * scale;
        if (tint >= 0) v *= 0.6f + 0.8f * (tint >> shift & 0xFF) / 255f;
        return Math.min(255, Math.round(v));
    }
}
