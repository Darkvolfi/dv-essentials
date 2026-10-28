package com.darkvolfi.dvlib.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** An on/off switch: a pixel track with a knob, green when on. Replaces ON/OFF text. */
public class DVSwitch extends AbstractButton {
    public static final int WIDTH = 24;
    public static final int HEIGHT = 12;
    private static final int ON = 0xFF4CAF50;
    private static final int OFF = 0xFF3A3548;

    private final Consumer<Boolean> onChange;
    private boolean on;

    public DVSwitch(int x, int y, Component name, boolean on, Consumer<Boolean> onChange) {
        super(x, y, WIDTH, HEIGHT, name);
        this.on = on;
        this.onChange = onChange;
    }

    @Override
    public void onPress() {
        on = !on;
        onChange.accept(on);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        g.fill(x, y, x + width, y + height, isHoveredOrFocused() ? DVUi.RIM_HOVER : 0xFF000000);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, on ? ON : OFF);
        int knob = on ? x + width - height + 1 : x + 1;
        g.fill(knob, y + 1, knob + height - 2, y + height - 1, 0xFFE6E6F0);
        g.fill(knob, y + height - 2, knob + height - 2, y + height - 1, 0xFF9A94AA); // knob shade
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, CommonComponents.optionStatus(getMessage(), on));
    }
}
