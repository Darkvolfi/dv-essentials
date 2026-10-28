package com.darkvolfi.dvlib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A text button in DV's panel look, for the few places that need words (a choice, a key name). */
public class DVButton extends AbstractButton {
    private final Consumer<DVButton> onPress;

    public DVButton(int x, int y, int width, int height, Component message, Consumer<DVButton> onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    /** Same shape as vanilla's {@code Button.builder}, so screens switch to DV's look by changing the class name. */
    public static Builder builder(Component message, Consumer<DVButton> onPress) {
        return new Builder(message, onPress);
    }

    public static final class Builder {
        private final Component message;
        private final Consumer<DVButton> onPress;
        private int x, y, width = 150, height = 20;
        private Tooltip tooltip;

        private Builder(Component message, Consumer<DVButton> onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            return size(width, height);
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public DVButton build() {
            DVButton button = new DVButton(x, y, width, height, message, onPress);
            button.setTooltip(tooltip);
            return button;
        }
    }

    @Override
    public void onPress() {
        onPress.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DVUi.panel(g, getX(), getY(), width, height, isHoveredOrFocused());
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, active ? 0xFFE6E6F0 : 0xFF6A6478);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
