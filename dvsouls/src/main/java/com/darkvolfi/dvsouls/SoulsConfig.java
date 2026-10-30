package com.darkvolfi.dvsouls;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** World (server) settings. */
public final class SoulsConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue FURNACE_ITEMS_PER_HEART;
    public static final ForgeConfigSpec.IntValue FUSER_FUEL_PER_HEART;
    public static final ForgeConfigSpec.IntValue FUSE_TIME;
    public static final ForgeConfigSpec.IntValue DISTIL_TIME;
    public static final ForgeConfigSpec.IntValue REFINE_TIME;
    public static final ForgeConfigSpec.IntValue PURE_SOUL_PER_HEART;
    public static final ForgeConfigSpec.IntValue SOULS_PER_AMETHYST;
    public static final ForgeConfigSpec.DoubleValue CORRUPT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue BIND_COST;
    public static final ForgeConfigSpec.DoubleValue EMPOWER_MAX;
    public static final ForgeConfigSpec.DoubleValue HORSE_SPEED_MAX;
    public static final ForgeConfigSpec.DoubleValue HORSE_JUMP_MAX;
    public static final ForgeConfigSpec.DoubleValue HORSE_HEALTH_MAX;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> UNCAPTURABLE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        FURNACE_ITEMS_PER_HEART = builder
                .comment("Items the soul furnaces cook per soul from their bar.")
                .defineInRange("furnaceItemsPerHeart", 2, 1, 64);
        FUSER_FUEL_PER_HEART = builder
                .comment("Ticks the Soul Fuser and the Soul Distiller work on one soul from their bar.")
                .defineInRange("fuserFuelPerHeart", 400, 20, 10000);
        FUSE_TIME = builder
                .comment("Ticks one fusion takes in the Soul Fuser.")
                .defineInRange("fuseTime", 200, 20, 2400);
        DISTIL_TIME = builder
                .comment("Ticks one soul takes to distil into an essence in the Soul Distiller.")
                .defineInRange("distilTime", 200, 20, 2400);
        REFINE_TIME = builder
                .comment("Ticks one soul takes to refine into pure soul in the Soul Refiner.")
                .defineInRange("refineTime", 100, 10, 2400);
        SOULS_PER_AMETHYST = builder
                .comment("Souls the Soul Refiner refines before its amethyst shard wears away.")
                .defineInRange("soulsPerAmethyst", 8, 1, 1000);
        PURE_SOUL_PER_HEART = builder
                .comment("Pure soul (mB) in one soul, the worth of one heart; a bucket is 1000.")
                .defineInRange("pureSoulPerHeart", 100, 10, 1000);
        CORRUPT_CHANCE = builder
                .comment("Chance that a creature's soul offered for another kind (bringing back, binding) corrupts it.")
                .defineInRange("corruptChance", 0.25, 0.0, 1.0);
        BIND_COST = builder
                .comment("At the Binding Altar, the offered soul needs this share of the bound creature's hearts.")
                .defineInRange("bindCost", 0.5, 0.0, 4.0);
        EMPOWER_MAX = builder
                .comment("Fusing two souls of the same kind adds their health, up to this many times the kind's own.")
                .defineInRange("empowerMax", 4.0, 1.0, 20.0);
        HORSE_SPEED_MAX = builder
                .comment("The fastest a soul can make a horse (0.3375 is the fastest a horse can be born).")
                .defineInRange("horseSpeedMax", 0.3375, 0.1, 1.0);
        HORSE_JUMP_MAX = builder
                .comment("The highest a soul can make a horse jump.")
                .defineInRange("horseJumpMax", 1.0, 0.4, 2.0);
        HORSE_HEALTH_MAX = builder
                .comment("The most health a soul can give a horse.")
                .defineInRange("horseHealthMax", 30.0, 15.0, 100.0);
        UNCAPTURABLE = builder
                .comment("Creatures no jar can hold, besides bosses (ids like minecraft:villager).")
                .defineListAllowEmpty("uncapturable", List.of(), id -> id instanceof String);
        SPEC = builder.build();
    }

    private SoulsConfig() {}
}
