package com.darkvolfi.dvlib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Any colour: the saturation/brightness square and hue bar, a hex box for typing an exact colour,
 * and the 16 dye colours as quick picks. Every change applies at once; click outside or Esc to close.
 */
public final class DVColorPopup extends DVPopup {
    private static final int PAD = 5;
    private static final int PICKER_W = 128;
    private static final int PICKER_H = 80;
    private static final int SWATCH = 8;

    private int color;
    private final IntConsumer onChange;
    private DVColorPicker picker;
    private EditBox hex;

    private DVColorPopup(AbstractWidget anchor, int color, IntConsumer onChange) {
        super(anchor);
        this.color = color & 0xFFFFFF;
        this.onChange = onChange;
    }

    public static void open(AbstractWidget anchor, int color, IntConsumer onChange) {
        Minecraft.getInstance().setScreen(new DVColorPopup(anchor, color, onChange));
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** "#RRGGBB" or "RRGGBB" → rgb, or -1 when it isn't a colour. */
    public static int parseHex(String text) {
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (!s.matches("[0-9A-Fa-f]{6}")) return -1;
        return Integer.parseInt(s, 16);
    }

    @Override
    protected int[] panelSize() {
        return new int[]{PICKER_W + 2 * PAD, PAD + PICKER_H + 6 + 16 + 5 + 2 * SWATCH + 2 + PAD};
    }

    @Override
    protected void initPanel() {
        int x = panelX + PAD;
        int y = panelY + PAD;
        picker = addRenderableWidget(new DVColorPicker(x, y, PICKER_W, PICKER_H, color, c -> set(c, true, false)));
        hex = new EditBox(font, x + 20, y + PICKER_H + 6, PICKER_W - 20, 16, Component.literal("#"));
        hex.setMaxLength(7);
        hex.setValue(hex(color));
        hex.setResponder(text -> {
            int rgb = parseHex(text);
            hex.setTextColor(rgb < 0 ? 0xFF5555 : 0xE0E0E0);
            if (rgb >= 0 && rgb != color) set(rgb, false, true);
        });
        addRenderableWidget(hex);
    }

    private void set(int rgb, boolean updateHex, boolean updatePicker) {
        color = rgb & 0xFFFFFF;
        if (updateHex) hex.setValue(hex(color));
        if (updatePicker) picker.setColor(color);
        onChange.accept(color);
    }

    private int swatchTop() {
        return panelY + PAD + PICKER_H + 6 + 16 + 5;
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX + PAD;
        int y = panelY + PAD + PICKER_H + 6;
        g.fill(x, y, x + 16, y + 16, 0xFF000000);
        g.fill(x + 1, y + 1, x + 15, y + 15, 0xFF000000 | color);
        int top = swatchTop();
        for (DyeColor dye : DyeColor.values()) {
            int sx = x + (dye.getId() % 8) * (SWATCH * 2);
            int sy = top + (dye.getId() / 8) * (SWATCH + 2);
            g.fill(sx, sy, sx + SWATCH * 2 - 2, sy + SWATCH, 0xFF000000);
            g.fill(sx + 1, sy + 1, sx + SWATCH * 2 - 3, sy + SWATCH - 1, 0xFF000000 | dye.getTextColor());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = panelX + PAD;
        int top = swatchTop();
        if (mouseX >= x && mouseX < x + 8 * SWATCH * 2 && mouseY >= top && mouseY < top + 2 * (SWATCH + 2)) {
            int id = (int) ((mouseX - x) / (SWATCH * 2)) + 8 * (int) ((mouseY - top) / (SWATCH + 2));
            set(DyeColor.byId(id).getTextColor(), true, true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
