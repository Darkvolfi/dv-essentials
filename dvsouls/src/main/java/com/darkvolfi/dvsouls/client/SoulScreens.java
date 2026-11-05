package com.darkvolfi.dvsouls.client;

import com.darkvolfi.dvlib.client.DVTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import com.darkvolfi.dvsouls.DVSouls;
import com.darkvolfi.dvsouls.SoulDistiller;
import com.darkvolfi.dvsouls.SoulTank;
import net.minecraft.client.gui.Font;
import java.util.Locale;
import com.darkvolfi.dvsouls.SoulFuser;
import com.darkvolfi.dvsouls.SoulMachine;
import com.darkvolfi.dvsouls.SoulFurnace;
import com.darkvolfi.dvsouls.SoulRefiner;
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

/** The soul furnaces', Soul Fuser's, Soul Distiller's and Soul Refiner's screens. */
final class SoulScreens {
    private SoulScreens() {}

    /** The bar of souls at a machine's side: dark blue filling from the bottom, with a lighter streak down it. */
    record Bar(int x, int y, int w, int h, int capacity) {
        private static final int FILL = 0xFF1E3A8A;
        private static final int STREAK = 0xFF4F7BD8;

        void draw(GuiGraphics g, int left, int top, int amount) {
            int bx = left + x, by = top + y;
            MachineScreen.sunken(g, bx, by, w, h);
            int level = Math.round(Math.min(amount, capacity) / (float) capacity * (h - 2));
            if (level <= 0) return;
            g.fill(bx + 1, by + h - 1 - level, bx + w - 1, by + h - 1, DVTheme.shade(FILL));
            g.fill(bx + 3, by + h - 1 - level, bx + 5, by + h - 1, DVTheme.shade(STREAK));
        }

        /** "12 / 40 souls" while the mouse is over it. */
        void tooltip(GuiGraphics g, Font font, int left, int top, int mouseX, int mouseY, int amount) {
            int bx = left + x, by = top + y;
            if (mouseX < bx || mouseX >= bx + w || mouseY < by || mouseY >= by + h) return;
            g.renderTooltip(font, Component.translatable("dvsouls.bar", souls(amount), souls(capacity)), mouseX, mouseY);
        }

        private static String souls(int amount) {
            float souls = SoulTank.souls(amount);
            return souls == Math.round(souls) ? String.valueOf(Math.round(souls)) : String.format(Locale.ROOT, "%.1f", souls);
        }
    }

    /** A faded item in an empty slot, so it's clear what goes there. */
    static void ghost(GuiGraphics g, int left, int top, Slot slot, ItemStack item) {
        if (slot.hasItem()) return;
        int x = left + slot.x, y = top + slot.y;
        g.renderFakeItem(item, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(x, y, x + 16, y + 16, 0xA0000000 | (DVTheme.shade(MachineScreen.SLOT_BG) & 0xFFFFFF));
        g.pose().popPose();
    }

    /** The game's own furnace, smoker or blast furnace screen, recipe book and all, with the bar of souls left of the slot under it. */
    static final class FurnaceScreen extends AbstractFurnaceScreen<SoulFurnace.Menu> {
        private static final Bar BAR = new Bar(41, 17, 12, 52, SoulFurnace.Entity.CAPACITY);

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

        @Override
        protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
            super.renderBg(g, partialTick, mouseX, mouseY);
            BAR.draw(g, leftPos, topPos, menu.amount());
            ghost(g, leftPos, topPos, menu.slots.get(1), BUCKET);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            BAR.tooltip(g, font, leftPos, topPos, mouseX, mouseY, menu.amount());
        }
    }

    private static final ItemStack BUCKET = new ItemStack(DVSouls.PURE_SOUL_BUCKET.get());

    /**
     * A plain grey panel like the backpack's: the bar of souls at the side with its slot under it, the inputs (the Fuser's two
     * souls with a plus between them), and an arrow filling towards the result.
     */
    static final class MachineScreen<M extends SoulMachine.Menu> extends AbstractContainerScreen<M> {
        private static final int PANEL = 0xFFC6C6C6;
        private static final int LIGHT = 0xFFFFFFFF;
        private static final int SHADOW = 0xFF555555;
        private static final int SLOT_BG = 0xFF8B8B8B;
        private static final int SLOT_DARK = 0xFF373737;
        private static final int SOUL = 0xFF4FD8E8;
        private static final ItemStack AMETHYST = new ItemStack(Items.AMETHYST_SHARD);

        private static final Bar BAR = new Bar(8, 17, 16, 34, SoulMachine.CAPACITY);

        MachineScreen(M menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            super.render(g, mouseX, mouseY, partialTick);
            renderTooltip(g, mouseX, mouseY);
            BAR.tooltip(g, font, leftPos, topPos, mouseX, mouseY, menu.amount());
        }

        @Override
        protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
            int x = leftPos, y = topPos;
            raised(g, x, y, imageWidth, imageHeight);
            for (Slot slot : menu.slots) sunken(g, x + slot.x - 1, y + slot.y - 1, 18, 18);

            // The plus between the Fuser's two souls.
            if (menu instanceof SoulFuser.Menu) g.fill(x + 48, y + 24, x + 56, y + 26, DVTheme.shade(SHADOW));
            if (menu instanceof SoulFuser.Menu) g.fill(x + 51, y + 21, x + 53, y + 29, DVTheme.shade(SHADOW));

            // Empty slots show faded what goes in them: the Distiller's amethyst and test tube, and a bucket under the bar.
            if (menu instanceof SoulDistiller.Menu) {
                ghost(g, x, y, menu.slots.get(SoulDistiller.AMETHYST), AMETHYST);
                ghost(g, x, y, menu.slots.get(SoulMachine.RESULT), new ItemStack(DVSouls.SOUL_TUBE.get()));
            }
            ghost(g, x, y, menu.slots.get(SoulMachine.FUEL), BUCKET);
            BAR.draw(g, x, y, menu.amount());

