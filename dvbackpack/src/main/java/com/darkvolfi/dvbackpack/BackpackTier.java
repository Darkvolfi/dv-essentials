package com.darkvolfi.dvbackpack;

import java.util.Locale;

/** Backpack sizes; each holds a whole number of 9-slot rows. */
public enum BackpackTier {
    LEATHER(27, 0x6E4426),
    COPPER(45, 0xC8714A),
    IRON(54, 0xC4C4C4),
    GOLD(81, 0xF2C230),
    DIAMOND(108, 0x4FD8D0),
    NETHERITE(135, 0x4A4145);

    private final int slots;
    private final int color;

    BackpackTier(int slots, int color) {
        this.slots = slots;
        this.color = color;
    }

    public int slots() {
        return slots;
    }

    /** Upgrade card slots: one for leather, one more for each tier above it. */
    public int upgradeSlots() {
        return ordinal() + 1;
    }

    public int rows() {
        return slots / 9;
    }

    /** The colour of the lid and the piping, tinted onto their grey textures; the bag itself is always leather. */
    public int color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
