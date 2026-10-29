package com.darkvolfi.dvbackpack.client;

import com.darkvolfi.dvbackpack.BackpackItem;
import com.darkvolfi.dvbackpack.BackpackMenu;
import com.darkvolfi.dvbackpack.network.BackpackNetwork;
import com.darkvolfi.dvbackpack.network.SetBagColorPacket;
import com.darkvolfi.dvbackpack.upgrade.FilterUpgrade;
import com.darkvolfi.dvbackpack.upgrade.TankUpgrade;
import com.darkvolfi.dvbackpack.upgrade.UpgradeType;
import com.darkvolfi.dvlib.client.DVColorPopup;
import com.darkvolfi.dvlib.client.DVTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

/**
 * In the vanilla container look (plain fills, darkened by DV Dark like every menu): a tab on top with the
 * backpack, the backpack's rows (scrolling past six), sort / put in / take out buttons, the inventory below,
 * and the upgrade slots as tabs down the right edge. Clicking a card's tab opens its panel from that tab:
 * the card's slot (to take it out) and a close arrow in its header, the tool below.
 */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int TEXT = 0xFF404040;
    private static final int REMOVE_COLOUR = 0xFFC03030;

    private static final String[] SORT = {"#######", ".......", "#####..", ".......", "###....", ".......", "#......"};
    private static final String[] DEPOSIT = {"...#...", "..###..", ".#####.", "...#...", "...#...", "...#...", "..###.."};
    private static final String[] TAKE = {"..###..", "...#...", "...#...", "...#...", ".#####.", "..###..", "...#..."};
    /** A backpack being lifted off: an arrow up out of a box. */
    private static final String[] TAKE_OFF = {"...#...", "..###..", ".#.#.#.", "...#...", "#.....#", "#.....#", "#######"};
    /** A sleeping bag with its pillow. */
    private static final String[] BED = {".......", "##.....", "##.....", "#######", "#######", "#.....#", "......."};
    /** Takes the open panel's card out into the inventory. */
    private static final String[] REMOVE = {"..#....", ".##....", "#######", "#######", ".##....", "..#....", "......."};

    private static final ResourceLocation FURNACE = new ResourceLocation("textures/gui/container/furnace.png");
    private static final ResourceLocation CRAFTING = new ResourceLocation("textures/gui/container/crafting_table.png");

    private static final int TOP_TAB_WIDTH = 28;
    private static final int TOP_TAB_HEIGHT = 24;
    private static final int BAR_X = 176;
    private static final int BAR_WIDTH = 12;
    private static final int BUTTON = 12;
    /** The filter panel's buttons, right of its 3x3 grid. */
    private static final int FILTER_BUTTON_X = 66;
    private static final int FILTER_BUTTON_WIDTH = 52;
    /** The tank card's gauge. */
    private static final int GAUGE_WIDTH = 32;
    private static final int GAUGE_HEIGHT = 52;

    private boolean dragging;

    public BackpackScreen(BackpackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.width();
        imageHeight = menu.inventoryY() + 83;
        inventoryLabelY = menu.inventoryY() - 11;
    }

    @Override
    protected void init() {
        super.init();
        topPos += TOP_TAB_HEIGHT / 2; // room for the tab above the panel
        int x = leftPos + 8 + 9 * 18 - 3 * (BUTTON + 1) + 1;
        int y = topPos + 4;
        addRenderableWidget(new SmallButton(x, y, SORT, "dvbackpack.sort", BackpackMenu.SORT));
        addRenderableWidget(new SmallButton(x + BUTTON + 1, y, DEPOSIT, "dvbackpack.deposit", BackpackMenu.DEPOSIT));
        addRenderableWidget(new SmallButton(x + 2 * (BUTTON + 1), y, TAKE, "dvbackpack.take", BackpackMenu.TAKE));
        // Left of those, from right to left: take off (worn), the sleeping bag (worn: sleep now; on the ground:
        // lay out / roll up), and its colour.
        int bx = x - 5 - BUTTON;
        BackpackMenu.Source.Kind kind = menu.source().kind();
        if (kind == BackpackMenu.Source.Kind.WORN) {
            addRenderableWidget(new SmallButton(bx, y, TAKE_OFF, "dvbackpack.take_off", BackpackMenu.TAKE_OFF));
            bx -= BUTTON + 1;
        }
        if (kind != BackpackMenu.Source.Kind.HAND) {
            String key = kind == BackpackMenu.Source.Kind.WORN ? "dvbackpack.sleep_now" : "dvbackpack.sleeping_bag";
            addRenderableWidget(new SmallButton(bx, y, BED, key, BackpackMenu.SLEEPING_BAG));
            bx -= BUTTON + 1;
        }
        addRenderableWidget(new ColourButton(bx, y));
        titleRoom = bx - leftPos - titleLabelX - 4;
    }

    /** How wide the title may be before it reaches the buttons beside it. */
    private int titleRoom = Integer.MAX_VALUE;

    private void press(int button) {
        // Runs on the server; the new contents come back with the menu's next sync.
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    // ---- scrolling

    private boolean scrolls() {
        return menu.maxScroll() > 0;
    }

    private int barTop() {
        return topPos + BackpackMenu.TOP - 1;
    }

    private int barHeight() {
        return menu.visibleRows() * 18;
    }

    private int thumbHeight() {
        return Math.max(15, barHeight() * menu.visibleRows() / menu.rows());
    }

    private void scrollTo(int row) {
        row = Mth.clamp(row, 0, menu.maxScroll());
        if (row != menu.scroll()) press(BackpackMenu.SCROLL + row);
    }

    private boolean overBar(double mouseX, double mouseY) {
        int x = leftPos + BAR_X;
        return mouseX >= x && mouseX < x + BAR_WIDTH && mouseY >= barTop() && mouseY < barTop() + barHeight();
    }

    private void dragTo(double mouseY) {
        double t = (mouseY - barTop() - thumbHeight() / 2.0) / (barHeight() - thumbHeight());
        scrollTo((int) Math.round(Mth.clamp(t, 0, 1) * menu.maxScroll()));
    }

    // ---- upgrade tabs and the open panel

    private int tabX() {
        return leftPos + menu.tabX();
    }

    private int tabY(int index) {
        return topPos + menu.shownTabY(index);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** The card tab under the mouse, or -1: clicking a closed one opens its panel, the open one (its header) closes it. */
    private int cardTabAt(double mouseX, double mouseY) {
        for (int i = 0; i < menu.upgradeCount(); i++) {
            if (menu.upgrade(i).isEmpty()) continue;
            if (inside(mouseX, mouseY, tabX(), tabY(i), BackpackMenu.TAB_WIDTH, BackpackMenu.TAB_HEIGHT)) return i;
        }
        return -1;
    }

    private int panelWidth() {
        UpgradeType type = menu.toolType();
        return type == null ? 0 : BackpackMenu.panelWidth(type);
    }

    private boolean overRemove(double mouseX, double mouseY) {
        return menu.toolType() != null
                && inside(mouseX, mouseY, tabX() + panelWidth() - BUTTON - 5, tabY(menu.openTool()) + 5, BUTTON, BUTTON);
    }

    /** Whether the mouse is over the open tank card's gauge (click it with a bucket). */
    private boolean overGauge(double mouseX, double mouseY) {
        return menu.toolType() == UpgradeType.TANK
                && inside(mouseX, mouseY, tabX() + 8, tabY(menu.openTool()) + 26, GAUGE_WIDTH, GAUGE_HEIGHT);
    }

    /** The open filter panel's button under the mouse: {@link BackpackMenu#FILTER_MODE}, {@link BackpackMenu#MAGNET} or -1. */
    private int filterButtonAt(double mouseX, double mouseY) {
        UpgradeType type = menu.toolType();
        if (type == null || !type.filter()) return -1;
        int x0 = tabX();
        int y0 = tabY(menu.openTool());
        if (inside(mouseX, mouseY, x0 + FILTER_BUTTON_X, y0 + 27, FILTER_BUTTON_WIDTH, 16)) return BackpackMenu.FILTER_MODE;
        if (type == UpgradeType.PICKUP && inside(mouseX, mouseY, x0 + FILTER_BUTTON_X, y0 + 47, FILTER_BUTTON_WIDTH, 16)) return BackpackMenu.MAGNET;
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (overGauge(mouseX, mouseY)) {
                press(BackpackMenu.TANK);
                return true;
            }
            int filterButton = filterButtonAt(mouseX, mouseY);
            if (filterButton >= 0) {
                press(filterButton);
                return true;
            }
            if (scrolls() && overBar(mouseX, mouseY)) {
                dragging = true;
                dragTo(mouseY);
                return true;
            }
            int tab = cardTabAt(mouseX, mouseY);
            if (tab >= 0) {
                press(BackpackMenu.TOOL + tab);
                return true;
            }
            if (overRemove(mouseX, mouseY)) {
                press(BackpackMenu.REMOVE + menu.openTool());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (scrolls() && mouseY < topPos + menu.inventoryY() - 12 && mouseX < leftPos + imageWidth) {
            scrollTo(menu.scroll() - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            dragTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Clicks on the top tab, the upgrade tabs or the open panel are not "outside the window" (which would drop the held stack). */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        boolean onTopTab = inside(mouseX, mouseY, left, top - TOP_TAB_HEIGHT + 2, TOP_TAB_WIDTH, TOP_TAB_HEIGHT);
        boolean onTabs = inside(mouseX, mouseY, tabX(), top, BackpackMenu.TAB_WIDTH, BackpackMenu.tabY(menu.upgradeCount()) + BackpackMenu.SHIFT);
        boolean onPanel = menu.toolType() != null
                && inside(mouseX, mouseY, tabX(), tabY(menu.openTool()), panelWidth(), BackpackMenu.PANEL_HEIGHT);
        return !onTopTab && !onTabs && !onPanel && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    // ---- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int tab = cardTabAt(mouseX, mouseY);
        if (tab >= 0) {
            String hint = menu.openTool() == tab ? "dvbackpack.close_tool" : "dvbackpack.open_tool";
            g.renderComponentTooltip(font, List.of(menu.upgrade(tab).getHoverName(),
                    Component.translatable(hint).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (overRemove(mouseX, mouseY)) {
            g.renderTooltip(font, Component.translatable("dvbackpack.remove_card"), mouseX, mouseY);
        } else if (filterButtonAt(mouseX, mouseY) == BackpackMenu.FILTER_MODE) {
            boolean only = FilterUpgrade.only(menu.upgrade(menu.openTool()));
            g.renderTooltip(font, Component.translatable(only ? "dvbackpack.filter.only.tooltip" : "dvbackpack.filter.except.tooltip"), mouseX, mouseY);
        } else if (filterButtonAt(mouseX, mouseY) == BackpackMenu.MAGNET) {
            g.renderTooltip(font, Component.translatable("dvbackpack.magnet.tooltip"), mouseX, mouseY);
        } else if (overGauge(mouseX, mouseY)) {
            FluidStack fluid = TankUpgrade.fluid(menu.upgrade(menu.openTool()));
            Component what = fluid.isEmpty() ? Component.translatable("dvbackpack.tank.empty")
                    : Component.translatable("dvbackpack.tank.amount", fluid.getDisplayName(), TankUpgrade.buckets(fluid.getAmount()), TankUpgrade.CAPACITY / 1000);
            g.renderComponentTooltip(font, List.of(what, Component.translatable("dvbackpack.tank.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    /** A raised box: light top-left edge, dark bottom-right edge. */
    private static void raised(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, DVTheme.shade(fill));
        g.fill(x, y, x + w - 1, y + 1, DVTheme.shade(LIGHT));
        g.fill(x, y, x + 1, y + h - 1, DVTheme.shade(LIGHT));
        g.fill(x + 1, y + h - 1, x + w, y + h, DVTheme.shade(SHADOW));
        g.fill(x + w - 1, y + 1, x + w, y + h, DVTheme.shade(SHADOW));
    }

    /** A sunken box, like a slot. */
    private static void sunken(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, DVTheme.shade(SLOT_BG));
        g.fill(x, y, x + w - 1, y + 1, DVTheme.shade(SLOT_DARK));
        g.fill(x, y, x + 1, y + h - 1, DVTheme.shade(SLOT_DARK));
        g.fill(x + 1, y + h - 1, x + w, y + h, DVTheme.shade(LIGHT));
        g.fill(x + w - 1, y + 1, x + w, y + h, DVTheme.shade(LIGHT));
    }

    /** A box joined to the backpack panel on its left: the panel's edge under it is painted over. */
    private void attached(GuiGraphics g, int x, int y, int w, int h, int fill) {
        raised(g, x, y, w, h, fill);
        g.fill(x - 1, y + 1, x + 1, y + h - 1, DVTheme.shade(fill));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // The tab, joined to the panel: its bottom edge is covered by the panel's fill.
        raised(g, x, y - TOP_TAB_HEIGHT + 2, TOP_TAB_WIDTH, TOP_TAB_HEIGHT, PANEL);
        raised(g, x, y, imageWidth, imageHeight, PANEL);
        g.fill(x + 1, y - 1, x + TOP_TAB_WIDTH - 1, y + 2, DVTheme.shade(PANEL));
        g.renderItem(menu.backpack(), x + (TOP_TAB_WIDTH - 16) / 2, y - TOP_TAB_HEIGHT + 6);

        // Upgrade tabs: an empty one holds a slot for a card, a full one shows its card.
        int hovered = cardTabAt(mouseX, mouseY);
        for (int i = 0; i < menu.upgradeCount(); i++) {
            if (menu.openTool() == i) continue;
            if (menu.toolType() != null && menu.upgrade(i).isEmpty()) continue; // empty tabs are put away while a panel is open
            attached(g, tabX(), tabY(i), BackpackMenu.TAB_WIDTH, BackpackMenu.TAB_HEIGHT, hovered == i ? 0xFFDADADA : PANEL);
            ItemStack card = menu.upgrade(i);
            if (!card.isEmpty()) g.renderItem(card, tabX() + 5, tabY(i) + 3);
        }

        UpgradeType tool = menu.toolType();
        if (tool != null) drawPanel(g, tool, tabX(), tabY(menu.openTool()), mouseX, mouseY);

        for (Slot slot : menu.slots) {
            if (slot.isActive()) sunken(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
        if (scrolls()) {
            int bx = x + BAR_X;
            sunken(g, bx, barTop(), BAR_WIDTH, barHeight());
            int thumbY = barTop() + 1 + Math.round((barHeight() - 2 - thumbHeight()) * menu.scroll() / (float) menu.maxScroll());
            boolean hot = dragging || overBar(mouseX, mouseY);
            raised(g, bx + 1, thumbY, BAR_WIDTH - 2, thumbHeight(), hot ? 0xFFDADADA : PANEL);
        }
    }

    private void drawPanel(GuiGraphics g, UpgradeType tool, int x0, int y0, int mouseX, int mouseY) {
        int w = BackpackMenu.panelWidth(tool);
        attached(g, x0, y0, w, BackpackMenu.PANEL_HEIGHT, PANEL);
        g.fill(x0 + 4, y0 + 22, x0 + w - 4, y0 + 23, DVTheme.shade(SHADOW)); // header line
        // The card in the header: click it to fold the panel back into its tab.
        if (cardTabAt(mouseX, mouseY) == menu.openTool()) g.fill(x0 + 4, y0 + 2, x0 + 22, y0 + 20, DVTheme.shade(0xFFDADADA));
        g.renderItem(menu.upgrade(menu.openTool()), x0 + 5, y0 + 3);
        int cx = x0 + w - BUTTON - 5;
        String name = Component.translatable("dvbackpack.upgrade." + tool.id()).getString();
        g.drawString(font, font.plainSubstrByWidth(name, cx - 4 - (x0 + 26)), x0 + 26, y0 + 8, DVTheme.shade(TEXT), false);
        int cy = y0 + 5;
        raised(g, cx, cy, BUTTON, BUTTON, overRemove(mouseX, mouseY) ? 0xFFDADADA : PANEL);
        flatIcon(g, REMOVE, cx + 3, cy + 3, DVTheme.shade(REMOVE_COLOUR));

        if (tool == UpgradeType.CRAFTING) {
            g.blit(CRAFTING, x0 + 68, y0 + 45, 90, 35, 22, 15);
            return;
        }
        if (tool == UpgradeType.TANK) {
            drawTank(g, menu.upgrade(menu.openTool()), x0 + 8, y0 + 26, overGauge(mouseX, mouseY));
            return;
        }
        if (tool.filter()) {
            // The nine filter places are slots; beside them the "only / except" switch and, for pickup, the magnet.
            ItemStack card = menu.upgrade(menu.openTool());
            int hot = filterButtonAt(mouseX, mouseY);
            boolean only = FilterUpgrade.only(card);
            textButton(g, x0 + FILTER_BUTTON_X, y0 + 27, Component.translatable(only ? "dvbackpack.filter.only" : "dvbackpack.filter.except"),
                    hot == BackpackMenu.FILTER_MODE, only ? 0xFF206020 : 0xFF902020);
            if (tool == UpgradeType.PICKUP) {
                boolean magnet = FilterUpgrade.magnet(card);
                textButton(g, x0 + FILTER_BUTTON_X, y0 + 47, Component.translatable("dvbackpack.magnet"),
                        hot == BackpackMenu.MAGNET, magnet ? 0xFF206020 : 0xFF707070);
            }
            return;
        }
        // Empty flame and arrow from the furnace screen, then the lit parts over them.
        int fx = x0 + 9;
        int fy = y0 + 45;
        int ax = x0 + 32;
        int ay = y0 + 43;
        g.blit(FURNACE, fx, fy, 56, 36, 14, 14);
        g.blit(FURNACE, ax, ay, 79, 34, 24, 17);
        int burn = menu.cookingValue("burn");
        int burnMax = Math.max(1, menu.cookingValue("burnMax"));
        if (burn > 0) {
            int h = Mth.ceil(burn * 13f / burnMax);
            g.blit(FURNACE, fx, fy + 13 - h, 176, 13 - h, 14, h + 1);
        }
        int cook = menu.cookingValue("cook");
        int total = menu.cookingValue("cookTotal");
        if (cook > 0 && total > 0) g.blit(FURNACE, ax, ay, 176, 14, cook * 24 / total + 1, 16);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int text = DVTheme.shade(TEXT);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), titleRoom), titleLabelX, titleLabelY, text, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, text, false);
        // An empty upgrade slot says what goes in it.
        Slot hovered = hoveredSlot;
        if (hovered != null && !hovered.hasItem() && menu.getCarried().isEmpty() && hovered.x == menu.tabX() + 5) {
            setTooltipForNextRenderPass(Component.translatable("dvbackpack.upgrade_slot"));
        }
    }

    /** The tank's gauge: the fluid's own texture, tinted like in the world, as high as the tank is full. */
    private void drawTank(GuiGraphics g, ItemStack card, int x, int y, boolean hot) {
        sunken(g, x - 1, y - 1, GAUGE_WIDTH + 2, GAUGE_HEIGHT + 2);
        FluidStack fluid = TankUpgrade.fluid(card);
        if (!fluid.isEmpty()) {
            IClientFluidTypeExtensions look = IClientFluidTypeExtensions.of(fluid.getFluid());
            TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(look.getStillTexture(fluid));
            int tint = look.getTintColor(fluid);
            int height = Math.max(1, GAUGE_HEIGHT * fluid.getAmount() / TankUpgrade.CAPACITY);
            RenderSystem.setShaderColor((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, 1f);
            // In 16 px tiles from the bottom up, so the texture is not stretched.
            for (int filled = 0; filled < height; filled += 16) {
                int h = Math.min(16, height - filled);
                for (int dx = 0; dx < GAUGE_WIDTH; dx += 16) {
                    g.blit(x + dx, y + GAUGE_HEIGHT - filled - h, 0, Math.min(16, GAUGE_WIDTH - dx), h, sprite);
                }
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        // A mark at every bucket.
        int buckets = TankUpgrade.CAPACITY / 1000;
        for (int b = 1; b < buckets; b++) {
            int my = y + GAUGE_HEIGHT - GAUGE_HEIGHT * b / buckets;
            g.fill(x, my, x + (b % 2 == 0 ? 8 : 4), my + 1, 0x80000000);
        }
        if (hot) g.fill(x, y, x + GAUGE_WIDTH, y + GAUGE_HEIGHT, 0x30FFFFFF);
        int tx = x + GAUGE_WIDTH + 8;
        String name = fluid.isEmpty() ? Component.translatable("dvbackpack.tank.empty").getString() : fluid.getDisplayName().getString();
        g.drawString(font, font.plainSubstrByWidth(name, 70), tx, y + 4, DVTheme.shade(TEXT), false);
        g.drawString(font, TankUpgrade.buckets(fluid.getAmount()) + " / " + buckets, tx, y + 16, DVTheme.shade(0xFF606060), false);
    }

    private void textButton(GuiGraphics g, int x, int y, Component label, boolean hot, int colour) {
        raised(g, x, y, FILTER_BUTTON_WIDTH, 16, hot ? 0xFFDADADA : PANEL);
        String text = font.plainSubstrByWidth(label.getString(), FILTER_BUTTON_WIDTH - 6);
        g.drawString(font, text, x + (FILTER_BUTTON_WIDTH - font.width(text)) / 2, y + 4, DVTheme.shade(colour), false);
    }

    /** A 7×7 icon without DVUi.icon's dark outline, which would blur a dark icon on a light button. */
    private static void flatIcon(GuiGraphics g, String[] icon, int x, int y, int colour) {
        for (int r = 0; r < icon.length; r++) {
            for (int c = 0; c < icon[r].length(); c++) {
                if (icon[r].charAt(c) == '#') g.fill(x + c, y + r, x + c + 1, y + r + 1, colour);
            }
        }
    }

    /** Shows the sleeping bag's colour; clicking it opens DV's colour picker. */
    private final class ColourButton extends AbstractButton {
        ColourButton(int x, int y) {
            super(x, y, BUTTON, BUTTON, Component.translatable("dvbackpack.bag_color"));
            setTooltip(Tooltip.create(Component.translatable("dvbackpack.bag_color")));
        }

        @Override
        public void onPress() {
            DVColorPopup.open(this, BackpackItem.bagColor(menu.backpack()), rgb -> {
                BackpackItem.setBagColor(menu.backpack(), rgb); // shown here at once; the server changes the real one
                BackpackNetwork.CHANNEL.sendToServer(new SetBagColorPacket(rgb));
            });
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            raised(g, getX(), getY(), width, height, isHoveredOrFocused() ? 0xFFDADADA : PANEL);
            g.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3, 0xFF000000 | BackpackItem.bagColor(menu.backpack()));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** A small vanilla-looking button with a 7×7 icon that runs a menu button on the server. */
    private final class SmallButton extends AbstractButton {
        private final String[] icon;
        private final int id;

        SmallButton(int x, int y, String[] icon, String key, int id) {
            super(x, y, BUTTON, BUTTON, Component.translatable(key));
            this.icon = icon;
            this.id = id;
            setTooltip(Tooltip.create(Component.translatable(key)));
        }

        @Override
        public void onPress() {
            press(id);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            raised(g, getX(), getY(), width, height, isHoveredOrFocused() ? 0xFFDADADA : PANEL);
            flatIcon(g, icon, getX() + 3, getY() + 3, DVTheme.shade(TEXT));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
