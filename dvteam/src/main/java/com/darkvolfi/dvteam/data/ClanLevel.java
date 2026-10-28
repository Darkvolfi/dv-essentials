package com.darkvolfi.dvteam.data;

/**
 * A clan grows with members and land: every member is worth 10 points and every claimed chunk 1.
 * Higher levels unlock more of the server's chunk and force-load limits; the server limits are what level 5 gets.
 * Shared by the server rules and the screens (which get the server limits in the sync).
 */
public final class ClanLevel {
    public static final int MAX = 5;
    /** Points needed for levels 1..5. */
    private static final int[] POINTS = {0, 25, 60, 110, 180};

    private ClanLevel() {}

    public static int points(int members, int chunks) {
        return members * 10 + chunks;
    }

    public static int level(int points) {
        int level = 1;
        for (int i = 1; i < POINTS.length; i++) if (points >= POINTS[i]) level = i + 1;
        return level;
    }

    /** Points the next level needs, or -1 at the top. */
    public static int nextPoints(int level) {
        return level >= MAX ? -1 : POINTS[level];
    }

    /** 40% of the server limit at level 1, +15% per level, all of it at level 5. */
    public static int chunkLimit(int serverMax, int level) {
        return Math.max(1, Math.round(serverMax * (0.4f + 0.15f * (level - 1))));
    }

    /** A fifth of the server limit per level. */
    public static int forcedLimit(int serverMax, int level) {
        return Math.round(serverMax * level / (float) MAX);
    }
}
