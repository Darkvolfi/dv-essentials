package com.darkvolfi.dvdark;

import net.minecraftforge.common.ForgeConfigSpec;

public final class DarkConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Dark menus and inventories. Also toggled with the DV Dark key (reloads textures).")
                .define("enabled", true);
        SPEC = b.build();
    }

    public static boolean enabled() {
        return SPEC.isLoaded() && ENABLED.get();
    }

    private DarkConfig() {}
}
