package com.darkvolfi.dvbackpack;

import net.minecraftforge.common.ForgeConfigSpec;

/** World (server) settings. */
public final class BackpackConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SLEEPING_BAG_SETS_SPAWN;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SLEEPING_BAG_SETS_SPAWN = builder
                .comment("Sleeping in a backpack's sleeping bag also sets your spawn point there (like a bed).")
                .define("sleepingBagSetsSpawn", false);
        SPEC = builder.build();
    }

    private BackpackConfig() {}
}
