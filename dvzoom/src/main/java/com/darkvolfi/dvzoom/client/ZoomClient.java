package com.darkvolfi.dvzoom.client;

import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvzoom.DVZoom;
import com.darkvolfi.dvzoom.ZoomConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = DVZoom.MOD_ID, value = Dist.CLIENT)
public final class ZoomClient {
    public static final KeyMapping ZOOM_KEY = new KeyMapping("key.dvzoom.zoom",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, DVLib.KEY_CATEGORY);

    private static final double SCROLL_STEP = 1.25;
    private static final double SMOOTHING = 0.5;

    private static boolean zooming;
    private static double targetZoom = 1.0;
    private static double currentZoom = 1.0;
    private static double previousZoom = 1.0;
    private static boolean savedSmoothCamera;

    private ZoomClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        boolean wantZoom = ZOOM_KEY.isDown() && mc.screen == null && mc.player != null;
        if (wantZoom && !zooming) startZoom(mc);
        else if (!wantZoom && zooming) stopZoom(mc);

        double target = zooming ? targetZoom : 1.0;
        previousZoom = currentZoom;
        if (ZoomConfig.SMOOTH_TRANSITION.get()) {
            currentZoom += (target - currentZoom) * SMOOTHING;
            if (Math.abs(target - currentZoom) < 0.01) currentZoom = target;
        } else {
            currentZoom = target;
        }
    }

    private static void startZoom(Minecraft mc) {
        zooming = true;
        targetZoom = ZoomConfig.ZOOM_LEVEL.get();
        if (ZoomConfig.CINEMATIC_CAMERA.get()) {
            savedSmoothCamera = mc.options.smoothCamera;
            mc.options.smoothCamera = true;
        }
    }

    private static void stopZoom(Minecraft mc) {
        zooming = false;
        if (ZoomConfig.CINEMATIC_CAMERA.get()) {
            mc.options.smoothCamera = savedSmoothCamera;
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        // Only the world view; leave the held-item FOV alone.
        if (!event.usedConfiguredFov()) return;
        double zoom = Mth.lerp(event.getPartialTick(), previousZoom, currentZoom);
        if (zoom != 1.0) event.setFOV(event.getFOV() / zoom);
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!zooming || !ZoomConfig.SCROLL_ADJUST.get()) return;
        double factor = event.getScrollDelta() > 0 ? SCROLL_STEP : 1.0 / SCROLL_STEP;
        targetZoom = Mth.clamp(targetZoom * factor, ZoomConfig.MIN_ZOOM, ZoomConfig.MAX_ZOOM);
        event.setCanceled(true); // don't switch hotbar slots while zooming
    }

    @Mod.EventBusSubscriber(modid = DVZoom.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(ZOOM_KEY);
        }
    }
}
