package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVChoicePopup;
import com.darkvolfi.dvlib.client.DVColorButton;
import com.darkvolfi.dvlib.client.DVUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Name, colour and optional icon of a waypoint category; deleting one moves its waypoints to General. */
public class CategoryEditScreen extends Screen {
    private final Screen parent;
    /** Null for a new category. */
    private final WaypointCategory existing;

    private String nameValue;
    private int color;
    private Waypoint.Shape iconValue;
    private DVButton saveButton;

    public CategoryEditScreen(Screen parent, WaypointCategory existing) {
        super(Component.translatable(existing == null ? "dvmap.category.new_title" : "dvmap.category.edit_title"));
        this.parent = parent;
        this.existing = existing;
        if (existing != null) {
            nameValue = existing.displayName().getString();
            color = existing.color;
            iconValue = existing.icon;
        } else {
            nameValue = "";
            color = Waypoint.PALETTE[(int) (System.nanoTime() & 0x7FFFFFFF) % Waypoint.PALETTE.length];
        }
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int top = height / 2 - 50;

        EditBox nameBox = new EditBox(font, cx - 100, top + 16, 200, 18, Component.translatable("dvmap.waypoint.name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(nameValue);
        nameBox.setResponder(v -> {
            nameValue = v;
            if (saveButton != null) saveButton.active = !v.isBlank();
        });
        addRenderableWidget(nameBox);

        addRenderableWidget(new DVColorButton(cx - 100, top + 44, 96, 16, color, c -> color = c))
                .setTooltip(Tooltip.create(Component.translatable("dvmap.waypoint.color")));

        // Icon: preview drawn over the right side of the button in render()
        addRenderableWidget(new DVButton(cx + 4, top + 44, 96, 16, Component.translatable("dvmap.category.icon"), b -> {
            List<DVChoicePopup.Option<Waypoint.Shape>> options = new ArrayList<>();
            options.add(new DVChoicePopup.Option<>(null, Component.translatable("dvmap.category.icon.none"),
                    (g, x, y) -> new WaypointCategory("", null, color, null).drawIcon(g, x, y, 1)));
            for (Waypoint.Shape s : Waypoint.Shape.values()) {
                options.add(new DVChoicePopup.Option<>(s, Component.translatable(s.translationKey()),
                        (g, x, y) -> s.draw(g, x, y, 2, color)));
            }
            DVChoicePopup.openGrid(b, options, iconValue, options.size(), s -> iconValue = s);
        }).menu());

        int actionY = top + 70;
        if (existing != null && !existing.isGeneral()) {
            DVButton delete = addRenderableWidget(new DVButton(cx - 100, actionY, 200, 16, Component.translatable("dvmap.category.delete"), b -> {
                WaypointManager.removeCategory(existing);
                onClose();
            }));
            delete.setTooltip(Tooltip.create(Component.translatable("dvmap.category.delete.tooltip")));
            actionY += 24;
        }

        saveButton = addRenderableWidget(new DVButton(cx - 100, actionY, 96, 16, CommonComponents.GUI_DONE, b -> saveAndClose()));
        saveButton.active = !nameValue.isBlank();
        addRenderableWidget(new DVButton(cx + 4, actionY, 96, 16, CommonComponents.GUI_CANCEL, b -> onClose()));
        setInitialFocus(nameBox);
    }

    private void saveAndClose() {
        String name = nameValue.trim();
        if (existing == null) {
            WaypointManager.addCategory(name, color, iconValue);
        } else {
            // A built-in category left with its own (translated) name keeps following the game language.
            boolean unchanged = existing.name == null && name.equals(existing.displayName().getString());
            existing.name = unchanged ? null : name;
            existing.color = color;
            existing.icon = iconValue;
            WaypointManager.save();
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2;
        int top = height / 2 - 50;
        int boxBottom = top + 70 + (existing != null && !existing.isGeneral() ? 24 : 0) + 24;
        DVUi.panel(g, cx - 112, top - 16, 224, boxBottom - (top - 16), false);
        g.drawCenteredString(font, title, cx, top - 8, 0xFFFFFF);
        g.drawString(font, Component.translatable("dvmap.waypoint.name"), cx - 100, top + 5, 0xCCCCCC);
        super.render(g, mouseX, mouseY, partialTick);
        new WaypointCategory("", null, color, iconValue).drawIcon(g, cx + 84, top + 52, 1);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
