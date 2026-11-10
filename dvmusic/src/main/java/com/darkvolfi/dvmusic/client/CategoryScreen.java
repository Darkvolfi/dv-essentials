package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVSwitch;
import com.darkvolfi.dvlib.client.DVUi;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/** Makes a new category or edits one: its name and the places its songs play. Songs are ticked in the music screen. */
public class CategoryScreen extends Screen {
    private static final int W = 220;
    private static final int PAD = 8;

    private final Screen parent;
    private final Categories.Category category;
    /** Gets the saved category, or null when it was deleted. */
    private final Consumer<Categories.Category> onDone;
    private final Set<Situation> places = EnumSet.noneOf(Situation.class);
    private EditBox name;
    private boolean confirmDelete;
    private Component error;

    public CategoryScreen(Screen parent, Categories.Category category, Consumer<Categories.Category> onDone) {
        super(Component.translatable(category == null ? "dvmusic.category.new" : "dvmusic.category.edit"));
        this.parent = parent;
        this.category = category;
        this.onDone = onDone;
        if (category != null) places.addAll(category.places);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return Math.max(10, (height - height()) / 2);
    }

    private int height() {
        return 66 + Situation.values().length * 16 + 30;
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        String typed = name != null ? name.getValue() : category != null ? category.name() : "";
        name = new EditBox(font, l + PAD + 1, t + 34, W - 2 * PAD - 2, 14, Component.translatable("dvmusic.category.name"));
        name.setMaxLength(40);
        name.setValue(typed);
        name.setHint(Component.translatable("dvmusic.category.name").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(name);
        setInitialFocus(name);

        int y = t + 66;
        for (Situation s : Situation.values()) {
            addRenderableWidget(new DVSwitch(l + W - PAD - DVSwitch.WIDTH, y, Component.translatable(s.translationKey()),
                    places.contains(s), on -> {
                        if (on) places.add(s);
                        else places.remove(s);
                    }));
            y += 16;
        }

        int by = t + height() - 24;
        int bw = (W - 2 * PAD - 8) / 3;
        addRenderableWidget(new DVButton(l + PAD, by, bw, 16, Component.translatable("dvmusic.category.save"), b -> save()));
        addRenderableWidget(new DVButton(l + PAD + bw + 4, by, bw, 16, CommonComponents.GUI_CANCEL, b -> onClose()));
        if (category != null) {
            addRenderableWidget(new DVButton(l + PAD + 2 * (bw + 4), by, bw, 16,
                    Component.translatable(confirmDelete ? "dvmusic.category.delete_sure" : "dvmusic.category.delete"), b -> {
                if (!confirmDelete) {
                    confirmDelete = true;
                    rebuildWidgets();
                    return;
                }
                Categories.delete(category);
                onDone.accept(null);
                onClose();
            }));
        }
    }

    private void save() {
        String n = name.getValue().strip();
        if (n.isEmpty()) {
            error = Component.translatable("dvmusic.category.no_name");
            return;
        }
        Categories.Category saved = category;
        if (saved == null) {
            saved = Categories.create(n);
        } else if (!Categories.rename(saved, n)) {
            error = Component.translatable("dvmusic.category.taken");
            return;
        }
        saved.places.clear();
        saved.places.addAll(places);
        Categories.save();
        onDone.accept(saved);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int l = left(), t = top();
        DVUi.panel(g, l, t, W, height(), false);
        g.drawString(font, title, l + PAD, t + 9, DVUi.accent(), false);
        g.drawString(font, Component.translatable("dvmusic.category.name"), l + PAD, t + 23, 0xFFA0A0A0, false);
        g.drawString(font, Component.translatable("dvmusic.category.places"), l + PAD, t + 54, 0xFFA0A0A0, false);
        int y = t + 66;
        for (Situation s : Situation.values()) {
            g.drawString(font, Component.translatable(s.translationKey()), l + PAD + 4, y + 2, 0xFFE6E6F0, false);
            y += 16;
        }
        Component hint = error != null ? error
                : places.isEmpty() ? Component.translatable("dvmusic.category.places.none") : null;
        if (hint != null) {
            g.drawString(font, font.plainSubstrByWidth(hint.getString(), W - 2 * PAD), l + PAD, y + 1,
                    error != null ? 0xFFFF6060 : 0xFF707080, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == 257 || key == 335) && name.isFocused()) { // Enter
            save();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
