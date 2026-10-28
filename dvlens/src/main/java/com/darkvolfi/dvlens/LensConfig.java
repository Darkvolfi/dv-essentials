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
    public static final ForgeConfigSpec.BooleanValue SHOW_FACE;
    public static final ForgeConfigSpec.BooleanValue SHOW_VILLAGER;
    public static final ForgeConfigSpec.BooleanValue SHOW_OWNER;
    public static final ForgeConfigSpec.BooleanValue SHOW_REDSTONE;
    public static final ForgeConfigSpec.BooleanValue SHOW_SPAWNER;
    public static final ForgeConfigSpec.BooleanValue SHOW_CONTENTS;
    public static final ForgeConfigSpec.BooleanValue SHOW_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue SHOW_DETAILS;
    public static final ForgeConfigSpec.BooleanValue SHOW_BREAK_PROGRESS;
    public static final ForgeConfigSpec.BooleanValue SHOW_MACHINES;
    public static final ForgeConfigSpec.BooleanValue SHOW_LAND;

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.BooleanValue SHARE_CONTENTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Show the info box. Also toggled with the DV Lens key.")
                .define("enabled", true);
        SHOW_MOD_NAME = b.comment("Show which mod the block or entity comes from.")
                .define("showModName", true);
        SHOW_HARVEST = b.comment("Show the tool a block needs and whether your held item can harvest it.")
                .define("showHarvest", true);
        SHOW_GROWTH = b.comment("Show crop growth, and for animals the time until grown up and until they can breed again (needs DV Lens on the server).")
                .define("showGrowth", true);
        SHOW_HEALTH = b.comment("Show the health of living entities.")
                .define("showHealth", true);
        SHOW_HORSE_STATS = b.comment("Show speed and jump height of horses, donkeys, mules and camels.")
                .define("showHorseStats", true);
        SHOW_FACE = b.comment("Show the face of the entity you're looking at instead of its spawn egg.")
                .define("showFace", true);
        SHOW_VILLAGER = b.comment("Show a villager's profession and level.")
                .define("showVillager", true);
        SHOW_OWNER = b.comment("Show who owns a tamed animal.")
                .define("showOwner", true);
        SHOW_REDSTONE = b.comment("Show redstone power, repeater delay, comparator mode and what an observer watches.")
                .define("showRedstone", true);
        SHOW_SPAWNER = b.comment("Show which mob a spawner spawns.")
                .define("showSpawner", true);
        SHOW_CONTENTS = b.comment("Show what's inside chests, furnaces, brewing stands and other containers, and how far they are (needs DV Lens on the server).")
                .define("showContents", true);
        SHOW_EFFECTS = b.comment("Show the potion effects on a mob (needs DV Lens on the server).")
                .define("showEffects", true);
        SHOW_DETAILS = b.comment("Show block and entity details: note block note, unstable TNT, honey and bees in a hive, painting title, item frame item, armor stand gear, command block command.")
                .define("showDetails", true);
        SHOW_BREAK_PROGRESS = b.comment("Show a progress bar while breaking a block.")
                .define("showBreakProgress", true);
        SHOW_MACHINES = b.comment("Show stored energy (FE/RF), fluid tanks and Create rotation speed and stress of any mod's machines (needs DV Lens on the server).")
                .define("showMachines", true);
        SHOW_LAND = b.comment("With DV Team: show which clan and settlement the block you look at belongs to.")
                .define("showLand", true);
        SPEC = b.build();

        ForgeConfigSpec.Builder s = new ForgeConfigSpec.Builder();
        SHARE_CONTENTS = s.comment("Let players see what's inside containers they look at. Turn off if players shouldn't peek into other people's chests. With DV Team, containers in settlements where the player may not open them always stay hidden.")
                .define("shareContainerContents", true);
        SERVER_SPEC = s.build();
    }

    private LensConfig() {}
}
