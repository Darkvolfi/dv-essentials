package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvmap.DVMap;
import com.darkvolfi.dvmap.MapConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = DVMap.MOD_ID, value = Dist.CLIENT)
public final class MapClient {
    public static final KeyMapping OPEN_MAP_KEY = new KeyMapping(
            "key.dvmap.open_map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            DVLib.KEY_CATEGORY
    );

    public static final KeyMapping NEW_WAYPOINT_KEY = new KeyMapping(
            "key.dvmap.new_waypoint",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            DVLib.KEY_CATEGORY
    );

    public static final KeyMapping TOGGLE_MINIMAP_KEY = new KeyMapping(
            "key.dvmap.toggle_minimap",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            DVLib.KEY_CATEGORY
    );

    private static int autosaveTicks;
    private static boolean effectsShifted;

    private MapClient() {}

    @Mod.EventBusSubscriber(modid = DVMap.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MAP_KEY);
            event.register(NEW_WAYPOINT_KEY);
            event.register(TOGGLE_MINIMAP_KEY);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        while (OPEN_MAP_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new WorldMapScreen());
            }
        }
        while (NEW_WAYPOINT_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new WaypointEditScreen(null, null,
                        mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
                        mc.level.dimension().location().toString()));
            }
        }
        while (TOGGLE_MINIMAP_KEY.consumeClick()) {
            MapConfig.MINIMAP_ENABLED.set(!MapConfig.MINIMAP_ENABLED.get());
            MapConfig.SPEC.save();
        }

        MapData.tickScan(mc);
        CreateTrains.tick(mc);
        RemotePlayers.tick();

        if (++autosaveTicks >= 200) {
            autosaveTicks = 0;
            MapData.saveDirtyRegions();
        }
        if (autosaveTicks % 20 == 0) settleAutoHeights(mc);
    }

    /** Waypoints placed on unloaded chunks get the surface height once their chunk loads. */
    private static void settleAutoHeights(Minecraft mc) {
        boolean changed = false;
        for (Waypoint wp : WaypointManager.forDimension(mc.level.dimension())) {
            if (!wp.autoY || !MapData.isLoaded(mc.level, wp.x, wp.z)) continue;
            wp.y = MapData.sampleSurfaceHeight(mc.level, wp.x, wp.z);
            wp.autoY = false;
            changed = true;
        }
        if (changed) WaypointManager.save();
    }

    /**
     * Respawning replaces the client player; the old one still holds where and in which dimension you died.
     * More reliable than watching health in the tick, which can miss a death with instant respawn.
     */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        Player old = event.getOldPlayer();
        if (!MapConfig.DEATH_WAYPOINT.get() || !old.isDeadOrDying()) return;
        WaypointManager.recordDeath(old.level().dimension(), old.blockPosition());
    }

    /** Potion effect icons sit in the top-right corner too: move them left of the minimap, along the top. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.POTION_ICONS.type()) return;
        int shift = MinimapOverlay.topRightWidth();
        effectsShifted = shift > 0;
        if (effectsShifted) {
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate(-shift, 0, 0);
        }
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.POTION_ICONS.type() || !effectsShifted) return;
        event.getGuiGraphics().pose().popPose();
        effectsShifted = false;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MapData.saveAndClearAll();
        RemotePlayers.reset();
    }
}
