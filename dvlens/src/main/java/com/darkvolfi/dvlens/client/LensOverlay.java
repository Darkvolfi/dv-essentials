package com.darkvolfi.dvlens.client;

import com.darkvolfi.dvlens.DVLens;
import com.darkvolfi.dvlens.LensConfig;
import com.darkvolfi.dvlib.DVLib;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Info box at the top centre of the screen, drawn in the vanilla tooltip style. */
@Mod.EventBusSubscriber(modid = DVLens.MOD_ID, value = Dist.CLIENT)
public final class LensOverlay {
    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.dvlens.toggle",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, DVLib.KEY_CATEGORY);

    private static final int TOP = 6;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int LINE_HEIGHT = 10;

    private LensOverlay() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (TOGGLE_KEY.consumeClick()) {
            LensConfig.ENABLED.set(!LensConfig.ENABLED.get());
        }
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        if (!LensConfig.ENABLED.get() || mc.options.hideGui || mc.options.renderDebug || mc.screen != null) return;
        LensInfo info = LensInfo.lookAt(mc);
        if (info == null || info.lines().isEmpty()) return;

        Font font = mc.font;
        int textWidth = 0;
        for (Component line : info.lines()) textWidth = Math.max(textWidth, font.width(line));
        boolean hasIcon = !info.icon().isEmpty();
        int textX = hasIcon ? ICON_SIZE + ICON_GAP : 0;
        int boxWidth = textX + textWidth;
        int boxHeight = Math.max(hasIcon ? ICON_SIZE : 0, info.lines().size() * LINE_HEIGHT - 2);

        int x = (width - boxWidth) / 2;
        int y = TOP;
        TooltipRenderUtil.renderTooltipBackground(g, x, y, boxWidth, boxHeight, 0);
        if (hasIcon) g.renderItem(info.icon(), x, y + (boxHeight - ICON_SIZE) / 2);
        for (int i = 0; i < info.lines().size(); i++) {
            g.drawString(font, info.lines().get(i), x + textX, y + i * LINE_HEIGHT, 0xFFFFFF);
        }
    }

    @Mod.EventBusSubscriber(modid = DVLens.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_KEY);
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("lens", LensOverlay::render);
        }
    }
}
