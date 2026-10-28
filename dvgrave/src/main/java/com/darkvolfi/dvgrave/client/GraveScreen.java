package com.darkvolfi.dvgrave.client;

import com.darkvolfi.dvgrave.GraveMenu;
import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVIconButton;
import com.darkvolfi.dvlib.client.DVUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Two DV panels side by side: the grave, laid out like the inventory it came from, and the player's own
 * inventory with "Take all" under it.
 */
public class GraveScreen extends AbstractContainerScreen<GraveMenu> {
    private static final String[] LEFT = {"....#..", "...##..", "..###..", ".####..", "..###..", "...##..", "....#.."};
    private static final String[] RIGHT = {"..#....", "..##...", "..###..", "..####.", "..###..", "..##...", "..#...."};
    /** Below the 4×4 extra grid. */
    private static final int PAGE_Y = GraveMenu.TOP + 4 * 18 + 2;

    public GraveScreen(GraveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = GraveMenu.PLAYER_X + 176;
        imageHeight = GraveMenu.GRAVE_HEIGHT;
        inventoryLabelX = GraveMenu.PLAYER_X + 8;
        inventoryLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        DVButton takeAll = addRenderableWidget(new DVButton(leftPos + GraveMenu.PLAYER_X, topPos + GraveMenu.PLAYER_HEIGHT + 4, 176, 20,
                Component.translatable("dvgrave.take_all"), b -> press(GraveMenu.TAKE_ALL)));
        takeAll.setTooltip(Tooltip.create(Component.translatable("dvgrave.take_all.tooltip")));
        if (menu.pages() > 1) {
            int x = leftPos + GraveMenu.EXTRA_X;
            addRenderableWidget(new DVIconButton(x, topPos + PAGE_Y, 12, LEFT,
                    Component.translatable("dvgrave.page.previous"), () -> press(GraveMenu.PREVIOUS)));
            addRenderableWidget(new DVIconButton(x + GraveMenu.EXTRA_COLUMNS * 18 - 12, topPos + PAGE_Y, 12, RIGHT,
                    Component.translatable("dvgrave.page.next"), () -> press(GraveMenu.NEXT)));
        }
    }

    private void press(int button) {
        // Runs on the server; the page number comes back through the menu's data slot.
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        DVUi.panel(g, leftPos, topPos, GraveMenu.GRAVE_WIDTH, GraveMenu.GRAVE_HEIGHT, false);
        DVUi.panel(g, leftPos + GraveMenu.PLAYER_X, topPos, 176, GraveMenu.PLAYER_HEIGHT, false);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, 0xFF0C0B10);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF26232F);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFE6E6F0, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFA0A0A0, false);
        // Where vanilla shows the player model: the experience kept in the grave.
        int cx = 26 + 25;
        if (menu.xp() > 0) {
            g.drawCenteredString(font, String.valueOf(menu.xp()), cx, GraveMenu.TOP + 22, 0xFF80FF20);
            g.drawCenteredString(font, "XP", cx, GraveMenu.TOP + 32, 0xFF80FF20);
        }
        if (menu.pages() > 1) {
            String text = (menu.page() + 1) + "/" + menu.pages();
            g.drawCenteredString(font, text, GraveMenu.EXTRA_X + GraveMenu.EXTRA_COLUMNS * 9, PAGE_Y + 2, 0xFFE6E6F0);
        }
    }
}
