package com.darkvolfi.dvglass;

import net.minecraftforge.common.ForgeConfigSpec;

public final class GlassConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BORDERLESS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        BORDERLESS = b.comment("Clear glass: drop the streaks inside the glass, so only the outer frame of each connected surface is left.")
                .define("borderless", false);
        SPEC = b.build();
    }

    private GlassConfig() {}
}
