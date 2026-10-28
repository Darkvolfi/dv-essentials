package com.darkvolfi.dvdark.client;

import com.darkvolfi.dvdark.DVDark;
import com.darkvolfi.dvdark.DarkConfig;
import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvlib.client.DVTheme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = DVDark.MOD_ID, value = Dist.CLIENT)
public final class DarkClient {
    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.dvdark.toggle",
            KeyConflictContext.UNIVERSAL, InputConstants.UNKNOWN, DVLib.KEY_CATEGORY);

    /** Container labels are drawn in this dark grey, which is unreadable on a dark panel. */
    private static final int LABEL_COLOR = 0x404040;
    private static final int LIGHT_LABEL_COLOR = 0xE0E0E0;
    private static final int VANILLA_TOOLTIP_BACKGROUND = 0xF0100010;

    /** The settings the loaded textures were darkened with. */
    private static String appliedState;

    private DarkClient() {}

    public static int adjustTextColor(int color) {
        if ((color & 0xFFFFFF) != LABEL_COLOR || Minecraft.getInstance().screen == null || !DarkConfig.enabled()) {
            return color;
        }
        return (color & 0xFF000000) | LIGHT_LABEL_COLOR;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (TOGGLE_KEY.consumeClick()) {
            DarkConfig.ENABLED.set(!DarkConfig.ENABLED.get());
            applyIfChanged();
        }
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        if (!DarkConfig.enabled() || !DarkConfig.DARK_TOOLTIPS.get()) return;
        // Only vanilla's purple tooltip; items with their own tooltip colours keep them.
        if (event.getOriginalBackgroundStart() != VANILLA_TOOLTIP_BACKGROUND) return;
        event.setBackground(DVTheme.shade(0xF0606060));
        int accent = DarkConfig.accent();
        int border = accent < 0 ? 0xFF505050 : 0xFF000000 | accent;
        event.setBorderStart(border);
        event.setBorderEnd((border & 0xFEFEFEFE) >>> 1 | 0xFF000000);
    }

    /** After a setting changes: tell DV Lib's theme and reload textures, which are recoloured while loading. */
    public static void applyIfChanged() {
        String state = state();
        if (state.equals(appliedState)) return;
        boolean first = appliedState == null;
        appliedState = state;
        updateTheme();
        if (!first) Minecraft.getInstance().reloadResourcePacks();
    }

    private static void updateTheme() {
        DVTheme.dark = DarkConfig.enabled();
        DVTheme.scale = 1 - DarkConfig.DARKNESS.get() / 100f;
        DVTheme.tint = DarkConfig.accent();
    }

    /** Everything that changes how textures load; the tooltip setting doesn't need a reload. */
    private static String state() {
        if (!DarkConfig.enabled()) return "off";
        return DarkConfig.DARKNESS.get() + "/" + DarkConfig.accent() + "/" + DarkConfig.EXCEPTIONS.get();
    }

    @Mod.EventBusSubscriber(modid = DVDark.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            applyIfChanged();
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_KEY);
        }
    }
}
