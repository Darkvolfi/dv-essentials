package com.darkvolfi.dvgrave;

import net.minecraftforge.common.ForgeConfigSpec;

public final class GraveConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue PRIVATE_MINUTES;
    public static final ForgeConfigSpec.BooleanValue CLAN_ACCESS;
    public static final ForgeConfigSpec.EnumValue<XpMode> XP;

    public enum XpMode {
        /** All of the player's experience. */
        ALL,
        /** As much as vanilla drops (7 per level, at most 100). */
        VANILLA,
        /** Experience drops on the ground as usual. */
        NONE
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        PRIVATE_MINUTES = b.comment("For how many minutes only the owner (and their clan) can open a grave; then anyone can. 0: anyone at once. -1: only ever the owner.")
                .defineInRange("privateMinutes", 30, -1, 10080);
        CLAN_ACCESS = b.comment("With DV Team: members of the owner's clan may open the grave while it is private.")
                .define("clanAccess", true);
        XP = b.comment("Experience kept in the grave: ALL of it, as much as VANILLA drops, or NONE (it drops as usual).")
                .defineEnum("experience", XpMode.ALL);
        SPEC = b.build();
    }

    private GraveConfig() {}
}
