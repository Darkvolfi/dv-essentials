package com.darkvolfi.dvmusic;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MusicConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<Mode> MODE;
    public static final ForgeConfigSpec.EnumValue<Gaps> GAPS;
    public static final ForgeConfigSpec.BooleanValue SHOW_TOAST;
    public static final ForgeConfigSpec.BooleanValue REPEAT;
    public static final ForgeConfigSpec.EnumValue<Corner> PLAYER_CORNER;

    /** Which songs play. */
    public enum Mode { MIXED, MINECRAFT, MINE }

    /** Silence between songs: Minecraft's own (minutes), short (5 to 30 seconds) or none. */
    public enum Gaps { SHORT, MINECRAFT, NONE }

    /** Where the small player sits in the pause menu. */
    public enum Corner { TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, HIDDEN }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MODE = b.comment("Which songs play: Minecraft's and yours MIXED, only MINECRAFT's, or only MINE (from the dvmusic folder).")
                .defineEnum("mode", Mode.MIXED);
        GAPS = b.comment("Silence between songs: SHORT (5 to 30 seconds), MINECRAFT's own (several minutes) or NONE.")
                .defineEnum("gaps", Gaps.SHORT);
        REPEAT = b.comment("Play the same song again when it ends.")
                .define("repeat", false);
        SHOW_TOAST = b.comment("Show the song's name in a corner when one starts.")
                .define("showToast", true);
        PLAYER_CORNER = b.comment("Where the small music player sits in the pause menu, or HIDDEN.")
                .defineEnum("playerCorner", Corner.TOP_RIGHT);
        SPEC = b.build();
    }

    private MusicConfig() {}
}
