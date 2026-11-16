package com.darkvolfi.dvlight;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class LightConfig {
    public enum Mode { OFF, FAST, FANCY }
    public enum Shape { ROUND, CONE }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<Mode> MODE;
    public static final ForgeConfigSpec.IntValue RANGE;
    public static final ForgeConfigSpec.EnumValue<Shape> SHAPE;
    public static final ForgeConfigSpec.IntValue CONE_ANGLE;
    public static final ForgeConfigSpec.IntValue CONE_RANGE;
    public static final ForgeConfigSpec.BooleanValue SMOOTH;
    public static final ForgeConfigSpec.BooleanValue ENCHANTED_GLOW;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_LIGHTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_LIGHTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MODE = b.comment("Off, Fast (updates less often, lights move a block at a time, no fading) or Fancy (lights glide smoothly).")
                .defineEnum("mode", Mode.FANCY);
        RANGE = b.comment("Light sources further than this many blocks from you are not lit.")
                .defineInRange("range", 48, 8, 128);
        SHAPE = b.comment("Light from players' hands: Round, or a Cone like a flashlight, toward where they look.")
                .defineEnum("shape", Shape.ROUND);
        CONE_ANGLE = b.comment("How wide the cone is, in degrees.")
                .defineInRange("coneAngle", 40, 10, 120);
        CONE_RANGE = b.comment("How far the cone reaches, in blocks.")
                .defineInRange("coneRange", 20, 8, 32);
        SMOOTH = b.comment("Lights fade in and out instead of switching at once (Fancy only).")
                .define("smooth", true);
        ENCHANTED_GLOW = b.comment("Enchanted items give off a faint light.")
                .define("enchantedGlow", false);
        ITEM_LIGHTS = b.comment("Light of items that are not light blocks, as item=level (0-15). Light blocks (torch, lantern...) light up on their own; list them here to change their level.")
                .defineListAllowEmpty("itemLights", List.of(
                        "minecraft:lava_bucket=15", "minecraft:blaze_rod=10", "minecraft:blaze_powder=8",
                        "minecraft:glowstone_dust=8", "minecraft:glow_berries=8", "minecraft:glow_ink_sac=6",
                        "minecraft:magma_cream=6", "minecraft:nether_star=10", "minecraft:spectral_arrow=6",
                        "minecraft:fire_charge=10", "minecraft:amethyst_shard=4"), LightConfig::isEntry);
        ENTITY_LIGHTS = b.comment("Creatures and flying things that give off light, as entity=level (0-15). Burning creatures always light up.")
                .defineListAllowEmpty("entityLights", List.of(
                        "minecraft:blaze=12", "minecraft:magma_cube=8", "minecraft:glow_squid=8", "minecraft:allay=6",
                        "minecraft:fireball=14", "minecraft:small_fireball=12", "minecraft:dragon_fireball=10",
                        "minecraft:wither_skull=6", "minecraft:firework_rocket=12", "minecraft:tnt=12",
                        "minecraft:spectral_arrow=6", "minecraft:glow_item_frame=6"), LightConfig::isEntry);
        SPEC = b.build();
    }

    private static boolean isEntry(Object o) {
        return o instanceof String s && s.matches("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+=\\d{1,2}");
    }

    private LightConfig() {}
}
