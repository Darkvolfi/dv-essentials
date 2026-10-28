package com.darkvolfi.dvlens.client;

import com.darkvolfi.dvlens.DVLens;
import com.darkvolfi.dvlens.LensConfig;
import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvlib.client.MobFace;
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

import java.util.Locale;

/** Info box at the top centre of the screen, drawn in the vanilla tooltip style. */
@Mod.EventBusSubscriber(modid = DVLens.MOD_ID, value = Dist.CLIENT)
public final class LensOverlay {
    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.dvlens.toggle",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, DVLib.KEY_CATEGORY);

    private static final int TOP = 6;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int ITEMS_PER_ROW = 9;
    private static final int ITEM_ROWS = 3;
    private static final int ITEM_STEP = 18;

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
        boolean hasIcon = info.face() != null || !info.icon().isEmpty();
        boolean hasTool = !info.tool().isEmpty();
        int textX = hasIcon ? ICON_SIZE + ICON_GAP : 0;
        int itemCount = Math.min(info.items().size(), ITEMS_PER_ROW * ITEM_ROWS);
        int itemRows = (itemCount + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        int itemsWidth = Math.min(itemCount, ITEMS_PER_ROW) * ITEM_STEP;
        int headerHeight = Math.max(hasIcon || hasTool ? ICON_SIZE : 0, info.lines().size() * LINE_HEIGHT - 2);
        int boxWidth = Math.max(textX + textWidth + (hasTool ? ICON_GAP + ICON_SIZE : 0), itemsWidth);
        int boxHeight = headerHeight + (itemRows > 0 ? 2 + itemRows * ITEM_STEP : 0);

        int x = (width - boxWidth) / 2;
        int y = TOP;
        int iconY = y + (headerHeight - ICON_SIZE) / 2;
        TooltipRenderUtil.renderTooltipBackground(g, x, y, boxWidth, boxHeight, 0);
        if (info.face() != null) {
            MobFace.draw(g, info.face(), x + ICON_SIZE / 2, iconY + ICON_SIZE / 2, ICON_SIZE);
        } else if (hasIcon) {
            g.renderItem(info.icon(), x, iconY);
        }
        for (int i = 0; i < info.lines().size(); i++) {
            g.drawString(font, info.lines().get(i), x + textX, y + i * LINE_HEIGHT, 0xFFFFFF);
        }
        if (hasTool) drawTool(g, font, info, x + boxWidth - ICON_SIZE, iconY);
        for (int i = 0; i < itemCount; i++) {
            int ix = x + (i % ITEMS_PER_ROW) * ITEM_STEP;
            int iy = y + headerHeight + 2 + (i / ITEMS_PER_ROW) * ITEM_STEP;
            g.renderItem(info.items().get(i), ix, iy);
            g.renderItemDecorations(font, info.items().get(i), ix, iy, count(info.items().get(i).getCount()));
        }
        if (LensConfig.SHOW_BREAK_PROGRESS.get() && mc.gameMode != null && mc.gameMode.isDestroying()) {
            drawBreakProgress(g, mc.gameMode.getDestroyStage(), x, y + boxHeight + 3, boxWidth);
        }
    }

    /** Thin bar under the box; the destroy stage goes 0..9 as the block cracks. */
    private static void drawBreakProgress(GuiGraphics g, int stage, int x, int y, int width) {
        if (stage < 0) return;
        int filled = width * (stage + 1) / 10;
        g.fill(x, y, x + width, y + 2, 0x80000000);
        g.fill(x, y, x + filled, y + 2, 0xFFFFFFFF);
    }

    /** Stack counts over 999 don't fit on an icon: 1.2k, 64k. */
    private static String count(int count) {
        if (count <= 1) return null;
        if (count < 1000) return Integer.toString(count);
        return count < 10000 ? String.format(Locale.ROOT, "%.1fk", count / 1000f) : count / 1000 + "k";
    }

    /** The required tool with a green tick (held item can harvest the block) or a red cross in its corner. */
    private static void drawTool(GuiGraphics g, Font font, LensInfo info, int x, int y) {
        g.renderItem(info.tool(), x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200); // above the item, like the stack count
        String mark = info.toolOk() ? "✔" : "✘";
        g.drawString(font, mark, x + ICON_SIZE - font.width(mark) + 1, y + ICON_SIZE - 7, info.toolOk() ? 0x55FF55 : 0xFF5555);
        g.pose().popPose();
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
