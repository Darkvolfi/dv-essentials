package com.darkvolfi.dvzoom;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ZoomConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue ZOOM_LEVEL;
    public static final ForgeConfigSpec.BooleanValue SCROLL_ADJUST;
    public static final ForgeConfigSpec.BooleanValue SMOOTH_TRANSITION;
    public static final ForgeConfigSpec.BooleanValue CINEMATIC_CAMERA;
    public static final ForgeConfigSpec.BooleanValue SLOW_MOUSE;
    public static final ForgeConfigSpec.IntValue MOUSE_SLOWDOWN;
    public static final ForgeConfigSpec.BooleanValue HIDE_HUD;
    public static final ForgeConfigSpec.BooleanValue HIDE_HAND;

    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 50.0;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ZOOM_LEVEL = b.comment("Magnification when the zoom key is first pressed.")
                .defineInRange("zoomLevel", 4.0, MIN_ZOOM, MAX_ZOOM);
        SLOW_MOUSE = b.comment("Lower the mouse sensitivity the further you zoom in.")
                .define("slowMouse", true);
        MOUSE_SLOWDOWN = b.comment("How much the mouse slows down while zooming, in percent. 0 = normal speed, 100 = slowed by the full zoom factor (4x zoom -> 4x slower).")
                .defineInRange("mouseSlowdown", 50, 0, 100);
        SCROLL_ADJUST = b.comment("Mouse wheel changes the magnification while zooming.")
                .define("scrollAdjust", true);
        SMOOTH_TRANSITION = b.comment("Animate zooming in and out instead of snapping.")
                .define("smoothTransition", true);
        CINEMATIC_CAMERA = b.comment("Use the smooth (cinematic) camera while zooming.")
                .define("cinematicCamera", true);
        HIDE_HUD = b.comment("Hide the HUD (hotbar, crosshair, chat) while zooming.")
                .define("hideHud", true);
        HIDE_HAND = b.comment("Hide your hand and held item while zooming.")
                .define("hideHand", true);
        SPEC = b.build();
    }

    private ZoomConfig() {}
}
