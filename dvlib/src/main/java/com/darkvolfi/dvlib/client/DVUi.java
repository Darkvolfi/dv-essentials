package com.darkvolfi.dvlib.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * DV's "clean pixel" look, shared by every DV screen: flat dark panels with a one-pixel rim, and small pixel icons
 * drawn from patterns instead of words. Colours follow DV Dark's accent when one is chosen.
 */
public final class DVUi {
    public static final int PANEL = 0xF0181620;
    public static final int RIM = 0xFF3A3548;
    public static final int RIM_HOVER = 0xFF8A80A8;
    public static final int SHADOW = 0x80000000;

    /** Icons are 7×7; '#' is filled. */
    public static final String[] SEARCH = {"..###..", ".#...#.", ".#...#.", ".#...#.", "..####.", ".....##", "......#"};
    public static final String[] PROFILES = {"######.", "#.##.#.", "#.##.#.", "######.", "#....#.", "#.##.#.", "######."};
    public static final String[] CLOSE = {"#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"};
    public static final String[] LOCK = {"..###..", ".#...#.", ".#...#.", "#######", "###.###", "###.###", "#######"};
    public static final String[] CHECK = {"......#", ".....##", "#...##.", "##.##..", ".###...", "..#....", "......."};
    public static final String[] SERVER = {"#######", "#.#...#", "#######", ".......", "#######", "#.#...#", "#######"};
    public static final String[] KEY = {".###...", "#...#..", "#...###", "#...#.#", ".###..#", ".......", "......."};
    public static final String[] PLUS = {"...#...", "...#...", "...#...", "#######", "...#...", "...#...", "...#..."};
    public static final String[] GEAR = {"..#.#..", ".#####.", "###.###", "##...##", "###.###", ".#####.", "..#.#.."};
    public static final String[] EYE = {".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."};
    public static final String[] LOAD = {"...#...", "...#...", ".#.#.#.", "..###..", "...#...", "#.....#", "#######"};
    public static final String[] FOLDER = {".......", "###....", "#..####", "#.....#", "#.....#", "#.....#", "#######"};
    public static final String[] UPDATE ={"...#...", "..###..", ".#####.", "#######", "..###..", "..###..", "..###.."};

    private DVUi() {}

    /** A panel with a drop shadow and a rim (the accent colour when DV Dark has one). */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, SHADOW);
        g.fill(x, y, x + w, y + h, hovered ? RIM_HOVER : rim());
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
    }

    public static int rim() {
        return DVTheme.dark && DVTheme.tint >= 0 ? 0xFF000000 | DVTheme.tint : RIM;
    }

    /** Highlight colour: DV Dark's accent, or a soft violet. */
    public static int accent() {
        return DVTheme.dark && DVTheme.tint >= 0 ? 0xFF000000 | DVTheme.tint : RIM_HOVER;
    }

    /** Draws a 7×7 pattern centred on (cx, cy), {@code px} screen pixels per icon pixel, with a dark outline. */
    public static void icon(GuiGraphics g, String[] pattern, int cx, int cy, int px, int argb) {
        int x0 = cx - pattern[0].length() * px / 2;
        int y0 = cy - pattern.length * px / 2;
        for (int pass = 0; pass < 2; pass++) {
            int color = pass == 0 ? 0xFF000000 : argb;
            int grow = pass == 0 ? 1 : 0;
            for (int r = 0; r < pattern.length; r++) {
                for (int c = 0; c < pattern[r].length(); c++) {
                    if (pattern[r].charAt(c) != '#') continue;
                    int x = x0 + c * px;
                    int y = y0 + r * px;
                    g.fill(x - grow, y - grow, x + px + grow, y + px + grow, color);
                }
            }
        }
    }
}
