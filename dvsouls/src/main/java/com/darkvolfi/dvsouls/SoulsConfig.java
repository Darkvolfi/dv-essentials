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
    public static final ForgeConfigSpec.DoubleValue BIND_COST;
    public static final ForgeConfigSpec.DoubleValue EMPOWER_MAX;
    public static final ForgeConfigSpec.DoubleValue HORSE_SPEED_MAX;
    public static final ForgeConfigSpec.DoubleValue HORSE_JUMP_MAX;
    public static final ForgeConfigSpec.DoubleValue HORSE_HEALTH_MAX;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> UNCAPTURABLE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        FURNACE_ITEMS_PER_HEART = builder
                .comment("Items the Soul Furnace smelts per heart of the soul it burns.")
                .defineInRange("furnaceItemsPerHeart", 2, 1, 64);
        FUSER_FUEL_PER_HEART = builder
                .comment("Ticks the Soul Fuser and the Soul Distiller burn per heart of its fuel's soul.")
                .defineInRange("fuserFuelPerHeart", 400, 20, 10000);
        FUSE_TIME = builder
                .comment("Ticks one fusion takes in the Soul Fuser.")
                .defineInRange("fuseTime", 200, 20, 2400);
        DISTIL_TIME = builder
                .comment("Ticks one soul takes to distil into an essence in the Soul Distiller.")
                .defineInRange("distilTime", 200, 20, 2400);
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
