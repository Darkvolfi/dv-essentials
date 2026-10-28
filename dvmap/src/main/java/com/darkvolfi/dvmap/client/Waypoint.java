package com.darkvolfi.dvmap.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Locale;

public final class Waypoint {
    public static final int[] PALETTE = {
            0xFF5555, // Red
            0xFFAA33, // Orange
            0xFFE055, // Gold
            0x66DD66, // Green
            0x33CCAA, // Teal
            0x4FC3F7, // Sky Blue
            0x5588FF, // Blue
            0x9B6BFF, // Purple
            0xF06292, // Pink
            0xFFFFFF, // White
            0xB0BEC5, // Silver
            0x8D6E63  // Brown
    };

    /** 7x7 pixel icons; '#' is filled. */
    public enum Shape {
        STAR("...#...", "..###..", "#######", ".#####.", "..###..", ".##.##.", "##...##"),
        CIRCLE("..###..", ".#####.", "#######", "#######", "#######", ".#####.", "..###.."),
        SQUARE("#######", "#######", "#######", "#######", "#######", "#######", "#######"),
        DIAMOND("...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."),
        TRIANGLE("...#...", "...#...", "..###..", "..###..", ".#####.", ".#####.", "#######"),
        HEART(".##.##.", "#######", "#######", ".#####.", "..###..", "...#...", "......."),
        CROSS("##...##", "###.###", ".#####.", "..###..", ".#####.", "###.###", "##...##");

        public static final int SIZE = 7;

        public String translationKey() {
            return "dvmap.shape." + name().toLowerCase(Locale.ROOT);
        }
        private final String[] rows;

        Shape(String... rows) {
            this.rows = rows;
        }

        /** Draws the icon centred on (cx, cy), {@code px} screen pixels per icon pixel, with a dark outline. */
        public void draw(GuiGraphics g, int cx, int cy, int px, int rgb) {
            int x0 = cx - SIZE * px / 2;
            int y0 = cy - SIZE * px / 2;
            for (int pass = 0; pass < 2; pass++) {
                int color = pass == 0 ? 0xFF000000 : 0xFF000000 | rgb;
                int grow = pass == 0 ? 1 : 0;
                for (int r = 0; r < SIZE; r++) {
                    for (int c = 0; c < SIZE; c++) {
                        if (rows[r].charAt(c) != '#') continue;
                        int x = x0 + c * px;
                        int y = y0 + r * px;
                        g.fill(x - grow, y - grow, x + px + grow, y + px + grow, color);
                    }
                }
            }
        }
    }

    public enum Category {
        GENERAL, HOME, MINE, VILLAGE, FARM, PORTAL, DANGER, EXPLORE;

        public String translationKey() {
            return "dvmap.category." + name().toLowerCase(Locale.ROOT);
        }
    }

    public String name;
    public int x;
    public int y;
    public int z;
    public String dimension;
    public int color;
    public boolean enabled;
    public boolean deathPoint;
    /** Null in files saved before shapes existed. */
    public Shape shape;
    public Category category;
    /** Placed from the map on a chunk that wasn't loaded: y is a guess, set to the surface once it loads. */
    public boolean autoY;

    public Waypoint() {}

    public Shape shape() {
        if (deathPoint) return Shape.CROSS;
        return shape != null ? shape : Shape.STAR;
    }

    public Category category() {
        return category != null ? category : Category.GENERAL;
    }

    /** Name with its category in front, e.g. "[Home] Base"; just the name for General. */
    public Component label() {
        if (category() == Category.GENERAL) return Component.literal(name);
        return Component.literal("[").append(Component.translatable(category().translationKey())).append("] " + name);
    }

    public Waypoint(String name, int x, int y, int z, String dimension, int color, boolean enabled, boolean deathPoint) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.color = color & 0xFFFFFF;
        this.enabled = enabled;
        this.deathPoint = deathPoint;
    }

    public boolean isInDimension(ResourceKey<Level> dim) {
        return dim != null && dim.location().toString().equals(dimension);
    }
}
