package com.darkvolfi.dvskin;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SkinServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ALLOW_SKINS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CAPES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ALLOW_SKINS = b.comment("Share the skins players choose with everyone on the server.")
                .define("allowSkins", true);
        ALLOW_CAPES = b.comment("Share the capes and elytra players choose with everyone on the server.")
                .define("allowCapes", true);
        SPEC = b.build();
    }

    private SkinServerConfig() {}
}
