package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVColorButton;
import com.darkvolfi.dvlib.client.DVUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/** Name and colour for a Create rail line or train on the map; "remove" takes the name off again. */
final class RailEditScreen extends Screen {
    private final Screen parent;
    private final BiConsumer<String, Integer> onSave;
    /** Null when there is no name yet, so nothing to remove. */
    private final Runnable onRemove;

    private String nameValue;
    private int color;
    private DVButton saveButton;

    RailEditScreen(Screen parent, Component title, String name, int color, BiConsumer<String, Integer> onSave, Runnable onRemove) {
        super(title);
        this.parent = parent;
        this.nameValue = name;
        this.color = color & 0xFFFFFF;
        this.onSave = onSave;
        this.onRemove = onRemove;
    }

    private int top() {
        return height / 2 - 40;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int top = top();

        EditBox nameBox = new EditBox(font, cx - 100, top + 16, 200, 18, Component.translatable("dvmap.waypoint.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(nameValue);
        nameBox.setResponder(v -> {
            nameValue = v;
            if (saveButton != null) saveButton.active = !v.isBlank();
        });
        addRenderableWidget(nameBox);

        addRenderableWidget(new DVColorButton(cx - 100, top + 44, 200, 16, color, c -> color = c & 0xFFFFFF))
                .setTooltip(Tooltip.create(Component.translatable("dvmap.waypoint.color")));

        int actionY = top + 70;
        if (onRemove != null) {
            addRenderableWidget(new DVButton(cx - 100, actionY, 200, 16, Component.translatable("dvmap.rail.remove"), b -> {
                onRemove.run();
                onClose();
            }));
            actionY += 24;
        }
        saveButton = addRenderableWidget(new DVButton(cx - 100, actionY, 96, 16, CommonComponents.GUI_DONE, b -> {
            onSave.accept(nameValue.trim(), color);
            onClose();
        }));
        saveButton.active = !nameValue.isBlank();
        addRenderableWidget(new DVButton(cx + 4, actionY, 96, 16, CommonComponents.GUI_CANCEL, b -> onClose()));
        setInitialFocus(nameBox);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2;
        int top = top();
        int boxBottom = top + 70 + (onRemove != null ? 24 : 0) + 24;
        DVUi.panel(g, cx - 112, top - 16, 224, boxBottom - (top - 16), false);
        g.drawCenteredString(font, title, cx, top - 8, 0xFFFFFF);
        g.drawString(font, Component.translatable("dvmap.waypoint.name"), cx - 100, top + 5, 0xCCCCCC);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
