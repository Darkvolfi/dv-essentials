package com.darkvolfi.dvnewcrafts.client;

import com.darkvolfi.dvnewcrafts.custom.CustomRecipe;
import com.darkvolfi.dvnewcrafts.custom.CustomRecipes;
import com.darkvolfi.dvnewcrafts.custom.RecipeEditorMenu;
import com.darkvolfi.dvnewcrafts.custom.RecipeKind;
import com.darkvolfi.dvnewcrafts.network.NewCraftsNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * The recipe editor: saved recipes on the left, the grid, type and numbers on the right, the inventory below.
 * Drawn with plain fills and vanilla widgets so it needs no texture and no DV Lib.
 */
public class RecipeEditorScreen extends AbstractContainerScreen<RecipeEditorMenu> {
    private static final int RIGHT = RecipeEditorMenu.RIGHT;
    private static final int LIST_X = 6;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 88;
    private static final int ROW = 12;
    private static final int ROWS = 14;
    private static final int TEXT = 0x404040;

    private CycleButton<RecipeKind> kindButton;
    private EditBox nameBox;
    private EditBox countBox;
    private EditBox timeBox;
    private EditBox xpBox;
    private Button deleteButton;
    private int scroll;
    /** Name of the saved recipe being edited; empty for a new one. */
    private String selected = "";
    /** Delete needs a second click. */
    private boolean deleteArmed;
    private Component status = Component.empty();

