package com.darkvolfi.dvlib.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A square button that shows a pixel icon; its name is the tooltip (and what narration reads).
 * As a toggle it is lit (accent rim, bright icon) while selected and dim otherwise.
 */
public class DVIconButton extends AbstractButton {
    private String[] icon;
    private final Runnable onPress;
    /** Null for a plain button. */
    private Boolean selected;

    public DVIconButton(int x, int y, int size, String[] icon, Component name, Runnable onPress) {
        super(x, y, size, size, name);
        this.icon = icon;
        this.onPress = onPress;
        setTooltip(Tooltip.create(name));
    }

    public DVIconButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public void setIcon(String[] icon) {
        this.icon = icon;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean lit = selected == null || selected;
        DVUi.panel(g, getX(), getY(), width, height, isHoveredOrFocused());
        if (selected != null && selected) {
            g.renderOutline(getX(), getY(), width, height, DVUi.accent());
        }
        int color = !active ? 0xFF4A4458 : lit ? 0xFFE6E6F0 : 0xFF6A6478;
        DVUi.icon(g, icon, getX() + width / 2, getY() + height / 2, Math.max(1, (width - 6) / 7), color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
