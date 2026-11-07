package com.darkvolfi.dvlens.client;

import com.darkvolfi.dvlens.DVLens;
import com.darkvolfi.dvlens.LensConfig;
import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvlib.client.MobFace;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.Locale;

/** Info box at the top centre of the screen, in DV's panel look; values like health are bars. */
@Mod.EventBusSubscriber(modid = DVLens.MOD_ID, value = Dist.CLIENT)
public final class LensOverlay {
    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.dvlens.toggle",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, DVLib.KEY_CATEGORY);

    private static final int TOP = 8;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int ITEMS_PER_ROW = 9;
    private static final int ITEM_ROWS = 3;
    private static final int ITEM_STEP = 18;
    private static final int BAR_HEIGHT = 10;
    private static final int MIN_BAR_WIDTH = 100;
    /** Space between the panel rim and its contents. */
    private static final int PAD = 4;

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
        int barsWidth = info.bars().isEmpty() ? 0 : MIN_BAR_WIDTH;
        for (LensInfo.Bar bar : info.bars()) barsWidth = Math.max(barsWidth, font.width(bar.text()) + 8);
        boolean hasIcon = info.face() != null || !info.icon().isEmpty();
        boolean hasTool = !info.tool().isEmpty();
        int textX = hasIcon ? ICON_SIZE + ICON_GAP : 0;
        int itemCount = Math.min(info.items().size(), ITEMS_PER_ROW * ITEM_ROWS);
        int itemRows = (itemCount + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        int itemsWidth = Math.min(itemCount, ITEMS_PER_ROW) * ITEM_STEP;
        int headerHeight = Math.max(hasIcon || hasTool ? ICON_SIZE : 0, info.lines().size() * LINE_HEIGHT - 2);
        int barsHeight = info.bars().size() * (BAR_HEIGHT + 2);
        int boxWidth = Math.max(Math.max(textX + textWidth + (hasTool ? ICON_GAP + ICON_SIZE : 0), itemsWidth), barsWidth);
        int boxHeight = headerHeight + (barsHeight > 0 ? 3 + barsHeight - 2 : 0) + (itemRows > 0 ? 2 + itemRows * ITEM_STEP : 0);

        int x = (width - boxWidth) / 2;
        int y = TOP;
        int iconY = y + (headerHeight - ICON_SIZE) / 2;
        DVUi.panel(g, x - PAD, y - PAD, boxWidth + 2 * PAD, boxHeight + 2 * PAD, false);
        if (info.face() != null) {
            MobFace.draw(g, info.face(), x + ICON_SIZE / 2, iconY + ICON_SIZE / 2, ICON_SIZE);
        } else if (hasIcon) {
            g.renderItem(info.icon(), x, iconY);
        }
        for (int i = 0; i < info.lines().size(); i++) {
            g.drawString(font, info.lines().get(i), x + textX, y + i * LINE_HEIGHT, 0xFFFFFF);
        }
        if (hasTool) drawTool(g, font, info, x + boxWidth - ICON_SIZE, iconY);
        int barY = y + headerHeight + 3;
        for (LensInfo.Bar bar : info.bars()) {
            drawBar(g, font, bar, x, barY, boxWidth);
            barY += BAR_HEIGHT + 2;
        }
        int itemsTop = y + headerHeight + (barsHeight > 0 ? 3 + barsHeight - 2 : 0);
        for (int i = 0; i < itemCount; i++) {
            int ix = x + (i % ITEMS_PER_ROW) * ITEM_STEP;
            int iy = itemsTop + 2 + (i / ITEMS_PER_ROW) * ITEM_STEP;
            g.renderItem(info.items().get(i), ix, iy);
            g.renderItemDecorations(font, info.items().get(i), ix, iy, count(info.items().get(i).getCount()));
        }
        if (LensConfig.SHOW_BREAK_PROGRESS.get() && mc.gameMode != null && mc.gameMode.isDestroying()) {
            drawBreakProgress(g, breakProgress(mc, partialTick), x - PAD, y + boxHeight + PAD + 2, boxWidth + 2 * PAD);
        }
    }

    /** A value bar: dark track, coloured fill up to the value, its text centred on top. */
    private static void drawBar(GuiGraphics g, Font font, LensInfo.Bar bar, int x, int y, int width) {
        g.fill(x, y, x + width, y + BAR_HEIGHT, 0xFF000000);
        g.fill(x + 1, y + 1, x + width - 1, y + BAR_HEIGHT - 1, 0xFF2A2633);
        int filled = Math.round((width - 2) * bar.fraction());
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + BAR_HEIGHT - 1, bar.color());
            g.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0x40FFFFFF); // top highlight
        }
        g.drawCenteredString(font, bar.text(), x + width / 2, y + 1, 0xFFFFFFFF);
    }

    /** Thin bar under the box, filling as the block breaks. */
    private static void drawBreakProgress(GuiGraphics g, float progress, int x, int y, int width) {
        if (progress <= 0) return;
        int filled = Math.round(width * Math.min(1f, progress));
        g.fill(x, y, x + width, y + 2, 0x80000000);
        g.fill(x, y, x + filled, y + 2, 0xFFFFFFFF);
    }

    private static Field destroyProgressField;
    private static Field destroyPosField;
    private static boolean progressBroken;

    /**
     * How far the block is broken, 0..1, moving smoothly between ticks: the game's own progress plus this tick's
     * share of the next step. The game only exposes the crack stage (10 steps), so the value is read from the field.
     */
    private static float breakProgress(Minecraft mc, float partialTick) {
        MultiPlayerGameMode mode = mc.gameMode;
        if (!progressBroken) {
            try {
                if (destroyProgressField == null) {
                    destroyProgressField = ObfuscationReflectionHelper.findField(MultiPlayerGameMode.class, "f_105193_");
                    destroyPosField = ObfuscationReflectionHelper.findField(MultiPlayerGameMode.class, "f_105191_");
                }
                float progress = destroyProgressField.getFloat(mode);
                BlockPos pos = (BlockPos) destroyPosField.get(mode);
                float perTick = mc.level == null || mc.player == null ? 0
                        : mc.level.getBlockState(pos).getDestroyProgress(mc.player, mc.level, pos);
                return Math.min(1f, progress + perTick * partialTick);
            } catch (ReflectiveOperationException | RuntimeException e) {
                progressBroken = true;
            }
        }
        int stage = mode.getDestroyStage();
        return stage < 0 ? 0 : (stage + 1) / 10f;
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
