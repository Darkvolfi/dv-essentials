package com.darkvolfi.dvteam.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.function.IntConsumer;

/**
 * Free colour choice: a saturation (→) / brightness (↓) square for the current hue, and a hue bar under it.
 * Click or drag either one.
 */
public class ColorPicker extends AbstractWidget {
    private static final int HUE_HEIGHT = 8;
    private static final int GAP = 3;

    private float hue;
    private float saturation;
    private float value;
    private boolean draggingHue;
    private final IntConsumer onChange;

    public ColorPicker(int x, int y, int width, int height, int rgb, IntConsumer onChange) {
        super(x, y, width, height, Component.empty());
        this.onChange = onChange;
        setColor(rgb);
    }

    public void setColor(int rgb) {
        float[] hsv = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    public int color() {
        return Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
    }

    private int squareHeight() {
        return height - HUE_HEIGHT - GAP;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int sh = squareHeight();
        g.fill(x - 1, y - 1, x + width + 1, y + sh + 1, 0xFF000000);
        for (int i = 0; i < width; i++) {
            int top = 0xFF000000 | Mth.hsvToRgb(hue, i / (float) (width - 1), 1.0f);
            g.fillGradient(x + i, y, x + i + 1, y + sh, top, 0xFF000000);
        }
        int hy = y + sh + GAP;
        g.fill(x - 1, hy - 1, x + width + 1, hy + HUE_HEIGHT + 1, 0xFF000000);
        for (int i = 0; i < width; i++) {
            g.fill(x + i, hy, x + i + 1, hy + HUE_HEIGHT, 0xFF000000 | Mth.hsvToRgb(i / (float) width, 1.0f, 1.0f));
        }
        // Markers
        int mx = x + Math.round(saturation * (width - 1));
        int my = y + Math.round((1 - value) * (sh - 1));
        g.renderOutline(mx - 2, my - 2, 5, 5, value > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
        int hx = x + Math.round(hue * (width - 1));
        g.renderOutline(hx - 1, hy - 1, 3, HUE_HEIGHT + 2, 0xFFFFFFFF);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        draggingHue = mouseY >= getY() + squareHeight();
        pick(mouseX, mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        pick(mouseX, mouseY);
    }

    private void pick(double mouseX, double mouseY) {
        float fx = Mth.clamp((float) (mouseX - getX()) / (width - 1), 0.0f, 1.0f);
        if (draggingHue) {
            hue = Math.min(fx, 0.999f);
        } else {
            saturation = fx;
            value = 1 - Mth.clamp((float) (mouseY - getY()) / (squareHeight() - 1), 0.0f, 1.0f);
        }
        onChange.accept(color());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
