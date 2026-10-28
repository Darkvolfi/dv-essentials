package com.darkvolfi.dvlib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A small panel opened over another screen (a choice menu, a colour picker). The screen below keeps drawing and
 * keeps its state: closing hands it back without running its init again, so typed text is not lost.
 * Clicking outside the panel or pressing Esc closes it.
 */
public abstract class DVPopup extends Screen {
    protected final Screen parent;
    /** The control that opened the popup; the panel is placed next to it. */
    private final int anchorX, anchorY, anchorH;
    protected int panelX, panelY, panelW, panelH;

    protected DVPopup(AbstractWidget anchor) {
        super(Component.empty());
        this.parent = Minecraft.getInstance().screen;
        this.anchorX = anchor.getX();
        this.anchorY = anchor.getY();
        this.anchorH = anchor.getHeight();
    }

    /** The panel's {width, height}; called from init, so it may depend on the screen size. */
    protected abstract int[] panelSize();

    @Override
    protected void init() {
        int[] size = panelSize();
        panelW = size[0];
        panelH = size[1];
        panelX = Math.max(4, Math.min(anchorX, width - panelW - 4));
        int below = anchorY + anchorH + 2;
        panelY = below + panelH <= height - 4 ? below : Math.max(4, anchorY - panelH - 2);
        initPanel();
    }

    /** Adds the panel's widgets once {@link #panelX}/{@link #panelY} are known. */
    protected void initPanel() {}

    /** Draws the panel's own contents; widgets are drawn afterwards. */
    protected abstract void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (parent != null) parent.render(g, -1, -1, partialTick);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400); // above the items the screen below may draw
        g.fill(0, 0, width, height, 0x40000000);
        DVUi.panel(g, panelX, panelY, panelW, panelH, false);
        renderPanel(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }

    protected boolean insidePanel(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX < panelX + panelW && mouseY >= panelY && mouseY < panelY + panelH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!insidePanel(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        // Straight back to the screen below, without re-running its init.
        minecraft.screen = parent;
    }

    @Override
    public void resize(Minecraft mc, int width, int height) {
        if (parent != null) parent.resize(mc, width, height);
        super.resize(mc, width, height);
    }

    @Override
    public void tick() {
        if (parent != null) parent.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return parent == null || parent.isPauseScreen();
    }
}
