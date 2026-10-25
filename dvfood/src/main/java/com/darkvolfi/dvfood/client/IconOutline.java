package com.darkvolfi.dvfood.client;

import com.darkvolfi.dvfood.DVFood;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The meat icons have no outline of their own; the dark border comes from the empty
 * background icon drawn under them. This finds the background pixels that touch the meat,
 * i.e. the ring around it, so it can be recoloured. Read from the current icons.png at
 * runtime: no vanilla art is shipped, and resource packs are respected.
 */
@Mod.EventBusSubscriber(modid = DVFood.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class IconOutline {
    private static final Map<Long, List<int[]>> CACHE = new HashMap<>();

    private IconOutline() {}

    public static List<int[]> of(int backgroundU, int iconU, int v) {
        long key = ((long) backgroundU << 40) | ((long) iconU << 20) | v;
        return CACHE.computeIfAbsent(key, k -> load(backgroundU, iconU, v));
    }

    private static List<int[]> load(int backgroundU, int iconU, int v) {
        List<int[]> points = new ArrayList<>();
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FoodHud.ICONS);
        if (resource.isEmpty()) return points;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            int scale = Math.max(1, image.getWidth() / 256); // high-res resource packs
            boolean[][] background = opaqueMask(image, scale, backgroundU, v);
            boolean[][] icon = opaqueMask(image, scale, iconU, v);
            for (int y = 0; y < 9; y++) {
                for (int x = 0; x < 9; x++) {
                    if (background[y][x] && !icon[y][x] && touches(icon, x, y)) points.add(new int[]{x, y});
                }
            }
        } catch (Exception e) {
            // Leave the outline empty; the bar still works without it.
        }
        return points;
    }

    private static boolean[][] opaqueMask(NativeImage image, int scale, int u, int v) {
        boolean[][] mask = new boolean[9][9];
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                int abgr = image.getPixelRGBA((u + x) * scale + scale / 2, (v + y) * scale + scale / 2);
                mask[y][x] = (abgr >>> 24) > 128;
            }
        }
        return mask;
    }

    private static boolean touches(boolean[][] mask, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < 9 && ny < 9 && mask[ny][nx]) return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> CACHE.clear());
    }
}
