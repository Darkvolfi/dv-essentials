package com.darkvolfi.dvmap.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Extension hook allowing other DV mods (such as DV Team) to draw chunk overlays
 * and interact with the full-screen world map and minimap.
 */
public interface MapOverlayHook {
    List<MapOverlayHook> HOOKS = new ArrayList<>();

    static void register(MapOverlayHook hook) {
        HOOKS.add(hook);
    }

    /**
     * Renders chunk overlays (such as claimed chunks) inside the map viewport.
     * For each visible chunk (cx, cz), screen coordinates of the chunk's top-left corner are:
     * {@code sx = centerScreenX + (cx * 16 - centerWorldX) * scale}, and size is {@code 16 * scale}.
     */
    default void renderChunkOverlay(GuiGraphics g, ResourceKey<Level> dimension,
                                    int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                                    float centerScreenX, float centerScreenY,
                                    double centerWorldX, double centerWorldZ,
                                    float scale, boolean isMinimap) {}

    /** Additional status text shown in the bottom bar when hovering a chunk on the world map. */
    default Component getChunkHoverText(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return null;
    }

    /** Adds custom toolbar buttons to the full-screen world map. */
    default void onWorldMapInit(WorldMapScreen screen, Consumer<AbstractWidget> addWidget) {}

    /** Handles mouse click on the world map canvas. Returns true if consumed. */
    default boolean onWorldMapClick(WorldMapScreen screen, ResourceKey<Level> dimension,
                                    int chunkX, int chunkZ, int blockX, int blockZ, int button) {
        return false;
    }

    /** Handles mouse drag across chunks on the world map canvas. Returns true if consumed. */
    default boolean onWorldMapDrag(WorldMapScreen screen, ResourceKey<Level> dimension,
                                   int chunkX, int chunkZ, int button) {
        return false;
    }

    /** Handles mouse release on the world map canvas. Returns true if consumed. */
    default boolean onWorldMapRelease(WorldMapScreen screen, int button) {
        return false;
    }

    /** Renders extra HUD text or indicators on the world map screen. */
    default void renderWorldMapExtra(GuiGraphics g, WorldMapScreen screen, int mouseX, int mouseY) {}
}
