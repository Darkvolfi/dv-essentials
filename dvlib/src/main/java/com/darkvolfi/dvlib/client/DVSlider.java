package com.darkvolfi.dvlib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/** A whole-number slider in DV's look: a thin track filled up to the knob, the value written beside it. */
public class DVSlider extends AbstractSliderButton {
    private final int min;
    private final int max;
    private final IntConsumer onChange;

    public DVSlider(int x, int y, int width, int min, int max, int value, IntConsumer onChange) {
        super(x, y, width, 12, Component.empty(), (value - min) / (double) Math.max(1, max - min));
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        updateMessage();
    }

    public int intValue() {
        return min + (int) Math.round(value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(String.valueOf(intValue())));
    }

    @Override
    protected void applyValue() {
        onChange.accept(intValue());
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int textW = font.width(String.valueOf(max)) + 4;
        int x = getX(), y = getY();
        int trackW = width - textW;
        int midY = y + height / 2;
        int knobX = x + (int) Math.round(value * (trackW - 4));
        boolean hot = isHoveredOrFocused();
        g.fill(x, midY - 2, x + trackW, midY + 2, 0xFF000000);
        g.fill(x + 1, midY - 1, x + trackW - 1, midY + 1, 0xFF3A3548);
        g.fill(x + 1, midY - 1, Mth.clamp(knobX + 2, x + 1, x + trackW - 1), midY + 1, DVUi.accent());
        g.fill(knobX - 1, y, knobX + 5, y + height, 0xFF000000);
        g.fill(knobX, y + 1, knobX + 4, y + height - 1, hot ? 0xFFFFFFFF : 0xFFE6E6F0);
        g.drawString(font, getMessage(), x + trackW + 4, y + 2, 0xFFE6E6F0);
    }
}
