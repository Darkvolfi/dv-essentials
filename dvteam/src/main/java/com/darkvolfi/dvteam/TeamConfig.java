package com.darkvolfi.dvteam;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TeamConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_CLAIMS_ON_MINIMAP;
    public static final ForgeConfigSpec.BooleanValue SHOW_TERRITORY_BANNER;

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.IntValue MAX_CLAIMS_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue MAX_FORCE_LOADED_PER_PLAYER;
    public static final ForgeConfigSpec.BooleanValue PROTECT_EXPLOSIONS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_FAKE_PLAYERS;

    static {
        ForgeConfigSpec.Builder cb = new ForgeConfigSpec.Builder();
        SHOW_CLAIMS_ON_MINIMAP = cb.comment("Show claimed team chunks on the DV Map minimap.")
                .define("showClaimsOnMinimap", true);
        SHOW_TERRITORY_BANNER = cb.comment("Show an action-bar message when entering or leaving a claimed territory.")
                .define("showTerritoryBanner", true);
        CLIENT_SPEC = cb.build();

        ForgeConfigSpec.Builder sb = new ForgeConfigSpec.Builder();
        MAX_CLAIMS_PER_PLAYER = sb.comment("Maximum number of chunks each team member adds to the team's claim limit.")
                .defineInRange("maxClaimsPerPlayer", 64, 1, 1024);
        MAX_FORCE_LOADED_PER_PLAYER = sb.comment("Maximum number of force-loaded chunks each team member adds to the team's limit.")
                .defineInRange("maxForceLoadedPerPlayer", 8, 0, 128);
        PROTECT_EXPLOSIONS = sb.comment("Prevent explosions (Creeper, TNT, Wither) from breaking blocks in claimed chunks.")
                .define("protectExplosions", true);
        ALLOW_FAKE_PLAYERS = sb.comment("Allow modded machines (fake players) to work inside claimed chunks.")
                .define("allowFakePlayers", true);
        SERVER_SPEC = sb.build();
    }

    private TeamConfig() {}
}
