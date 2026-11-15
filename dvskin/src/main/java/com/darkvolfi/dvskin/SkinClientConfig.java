package com.darkvolfi.dvskin;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The player's own choice. The pictures themselves are skin.png, cape.png and elytra.png next to this file;
 * these values say which are in use and where they came from.
 */
public final class SkinClientConfig {
    public static final ForgeConfigSpec SPEC;
    /** "" (Minecraft's own skin), or where skin.png came from: "file:<path>", "mojang:<name>", "elyby:<name>", "url:<link>". */
    public static final ForgeConfigSpec.ConfigValue<String> SKIN;
    public static final ForgeConfigSpec.BooleanValue SLIM;
    /** "" (none: a real Mojang cape stays), "dv:<name>", or a custom cape.png: "file:<path>", "url:<link>". */
    public static final ForgeConfigSpec.ConfigValue<String> CAPE;
    /** "" (vanilla: the elytra follows the cape), "dv:<name>", or a custom elytra.png: "file:<path>", "url:<link>". */
    public static final ForgeConfigSpec.ConfigValue<String> ELYTRA;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Set from the DV Skin screen. The same look is used on every server and in single player.");
        SKIN = b.comment("Skin source; empty uses Minecraft's own skin.").define("skin", "");
        SLIM = b.comment("Slim (Alex) arms instead of wide (Steve) arms.").define("slim", false);
        CAPE = b.comment("Cape: empty (none), dv:<name>, or the source of cape.png.").define("cape", "");
        ELYTRA = b.comment("Elytra: empty (follows the cape), dv:<name>, or the source of elytra.png.").define("elytra", "");
        SPEC = b.build();
    }

    private SkinClientConfig() {}
}
