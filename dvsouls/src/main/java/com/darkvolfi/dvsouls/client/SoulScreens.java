package com.darkvolfi.dvsouls.client;

import com.darkvolfi.dvlib.client.DVTheme;
import com.darkvolfi.dvsouls.SoulDistiller;
import com.darkvolfi.dvsouls.SoulFuser;
import com.darkvolfi.dvsouls.SoulMachine;
import com.darkvolfi.dvsouls.SoulFurnace;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.recipebook.AbstractFurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.BlastingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.SmeltingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.SmokingRecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The soul furnaces', Soul Fuser's and Soul Distiller's screens. */
final class SoulScreens {
    private SoulScreens() {}

    /** The game's own furnace, smoker or blast furnace screen, recipe book and all. */
    static final class FurnaceScreen extends AbstractFurnaceScreen<SoulFurnace.Menu> {
        FurnaceScreen(SoulFurnace.Menu menu, Inventory inventory, Component title) {
            super(menu, book(menu.kind()), inventory, title, new ResourceLocation("textures/gui/container/" + switch (menu.kind()) {
                case FURNACE -> "furnace";
                case SMOKER -> "smoker";
                case BLAST_FURNACE -> "blast_furnace";
            } + ".png"));
        }

        private static AbstractFurnaceRecipeBookComponent book(SoulFurnace.Kind kind) {
            return switch (kind) {
                case FURNACE -> new SmeltingRecipeBookComponent();
                case SMOKER -> new SmokingRecipeBookComponent();
                case BLAST_FURNACE -> new BlastingRecipeBookComponent();
            };
        }
    }

    /**
     * A plain grey panel like the backpack's: the inputs (the Fuser's two souls with a plus between them), the fuel under
     * them with a soul flame showing how much is left, and an arrow filling towards the result.
     */
    static final class MachineScreen<M extends SoulMachine.Menu> extends AbstractContainerScreen<M> {
        private static final int PANEL = 0xFFC6C6C6;
        private static final int LIGHT = 0xFFFFFFFF;
        private static final int SHADOW = 0xFF555555;
        private static final int SLOT_BG = 0xFF8B8B8B;
        private static final int SLOT_DARK = 0xFF373737;
        private static final int SOUL = 0xFF4FD8E8;
        private static final int SOUL_LIGHT = 0xFFB8F4FF;
        private static final ItemStack AMETHYST = new ItemStack(Items.AMETHYST_SHARD);

        MachineScreen(M menu, Inventory inventory, Component title) {
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

            // The plus between the Fuser's two souls.
            if (menu instanceof SoulFuser.Menu) g.fill(x + 48, y + 24, x + 56, y + 26, DVTheme.shade(SHADOW));
            if (menu instanceof SoulFuser.Menu) g.fill(x + 51, y + 21, x + 53, y + 29, DVTheme.shade(SHADOW));

            // The Distiller's empty amethyst slot shows a faded shard, so it's clear what goes there.
            Slot amethyst = menu.slots.get(SoulDistiller.AMETHYST);
            if (menu instanceof SoulDistiller.Menu && !amethyst.hasItem()) {
                g.renderFakeItem(AMETHYST, x + amethyst.x, y + amethyst.y);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(x + amethyst.x, y + amethyst.y, x + amethyst.x + 16, y + amethyst.y + 16, 0xA0000000 | (DVTheme.shade(SLOT_BG) & 0xFFFFFF));
                g.pose().popPose();
            }

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
