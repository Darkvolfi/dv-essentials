package com.darkvolfi.dvsouls.client;

import com.darkvolfi.dvlib.client.DVTheme;
import com.darkvolfi.dvsouls.SoulFuser;
import com.darkvolfi.dvsouls.SoulFurnace;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.recipebook.SmeltingRecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** The Soul Furnace's and Soul Fuser's screens. */
final class SoulScreens {
    private SoulScreens() {}

    /** The game's own furnace screen, recipe book and all. */
    static final class FurnaceScreen extends AbstractFurnaceScreen<SoulFurnace.Menu> {
        FurnaceScreen(SoulFurnace.Menu menu, Inventory inventory, Component title) {
            super(menu, new SmeltingRecipeBookComponent(), inventory, title, new ResourceLocation("textures/gui/container/furnace.png"));
        }
    }

    /**
     * A plain grey panel like the backpack's: the two souls with a plus between them, the fuel under them with a
     * soul flame showing how much is left, and an arrow filling towards the hybrid's jar.
     */
    static final class FuserScreen extends AbstractContainerScreen<SoulFuser.Menu> {
        private static final int PANEL = 0xFFC6C6C6;
        private static final int LIGHT = 0xFFFFFFFF;
        private static final int SHADOW = 0xFF555555;
        private static final int SLOT_BG = 0xFF8B8B8B;
        private static final int SLOT_DARK = 0xFF373737;
        private static final int SOUL = 0xFF4FD8E8;
        private static final int SOUL_LIGHT = 0xFFB8F4FF;

        FuserScreen(SoulFuser.Menu menu, Inventory inventory, Component title) {
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
            raised(g, x, y, imageWidth, imageHeight);
            for (Slot slot : menu.slots) sunken(g, x + slot.x - 1, y + slot.y - 1, 18, 18);

            // The plus between the two souls.
            g.fill(x + 48, y + 24, x + 56, y + 26, DVTheme.shade(SHADOW));
            g.fill(x + 51, y + 21, x + 53, y + 29, DVTheme.shade(SHADOW));

            // The fuel's flame, above its slot: a column of soul fire that sinks as the fuel burns.
            int fx = x + 46, fy = y + 37, fh = 13;
            sunken(g, fx, fy, 12, fh + 2);
            int lit = Math.round(menu.fuel() * fh);
            if (lit > 0) {
                g.fill(fx + 1, fy + 1 + fh - lit, fx + 11, fy + 1 + fh, DVTheme.shade(SOUL));
                g.fill(fx + 4, fy + 1 + fh - lit, fx + 8, fy + 1 + fh, DVTheme.shade(SOUL_LIGHT));
            }

            // The arrow: a shaft and a head, outlined, filling left to right with the fusion's progress.
            int ax = x + 86, ay = y + 35, filled = Math.round(menu.progress() * 24);
            for (int col = 0; col < 24; col++) {
                int half = col < 16 ? 2 : 8 - (col - 16);   // half the arrow's height at this column
                int colour = col < filled ? SOUL : SLOT_BG;
                g.fill(ax + col, ay + 8 - half, ax + col + 1, ay + 8 + half, DVTheme.shade(colour));
            }
        }

        private static void raised(GuiGraphics g, int x, int y, int w, int h) {
            g.fill(x, y, x + w, y + h, DVTheme.shade(PANEL));
            g.fill(x, y, x + w - 1, y + 1, DVTheme.shade(LIGHT));
            g.fill(x, y, x + 1, y + h - 1, DVTheme.shade(LIGHT));
            g.fill(x + 1, y + h - 1, x + w, y + h, DVTheme.shade(SHADOW));
            g.fill(x + w - 1, y + 1, x + w, y + h, DVTheme.shade(SHADOW));
        }

        private static void sunken(GuiGraphics g, int x, int y, int w, int h) {
            g.fill(x, y, x + w, y + h, DVTheme.shade(SLOT_BG));
            g.fill(x, y, x + w - 1, y + 1, DVTheme.shade(SLOT_DARK));
            g.fill(x, y, x + 1, y + h - 1, DVTheme.shade(SLOT_DARK));
            g.fill(x + 1, y + h - 1, x + w, y + h, DVTheme.shade(LIGHT));
            g.fill(x + w - 1, y + 1, x + w, y + h, DVTheme.shade(LIGHT));
        }
    }
}