            // The arrow: a shaft and a head, outlined, filling left to right with the fusion's progress.
            int ax = x + 86, ay = y + 35, filled = Math.round(menu.progress() * 24);
            for (int col = 0; col < 24; col++) {
                int half = col < 16 ? 2 : 8 - (col - 16);   // half the arrow's height at this column
                int colour = col < filled ? SOUL : SLOT_BG;
                g.fill(ax + col, ay + 8 - half, ax + col + 1, ay + 8 + half, DVTheme.shade(colour));
            }
        }

        static void raised(GuiGraphics g, int x, int y, int w, int h) {
            g.fill(x, y, x + w, y + h, DVTheme.shade(PANEL));
            g.fill(x, y, x + w - 1, y + 1, DVTheme.shade(LIGHT));
            g.fill(x, y, x + 1, y + h - 1, DVTheme.shade(LIGHT));
            g.fill(x + 1, y + h - 1, x + w, y + h, DVTheme.shade(SHADOW));
            g.fill(x + w - 1, y + 1, x + w, y + h, DVTheme.shade(SHADOW));
        }

        static void sunken(GuiGraphics g, int x, int y, int w, int h) {
            g.fill(x, y, x + w, y + h, DVTheme.shade(SLOT_BG));
            g.fill(x, y, x + w - 1, y + 1, DVTheme.shade(SLOT_DARK));
            g.fill(x, y, x + 1, y + h - 1, DVTheme.shade(SLOT_DARK));
            g.fill(x + 1, y + h - 1, x + w, y + h, DVTheme.shade(LIGHT));
            g.fill(x + w - 1, y + 1, x + w, y + h, DVTheme.shade(LIGHT));
        }
    }

    /**
     * The Soul Refiner's panel, like the machines': the soul over the amethyst, an arrow filling from the soul into the
     * bar of souls, the empty jar beside the amethyst, and a bucket or bottle to fill under the bar.
     */
    static final class RefinerScreen extends AbstractContainerScreen<SoulRefiner.Menu> {
        private static final Bar BAR = new Bar(86, 17, 18, 34, SoulRefiner.CAPACITY);
        private static final ItemStack AMETHYST = new ItemStack(Items.AMETHYST_SHARD);
        private static final ItemStack EMPTY_BUCKET = new ItemStack(Items.BUCKET);

        RefinerScreen(SoulRefiner.Menu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            super.render(g, mouseX, mouseY, partialTick);
            renderTooltip(g, mouseX, mouseY);
            BAR.tooltip(g, font, leftPos, topPos, mouseX, mouseY, menu.amount());
            int lx = leftPos + SoulRefiner.LENS_X, ly = topPos + 17;
            if (mouseX >= lx - 1 && mouseX < lx + 17 && mouseY >= ly - 1 && mouseY < ly + 17) {
                int wear = menu.lensWear();
                g.renderTooltip(font, wear < 0 ? Component.translatable("dvsouls.refiner.no_lens")
                        : Component.translatable("dvsouls.refiner.lens", menu.lensLife() - wear, menu.lensLife()), mouseX, mouseY);
            }
        }

        @Override
        protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
            int x = leftPos, y = topPos;
            MachineScreen.raised(g, x, y, imageWidth, imageHeight);
            for (Slot slot : menu.slots) MachineScreen.sunken(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
            ghost(g, x, y, menu.slots.get(SoulRefiner.AMETHYST), AMETHYST);
            ghost(g, x, y, menu.slots.get(SoulRefiner.CONTAINER), EMPTY_BUCKET);
            BAR.draw(g, x, y, menu.amount());

            // The amethyst in use, out of reach in its own frame, cracking as it wears; a dotted line from the slot it
            // is drawn up from.
            int lx = x + SoulRefiner.LENS_X, ly = y + 17, wear = menu.lensWear();
            MachineScreen.sunken(g, lx - 1, ly - 1, 18, 18);
            for (int dy = 36; dy < 52; dy += 3) g.fill(lx + 7, y + dy, lx + 9, y + dy + 2, DVTheme.shade(MachineScreen.SLOT_BG));
            if (wear >= 0) {
                g.renderItem(AMETHYST, lx, ly);
                int stage = 1 + Math.min(2, wear * 3 / Math.max(1, menu.lensLife()));
                if (wear > 0) {
                    RenderSystem.enableBlend();
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.blit(new ResourceLocation(DVSouls.MOD_ID, "textures/block/amethyst_crack_" + stage + ".png"), lx, ly, 0, 0, 16, 16, 16, 16);
                    g.pose().popPose();
                    RenderSystem.disableBlend();
                }
            }

            // The arrow from the soul through the amethyst to the bar, filling with the refining.
            int[][] parts = {{48, 11, 0}, {SoulRefiner.LENS_X + 18, 86 - SoulRefiner.LENS_X - 18, 1}};
            int total = parts[0][1] + parts[1][1], done = Math.round(menu.progress() * total), col = 0;
            for (int[] part : parts) {
                for (int i = 0; i < part[1]; i++, col++) {
                    int fromEnd = part[1] - 1 - i;
                    int half = part[2] == 1 && fromEnd < 5 ? 1 + fromEnd : 2;
                    g.fill(x + part[0] + i, ly + 8 - half, x + part[0] + i + 1, ly + 8 + half,
                            DVTheme.shade(col < done ? MachineScreen.SOUL : MachineScreen.SLOT_BG));
                }
            }
        }
    }
}
