package com.darkvolfi.dvzoom;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ZoomConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue ZOOM_LEVEL;
    public static final ForgeConfigSpec.BooleanValue SCROLL_ADJUST;
    public static final ForgeConfigSpec.BooleanValue SMOOTH_TRANSITION;
    public static final ForgeConfigSpec.BooleanValue CINEMATIC_CAMERA;

    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 50.0;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ZOOM_LEVEL = b.comment("Magnification when the zoom key is first pressed.")
                .defineInRange("zoomLevel", 4.0, MIN_ZOOM, MAX_ZOOM);
        SCROLL_ADJUST = b.comment("Mouse wheel changes the magnification while zooming.")
                .define("scrollAdjust", true);
        SMOOTH_TRANSITION = b.comment("Animate zooming in and out instead of snapping.")
                .define("smoothTransition", true);
        CINEMATIC_CAMERA = b.comment("Use the smooth (cinematic) camera while zooming.")
                .define("cinematicCamera", true);
        SPEC = b.build();
    }

    private ZoomConfig() {}
}
