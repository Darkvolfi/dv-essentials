package com.darkvolfi.dvteam;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TeamConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_CLAIMS_ON_MINIMAP;
    public static final ForgeConfigSpec.BooleanValue SHOW_TERRITORY_BANNER;

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.IntValue MAX_CHUNKS_PER_CLAN;
    public static final ForgeConfigSpec.IntValue MAX_VILLAGES;
    public static final ForgeConfigSpec.IntValue MAX_TRADE_CENTERS;
    public static final ForgeConfigSpec.IntValue MAX_VILLAGE_CHUNKS;
    public static final ForgeConfigSpec.IntValue MAX_FORCE_LOADED;
    public static final ForgeConfigSpec.IntValue MIN_GAP;
    public static final ForgeConfigSpec.BooleanValue PROTECT_EXPLOSIONS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_FAKE_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue WARS_ENABLED;
    public static final ForgeConfigSpec.IntValue WAR_WARMUP_MINUTES;
    public static final ForgeConfigSpec.IntValue WAR_MAX_MINUTES;
    public static final ForgeConfigSpec.IntValue WAR_COOLDOWN_HOURS;
    public static final ForgeConfigSpec.BooleanValue OFFLINE_RAIDS;

    static {
        ForgeConfigSpec.Builder cb = new ForgeConfigSpec.Builder();
        SHOW_CLAIMS_ON_MINIMAP = cb.comment("Show clan territory on the DV Map minimap.")
                .define("showClaimsOnMinimap", true);
        SHOW_TERRITORY_BANNER = cb.comment("Show an action-bar message when entering or leaving a settlement.")
                .define("showTerritoryBanner", true);
        CLIENT_SPEC = cb.build();

        ForgeConfigSpec.Builder sb = new ForgeConfigSpec.Builder();
        MAX_CHUNKS_PER_CLAN = sb.comment("Most chunks a clan can claim, all settlements together.")
                .defineInRange("maxChunksPerClan", 256, 1, 100000);
        MAX_VILLAGES = sb.comment("Most villages a clan can found besides its capital.")
                .defineInRange("maxVillages", 5, 0, 1000);
        MAX_TRADE_CENTERS = sb.comment("Most trade centers a clan can found.")
                .defineInRange("maxTradeCenters", 1, 0, 1000);
        MAX_VILLAGE_CHUNKS = sb.comment("Most chunks in one village or trade center (the capital is only limited by the clan total).")
                .defineInRange("maxVillageChunks", 32, 1, 100000);
        MAX_FORCE_LOADED = sb.comment("Most chunks a clan can keep loaded.")
                .defineInRange("maxForceLoaded", 8, 0, 1000);
        MIN_GAP = sb.comment("Free chunks required between the settlements of different clans.")
                .defineInRange("minGap", 2, 0, 64);
        PROTECT_EXPLOSIONS = sb.comment("Explosions don't break blocks in claimed chunks (except during a raid).")
                .define("protectExplosions", true);
        ALLOW_FAKE_PLAYERS = sb.comment("Allow modded machines (fake players) to work inside claimed chunks.")
                .define("allowFakePlayers", true);
        WARS_ENABLED = sb.comment("Allow clan leaders to declare war.")
                .define("warsEnabled", true);
        WAR_WARMUP_MINUTES = sb.comment("Minutes between declaring war and the fighting starting.")
                .defineInRange("warWarmupMinutes", 10, 0, 1440);
        WAR_MAX_MINUTES = sb.comment("A war ends by itself after this many minutes.")
                .defineInRange("warMaxMinutes", 120, 1, 10080);
        WAR_COOLDOWN_HOURS = sb.comment("Hours before the same two clans can go to war again.")
                .defineInRange("warCooldownHours", 24, 0, 720);
        OFFLINE_RAIDS = sb.comment("Allow raiding a clan's land while none of its members is online.")
                .define("offlineRaids", false);
        SERVER_SPEC = sb.build();
    }

    private TeamConfig() {}
}
