package com.darkvolfi.dvdark;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class DarkConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue DARKNESS;
    public static final ForgeConfigSpec.EnumValue<Accent> ACCENT;
    public static final ForgeConfigSpec.BooleanValue DARK_TOOLTIPS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCEPTIONS;

    /** Colour the dark greys lean towards. */
    public enum Accent {
        NONE(-1), BLUE(0x5070C0), PURPLE(0x8060C0), GREEN(0x50A060), RED(0xC05050), ORANGE(0xC08040);

        public final int rgb;

        Accent(int rgb) {
            this.rgb = rgb;
        }
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Dark menus and inventories. Also toggled with the DV Dark key (reloads textures).")
                .define("enabled", true);
        DARKNESS = b.comment("How dark menus get, in percent (higher is darker).")
                .defineInRange("darkness", 73, 40, 90);
        ACCENT = b.comment("Tint the dark menus with a colour.")
                .defineEnum("accent", Accent.NONE);
        DARK_TOOLTIPS = b.comment("Item tooltips get a dark grey background and an accent-coloured border.")
                .define("darkTooltips", true);
        EXCEPTIONS = b.comment("Never darken these: mod ids (e.g. create) or texture paths (e.g. minecraft:textures/gui/book.png).")
                .defineListAllowEmpty(List.of("exceptions"), List::of, o -> o instanceof String s && !s.isBlank());
        SPEC = b.build();
    }

    public static boolean enabled() {
        return SPEC.isLoaded() && ENABLED.get();
    }

    private DarkConfig() {}
}
