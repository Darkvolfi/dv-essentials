package com.darkvolfi.dvdark;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class DarkConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue DARKNESS;
    public static final ForgeConfigSpec.BooleanValue TINT;
    public static final ForgeConfigSpec.ConfigValue<String> ACCENT;
    public static final ForgeConfigSpec.BooleanValue DARK_TOOLTIPS;
    public static final ForgeConfigSpec.BooleanValue NIGHT_PANORAMA;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCEPTIONS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Dark menus and inventories. Also toggled with the DV Dark key (reloads textures).")
                .define("enabled", true);
        DARKNESS = b.comment("How dark menus get, in percent (higher is darker).")
                .defineInRange("darkness", 73, 40, 90);
        TINT = b.comment("Tint the dark menus with the accent colour.")
                .define("tint", false);
        ACCENT = b.comment("Accent colour as #RRGGBB: the tint, tooltip borders and the rims of DV screens.")
                .define("accent", "#8060C0", o -> o instanceof String s && s.matches("#[0-9A-Fa-f]{6}"));
        DARK_TOOLTIPS = b.comment("Item tooltips get a dark grey background and an accent-coloured border.")
                .define("darkTooltips", true);
        NIGHT_PANORAMA = b.comment("The title screen's background shows the same place at night: a starry sky, a moon and moonlit land.")
                .define("nightPanorama", true);
        EXCEPTIONS = b.comment("Never darken these: mod ids (e.g. create) or texture paths (e.g. minecraft:textures/gui/book.png).")
                .defineListAllowEmpty(List.of("exceptions"), List::of, o -> o instanceof String s && !s.isBlank());
        SPEC = b.build();
    }

    /** The accent as rgb, or -1 when menus are not tinted. */
    public static int accent() {
        return TINT.get() ? Integer.parseInt(ACCENT.get().substring(1), 16) : -1;
    }

    public static boolean enabled() {
        return SPEC.isLoaded() && ENABLED.get();
    }

    private DarkConfig() {}
}
