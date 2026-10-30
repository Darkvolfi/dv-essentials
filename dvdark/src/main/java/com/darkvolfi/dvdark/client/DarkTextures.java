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
    private static final String PANORAMA = "textures/gui/title/background/panorama_";

    /** HUD icons and artwork that should keep their own colours. */
    private static final List<String> SKIPPED = List.of(
            "textures/gui/icons.png",
            "textures/gui/title/",
            "textures/gui/presets/",
            "textures/gui/advancements/backgrounds/");

    private DarkTextures() {}

    /** The face (0-5) of the title screen's panorama this texture is, to be turned to night; -1 if it isn't one or that is off. */
    public static int nightFace(ResourceLocation location) {
        if (!DarkConfig.enabled() || !DarkConfig.NIGHT_PANORAMA.get() || !location.getNamespace().equals("minecraft")) return -1;
        String path = location.getPath();
        if (!path.startsWith(PANORAMA) || !path.endsWith(".png") || path.length() != PANORAMA.length() + 5) return -1;
        char face = path.charAt(PANORAMA.length());
        return face >= '0' && face <= '5' ? face - '0' : -1;
    }

    /** Turns a panorama face to night ({@link NightSky}). */
    public static void night(NativeImage image, int face) {
        if (image.format() != NativeImage.Format.RGBA) return;
        int size = image.getWidth();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < size; x++) {
                int abgr = image.getPixelRGBA(x, y);
                int rgb = NightSky.night(abgr & 0xFF, abgr >> 8 & 0xFF, abgr >> 16 & 0xFF, x, y, size, face);
                image.setPixelRGBA(x, y, (abgr & 0xFF000000) | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | rgb >> 16 & 0xFF);
            }
        }
    }

    public static boolean shouldDarken(ResourceLocation location) {
        if (!DarkConfig.enabled()) return false;
        String path = location.getPath();
        if (!path.startsWith("textures/gui/")) return false;
        for (String skipped : SKIPPED) {
            if (path.startsWith(skipped)) return false;
        }
        return !isException(location);
    }

    /** Atlases of GUI sprites, such as JEI's {@code jei:textures/atlas/gui.png}. */
    public static boolean shouldDarkenAtlas(ResourceLocation atlas) {
        return DarkConfig.enabled() && atlas.getPath().equals("textures/atlas/gui.png") && !isException(atlas);
    }

    private static boolean isException(ResourceLocation location) {
        String full = location.toString();
        for (String exception : DarkConfig.EXCEPTIONS.get()) {
            if (exception.equals(location.getNamespace()) || full.startsWith(exception)) return true;
        }
        return false;
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
                int rgb = DVTheme.darkRgb(r, g, b);
                image.setPixelRGBA(x, y, (abgr & 0xFF000000) | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | rgb >> 16 & 0xFF);
            }
        }
    }
}
