package com.darkvolfi.dvlib.client;

/**
 * Shared GUI colours. DV Dark switches {@link #dark} on; screens drawn with fills
 * (no texture to recolour) pass their colours through {@link #shade(int)}.
 */
public final class DVTheme {
    private static final int FLOOR = 12;       // black ends up here
    private static final float SCALE = 0.27f;  // white ends up at FLOOR + 255 * SCALE

    public static boolean dark;

    private DVTheme() {}

    /** The ARGB colour as-is, or its dark-mode counterpart while DV Dark is on. */
    public static int shade(int argb) {
        if (!dark) return argb;
        return (argb & 0xFF000000) | darkChannel(argb >> 16 & 0xFF) << 16 | darkChannel(argb >> 8 & 0xFF) << 8 | darkChannel(argb & 0xFF);
    }

    /** Maps one 0-255 colour channel to dark mode, keeping the order of shades. */
    public static int darkChannel(int channel) {
        return Math.round(FLOOR + channel * SCALE);
    }
}
