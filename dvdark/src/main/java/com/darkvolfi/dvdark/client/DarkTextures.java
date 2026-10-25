package com.darkvolfi.dvdark.client;

import com.darkvolfi.dvdark.DarkConfig;
import com.darkvolfi.dvlib.client.DVTheme;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Darkens GUI textures as they load. Only grey pixels change, so coloured details stay;
 * the mapping keeps the order of shades, so bevels and shadows still read the same way.
 */
public final class DarkTextures {
    private static final float MAX_SATURATION = 0.15f;

    /** HUD icons and artwork that should keep their own colours. */
    private static final List<String> SKIPPED = List.of(
            "textures/gui/icons.png",
            "textures/gui/title/",
            "textures/gui/presets/",
            "textures/gui/advancements/backgrounds/");

    private DarkTextures() {}

    public static boolean shouldDarken(ResourceLocation location) {
        if (!DarkConfig.enabled()) return false;
        String path = location.getPath();
        if (!path.startsWith("textures/gui/")) return false;
        for (String skipped : SKIPPED) {
            if (path.startsWith(skipped)) return false;
        }
        return true;
    }

    public static void darken(NativeImage image) {
        if (image.format() != NativeImage.Format.RGBA) return;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                if ((abgr >>> 24) == 0) continue;
                int r = abgr & 0xFF, g = abgr >> 8 & 0xFF, b = abgr >> 16 & 0xFF;
                int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
                if (max > 0 && (max - min) / (float) max > MAX_SATURATION) continue; // coloured: keep
                image.setPixelRGBA(x, y, (abgr & 0xFF000000)
                        | DVTheme.darkChannel(b) << 16 | DVTheme.darkChannel(g) << 8 | DVTheme.darkChannel(r));
            }
        }
    }
}
