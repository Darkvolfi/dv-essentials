package com.darkvolfi.dvdark.client;

/**
 * Turns the title screen's panorama (a sunset over cherry groves) into the same place at night, pixel by pixel as it
 * loads: the blue sky goes deep navy with stars in it, the sunset's orange glow fades to a faint violet, the sun is
 * gone and a square moon is up, clouds go dark, and the land is darkened and cooled as if by moonlight.
 * Faces 0-3 are the sides (the horizon across their middle), 4 the top and 5 the bottom.
 */
public final class NightSky {
    private NightSky() {}

    /** The night colour (0xRRGGBB) of a panorama pixel. */
    public static int night(int r, int g, int b, int x, int y, int size, int face) {
        // How high up the sky the pixel is: 1 at the zenith (the top face), 0 at the horizon.
        float height = face == 4 ? 1 : face == 5 ? 0 : Math.max(0, 1 - y / (size * 0.5f));
        boolean skyward = face == 4 || face < 4 && y < size * 0.6f;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));

        if (skyward) {
            boolean blue = b > r + 25 && b > g + 10;
            boolean sun = min > 225;
            boolean glow = r > 150 && r > g + 30 && g > b + 10;
            boolean cloud = max - min < 30 && max > 140;
            // the sun and its bright rim: gone below the horizon, a little of the dusk left where it was
            if (sun || r > 200 && g > 165) return mix(sky(height), 0x3C2E5C, 0.85f);
            if (glow) {
                // the last of the dusk: violet, as faint as the glow was strong
                float strength = (r - b) / 255f;
                return mix(sky(height), 0x2E1E48, Math.min(1, strength * 0.9f));
            }
            if (cloud) {
                float light = max / 255f;
                return rgb(Math.round(28 + 22 * light), Math.round(32 + 24 * light), Math.round(48 + 30 * light));
            }
            if (blue) {
                int moon = moon(x, y, face, size);
                if (moon >= 0) return moon;
                int star = star(x, y, face, size);
                return star > 0 ? rgb(star, star, Math.min(255, star + 10)) : sky(height);
            }
        }
        // The land under the moon: dark, and bluer the darker it is.
        return rgb(Math.round(r * 0.24f + 4), Math.round(g * 0.27f + 6), Math.round(b * 0.36f + 14));
    }

    /** The night sky's own colour: near black up high, a little lighter towards the horizon. */
    private static int sky(float height) {
        return mix(0x1A2242, 0x070A18, height);
    }

    /** The moon, a square like the game's, high in the open sky of face 1 (behind anything in front of it); -1 elsewhere. */
    private static int moon(int x, int y, int face, int size) {
        if (face != 1) return -1;
        int unit = Math.max(1, size / 128), mx = (x - size * 5 / 8) / unit, my = (y - size / 10) / unit;
        if (x < size * 5 / 8 || y < size / 10 || mx >= 6 || my >= 6) return -1;
        // a few darker maria on its face
        boolean mare = mx == 1 && my == 1 || mx == 3 && my == 2 || mx == 2 && my == 4 || mx == 4 && my == 4;
        return mare ? rgb(170, 176, 192) : rgb(222, 228, 238);
    }

    /** A star's brightness at this pixel, or 0: stars are 2x2 pixels, scattered evenly over the open sky. */
    private static int star(int x, int y, int face, int size) {
        int cell = Math.max(1, size / 512);
        int h = hash(x / (2 * cell), y / (2 * cell), face);
        if ((h & 0x3FF) >= 3) return 0;
        return 150 + (h >>> 10 & 0x63);
    }

    private static int hash(int x, int y, int face) {
        int h = x * 374761393 + y * 668265263 + face * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static int mix(int from, int to, float amount) {
        int r = Math.round((from >> 16 & 0xFF) * (1 - amount) + (to >> 16 & 0xFF) * amount);
        int g = Math.round((from >> 8 & 0xFF) * (1 - amount) + (to >> 8 & 0xFF) * amount);
        int b = Math.round((from & 0xFF) * (1 - amount) + (to & 0xFF) * amount);
        return rgb(r, g, b);
    }

    private static int rgb(int r, int g, int b) {
        return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }
}