    public RecipeEditorScreen(RecipeEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = RIGHT + 176;
        imageHeight = RecipeEditorMenu.INVENTORY_Y + 82;
        inventoryLabelX = RIGHT + 8;
        inventoryLabelY = RecipeEditorMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + RIGHT;
        int y = topPos;
        kindButton = addRenderableWidget(CycleButton.builder(RecipeKind::label).withValues(RecipeKind.values())
                .withInitialValue(menu.kind)
                .create(x + 7, y + 16, 162, 16, Component.translatable("dvnewcrafts.recipes.kind"), (b, kind) -> {
                    setKind(kind);
                    if (kind.cooking()) timeBox.setValue(String.valueOf(kind.defaultCookTime));
                }));

        countBox = addRenderableWidget(numberBox(x + 130, y + RecipeEditorMenu.RESULT_Y + 1, 30, 2, "1"));
        countBox.setTooltip(Tooltip.create(Component.translatable("dvnewcrafts.recipes.count")));
        addRenderableWidget(Button.builder(Component.translatable("dvnewcrafts.recipes.save"), b -> save())
                .bounds(x + 94, y + 78, 74, 16).build());

        nameBox = addRenderableWidget(new EditBox(font, x + 8, y + 99, 160, 14, Component.translatable("dvnewcrafts.recipes.name")));
        nameBox.setMaxLength(CustomRecipe.MAX_NAME);
        nameBox.setHint(Component.translatable("dvnewcrafts.recipes.name").withStyle(ChatFormatting.DARK_GRAY));

        timeBox = addRenderableWidget(numberBox(x + 40, y + 117, 44, 5, "200"));
        timeBox.setTooltip(Tooltip.create(Component.translatable("dvnewcrafts.recipes.time.tip")));
        xpBox = addRenderableWidget(new EditBox(font, x + 118, y + 117, 50, 14, Component.translatable("dvnewcrafts.recipes.xp")));
        xpBox.setMaxLength(6);
        xpBox.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
        xpBox.setValue("0.1");
        xpBox.setTooltip(Tooltip.create(Component.translatable("dvnewcrafts.recipes.xp.tip")));

        addRenderableWidget(Button.builder(Component.translatable("dvnewcrafts.recipes.new"), b -> newRecipe())
                .bounds(leftPos + LIST_X, y + imageHeight - 24, 42, 16).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("dvnewcrafts.recipes.delete"), b -> delete())
                .bounds(leftPos + LIST_X + 46, y + imageHeight - 24, 42, 16).build());
        setKind(menu.kind);
    }

    private EditBox numberBox(int x, int y, int width, int maxLength, String value) {
        EditBox box = new EditBox(font, x, y, width, 14, Component.empty());
        box.setMaxLength(maxLength);
        box.setFilter(s -> s.chars().allMatch(Character::isDigit));
        box.setValue(value);
        return box;
    }

    /** Shows only the slots and fields the recipe type uses. */
    private void setKind(RecipeKind kind) {
        menu.kind = kind;
        countBox.setVisible(kind.hasCount());
        timeBox.setVisible(kind.cooking());
        xpBox.setVisible(kind.cooking());
        refreshButtons();
    }

    private void refreshButtons() {
        deleteButton.active = !selected.isEmpty();
        deleteButton.setMessage(Component.translatable(deleteArmed ? "dvnewcrafts.recipes.delete.confirm" : "dvnewcrafts.recipes.delete"));
    }

    private void select(CustomRecipe recipe) {
        selected = recipe.name();
        deleteArmed = false;
        nameBox.setValue(recipe.name());
        kindButton.setValue(recipe.kind());
        setKind(recipe.kind());
        countBox.setValue(String.valueOf(recipe.count()));
        timeBox.setValue(String.valueOf(recipe.cookTime()));
        xpBox.setValue(String.valueOf(recipe.experience()));
        status = recipe.valid() ? Component.empty() : Component.translatable("dvnewcrafts.recipes.unreadable");
        // The server owns the slots; it fills them from the saved file.
        NewCraftsNetwork.CHANNEL.sendToServer(new NewCraftsNetwork.Select(recipe.name()));
    }

    private void newRecipe() {
        selected = "";
        deleteArmed = false;
        nameBox.setValue("");
        status = Component.empty();
        refreshButtons();
        NewCraftsNetwork.CHANNEL.sendToServer(new NewCraftsNetwork.Select(""));
    }

    private void save() {
        String name = CustomRecipes.cleanName(nameBox.getValue());
        if (name.isEmpty()) {
            status = Component.translatable(CustomRecipes.ERROR_NAME);
            return;
        }
        nameBox.setValue(name);
        RecipeKind kind = kindButton.getValue();
        NewCraftsNetwork.CHANNEL.sendToServer(new NewCraftsNetwork.Save(name, kind.ordinal(),
                parseInt(countBox.getValue(), 1), parseInt(timeBox.getValue(), kind.defaultCookTime),
                parseFloat(xpBox.getValue())));
    }

    private void delete() {
        if (selected.isEmpty()) return;
        if (!deleteArmed) {
            deleteArmed = true;
            refreshButtons();
            return;
        }
        deleteArmed = false;
        NewCraftsNetwork.CHANNEL.sendToServer(new NewCraftsNetwork.Delete(selected));
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The server's answer to a save or delete. */
    public static void onList(List<CustomRecipe> recipes, String message, String name) {
        if (!(Minecraft.getInstance().screen instanceof RecipeEditorScreen screen)) return;
        screen.menu.recipes = recipes;
        screen.status = Component.translatable(message, name);
        boolean saved = CustomRecipes.SAVED.equals(message);
        if (saved) screen.selected = name;
        if (recipes.stream().noneMatch(r -> r.name().equals(screen.selected))) screen.selected = "";
        screen.scroll = Math.max(0, Math.min(screen.scroll, recipes.size() - ROWS));
        screen.refreshButtons();
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (getFocused() instanceof EditBox box && !box.isMouseOver(mouseX, mouseY)) setFocused(null);
        int row = listRow(mouseX, mouseY);
        if (row >= 0 && button == 0) {
            if (row < menu.recipes.size()) select(menu.recipes.get(row));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The list row under the mouse (counting scrolled-away rows), or -1. */
    private int listRow(double mouseX, double mouseY) {
        double x = mouseX - leftPos - LIST_X;
        double y = mouseY - topPos - LIST_Y;
        if (x < 0 || x >= LIST_W || y < 0 || y >= ROWS * ROW) return -1;
        return scroll + (int) (y / ROW);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX - leftPos < RIGHT) {
            scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), menu.recipes.size() - ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While typing, keys like E (inventory) and 1-9 (hotbar swap) belong to the text box. Escape still closes.
        if (keyCode != 256 && getFocused() instanceof EditBox box && box.canConsumeInput()) {
            box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        nameBox.tick();
        countBox.tick();
        timeBox.tick();
        xpBox.tick();
    }

    // --- Drawing ---

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        panel(g, leftPos, topPos, RIGHT - 4, imageHeight);
        panel(g, leftPos + RIGHT, topPos, 176, imageHeight);
        g.fill(leftPos + LIST_X - 1, topPos + LIST_Y - 1, leftPos + LIST_X + LIST_W + 1, topPos + LIST_Y + ROWS * ROW + 1, 0xFF8B8B8B);

        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, 0xFF373737);
            g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
            g.fill(x + 1, y + 1, x + 17, y + 17, slot.isActive() ? 0xFF8B8B8B : 0xFF5A5A5A);
        }

        // Arrow from the grid to the result.
        int ax = leftPos + RecipeEditorMenu.GRID_X + 60;
        int ay = topPos + RecipeEditorMenu.RESULT_Y + 8;
        g.fill(ax, ay - 1, ax + 24, ay + 1, 0xFF8B8B8B);
        for (int i = 0; i < 5; i++) g.fill(ax + 24 + i, ay - 5 + i, ax + 25 + i, ay + 5 - i, 0xFF8B8B8B);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFC6C6C6);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, LIST_X, 6, TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        if (countBox.isVisible()) g.drawString(font, "x", RIGHT + 122, RecipeEditorMenu.RESULT_Y + 4, TEXT, false);
        if (timeBox.isVisible()) {
            g.drawString(font, Component.translatable("dvnewcrafts.recipes.time"), RIGHT + 8, 120, TEXT, false);
            g.drawString(font, Component.translatable("dvnewcrafts.recipes.xp"), RIGHT + 92, 120, TEXT, false);
        }

        List<CustomRecipe> recipes = menu.recipes;
        if (recipes.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("dvnewcrafts.recipes.empty"), LIST_X + 3, LIST_Y + 3, LIST_W - 6, 0xFFE0E0E0);
        }
        int hovered = listRow(mouseX, mouseY);
        for (int i = scroll; i < Math.min(recipes.size(), scroll + ROWS); i++) {
            CustomRecipe recipe = recipes.get(i);
            int y = LIST_Y + (i - scroll) * ROW;
            boolean isSelected = recipe.name().equals(selected);
            if (isSelected || i == hovered) {
                g.fill(LIST_X, y, LIST_X + LIST_W, y + ROW, isSelected ? 0xFF373737 : 0xFF6F6F6F);
            }
            String text = font.plainSubstrByWidth(recipe.name(), LIST_W - 6);
            g.drawString(font, text, LIST_X + 3, y + 2, recipe.valid() ? 0xFFFFFF : 0xFF8080, false);
        }

        // Below the window, where there is room for long messages.
        g.drawCenteredString(font, status, imageWidth / 2, imageHeight + 6, 0xFFFFFF);
    }
}
