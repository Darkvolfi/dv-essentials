package com.darkvolfi.dvlib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Shows a colour and its hex code; pressing it opens {@link DVColorPopup} to pick any colour. */
public class DVColorButton extends AbstractButton {
    private int color;
    private final IntConsumer onChange;

    public DVColorButton(int x, int y, int width, int height, int color, IntConsumer onChange) {
        super(x, y, width, height, Component.empty());
        this.color = color & 0xFFFFFF;
        this.onChange = onChange;
    }

    public int color() {
        return color;
    }

    @Override
    public void onPress() {
        DVColorPopup.open(this, color, c -> {
            color = c;
            onChange.accept(c);
        });
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DVUi.panel(g, getX(), getY(), width, height, isHoveredOrFocused());
        int s = height - 6;
        g.fill(getX() + 3, getY() + 3, getX() + 3 + s, getY() + 3 + s, 0xFF000000);
        g.fill(getX() + 4, getY() + 4, getX() + 2 + s, getY() + 2 + s, 0xFF000000 | color);
        var font = Minecraft.getInstance().font;
        int textX = getX() + 3 + s + (width - 3 - s - font.width(DVColorPopup.hex(color))) / 2;
        g.drawString(font, DVColorPopup.hex(color), textX, getY() + (height - 8) / 2, 0xFFE6E6F0);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal(DVColorPopup.hex(color)));
    }
}
