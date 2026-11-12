package com.darkvolfi.dvmap;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server rules for what players may hide from each other. */
public final class MapServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ALLOW_HIDDEN_NAME_TAGS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ALLOW_HIDDEN_NAME_TAGS = b.comment("Players who hide their name on maps also hide the name tag over their head (for others with DV Map). Turn off for PvP servers where name tags must stay visible.")
                .define("allowHiddenNameTags", true);
        SPEC = b.build();
    }

    private MapServerConfig() {}
}
