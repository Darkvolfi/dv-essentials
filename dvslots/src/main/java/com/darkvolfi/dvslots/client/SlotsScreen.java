package com.darkvolfi.dvslots.client;

import com.darkvolfi.dvlib.client.DVTheme;
import com.darkvolfi.dvslots.menu.SlotsMenu;
import com.darkvolfi.dvslots.slot.SlotType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Drawn with plain fills in the vanilla container colours, so no texture is shipped. */
public class SlotsScreen extends AbstractContainerScreen<SlotsMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;

    public SlotsScreen(SlotsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        int panel = DVTheme.shade(PANEL), light = DVTheme.shade(LIGHT), shadow = DVTheme.shade(SHADOW);
        int slotBg = DVTheme.shade(SLOT_BG), slotDark = DVTheme.shade(SLOT_DARK);
        g.fill(x, y, x + imageWidth, y + imageHeight, panel);
        g.fill(x, y, x + imageWidth - 1, y + 1, light);
        g.fill(x, y, x + 1, y + imageHeight - 1, light);
        g.fill(x + 1, y + imageHeight - 1, x + imageWidth, y + imageHeight, shadow);
        g.fill(x + imageWidth - 1, y + 1, x + imageWidth, y + imageHeight, shadow);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, slotBg);
            g.fill(sx, sy, sx + 17, sy + 1, slotDark);
            g.fill(sx, sy, sx + 1, sy + 17, slotDark);
            g.fill(sx + 1, sy + 17, sx + 18, sy + 18, light);
            g.fill(sx + 17, sy + 1, sx + 18, sy + 18, light);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        for (SlotType type : SlotType.values()) {
            g.drawString(font, Component.translatable("dvslots.slot." + type.id()),
                    SlotsMenu.slotX(type) + 20, SlotsMenu.slotY(type) + 5, 0x404040, false);
        }
    }
}
