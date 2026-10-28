package com.darkvolfi.dvfood;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FoodConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_SATURATION;
    public static final ForgeConfigSpec.BooleanValue SHOW_PREVIEW;
    public static final ForgeConfigSpec.BooleanValue SHOW_HEALTH_PREVIEW;
    public static final ForgeConfigSpec.BooleanValue SHOW_EXHAUSTION;
    public static final ForgeConfigSpec.BooleanValue SHOW_TOOLTIP;
    public static final ForgeConfigSpec.BooleanValue SHOW_EFFECTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        SHOW_SATURATION = b.comment("Outline the hunger icons in gold up to your current saturation.")
                .define("showSaturation", true);
        SHOW_PREVIEW = b.comment("While holding food, flash the hunger and saturation it would restore.")
                .define("showPreview", true);
        SHOW_HEALTH_PREVIEW = b.comment("While holding food, flash the health it would restore through regeneration.")
                .define("showHealthPreview", true);
        SHOW_EXHAUSTION = b.comment("Show exhaustion (progress to the next hunger loss) behind the hunger bar.")
                .define("showExhaustion", true);
        SHOW_TOOLTIP = b.comment("Show hunger and saturation icons in food tooltips.")
                .define("showTooltip", true);
        SHOW_EFFECTS = b.comment("List the effects a food gives (golden apple, pufferfish...) in its tooltip, with duration and chance.")
                .define("showEffects", true);
        SPEC = b.build();
    }

    private FoodConfig() {}
}
