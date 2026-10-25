package com.darkvolfi.dvlens;

import net.minecraftforge.common.ForgeConfigSpec;

public final class LensConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue SHOW_MOD_NAME;
    public static final ForgeConfigSpec.BooleanValue SHOW_HARVEST;
    public static final ForgeConfigSpec.BooleanValue SHOW_GROWTH;
    public static final ForgeConfigSpec.BooleanValue SHOW_HEALTH;
    public static final ForgeConfigSpec.BooleanValue SHOW_HORSE_STATS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Show the info box. Also toggled with the DV Lens key.")
                .define("enabled", true);
        SHOW_MOD_NAME = b.comment("Show which mod the block or entity comes from.")
                .define("showModName", true);
        SHOW_HARVEST = b.comment("Show the tool a block needs and whether your held item can harvest it.")
                .define("showHarvest", true);
        SHOW_GROWTH = b.comment("Show crop growth.")
                .define("showGrowth", true);
        SHOW_HEALTH = b.comment("Show the health of living entities.")
                .define("showHealth", true);
        SHOW_HORSE_STATS = b.comment("Show speed and jump height of horses, donkeys, mules and camels.")
                .define("showHorseStats", true);
        SPEC = b.build();
    }

    private LensConfig() {}
}
