package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVChoicePopup;
import com.darkvolfi.dvlib.client.DVColorButton;
import com.darkvolfi.dvlib.client.DVSwitch;
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

public class WaypointEditScreen extends Screen {
    private final Screen parent;
    private final Waypoint existing;
    private final String dimension;

    private String nameValue;
    private int xValue;
    private int yValue;
    private int zValue;
    private int color;
    private boolean enabledValue;
    private Waypoint.Shape shapeValue;
    private String categoryValue;
    private final int initialY;
    private boolean autoY;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private DVButton saveButton;

    public WaypointEditScreen(Screen parent, Waypoint existing, int defaultX, int defaultY, int defaultZ, String dimension) {
        super(Component.translatable(existing == null ? "dvmap.waypoint.new_title" : "dvmap.waypoint.edit_title"));
        this.parent = parent;
        this.existing = existing;
        this.dimension = existing != null ? existing.dimension : dimension;
        if (existing != null) {
            this.nameValue = existing.name;
            this.xValue = existing.x;
            this.yValue = existing.y;
            this.zValue = existing.z;
            this.enabledValue = existing.enabled;
            this.color = existing.color & 0xFFFFFF;
            // A death point becomes a normal waypoint once edited, so offer the regular default shape.
            this.shapeValue = existing.deathPoint ? Waypoint.Shape.STAR : existing.shape();
            this.categoryValue = existing.category().id;
        } else {
            this.shapeValue = Waypoint.Shape.STAR;
            this.categoryValue = WaypointCategory.GENERAL;
            this.nameValue = Component.translatable("dvmap.waypoint.default_name").getString();
            this.xValue = defaultX;
            this.yValue = defaultY;
            this.zValue = defaultZ;
            this.enabledValue = true;
            this.color = Waypoint.PALETTE[(int) (Math.abs(defaultX * 31L + defaultZ) % Waypoint.PALETTE.length)];
        }
        this.initialY = this.yValue;
        this.autoY = existing != null && existing.autoY;
    }

    /** The Y is a placeholder; it moves to the surface when the chunk loads, unless the player changes it. */
    public void autoHeight() {
        this.autoY = true;
    }

    /** A new waypoint's suggested name, e.g. from a shared one. */
    public WaypointEditScreen withName(String name) {
        this.nameValue = name;
        return this;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int top = height / 2 - 74;

        nameBox = new EditBox(font, cx - 100, top + 16, 200, 18, Component.translatable("dvmap.waypoint.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(nameValue);
        nameBox.setResponder(v -> {
            nameValue = v;
            validateInputs();
        });
        addRenderableWidget(nameBox);

        xBox = new EditBox(font, cx - 100, top + 50, 62, 18, Component.literal("X"));
        xBox.setValue(String.valueOf(xValue));
        xBox.setResponder(v -> validateInputs());
        addRenderableWidget(xBox);

        yBox = new EditBox(font, cx - 31, top + 50, 62, 18, Component.literal("Y"));
        yBox.setValue(String.valueOf(yValue));
        yBox.setResponder(v -> validateInputs());
        addRenderableWidget(yBox);

        zBox = new EditBox(font, cx + 38, top + 50, 62, 18, Component.literal("Z"));
        zBox.setValue(String.valueOf(zValue));
        zBox.setResponder(v -> validateInputs());
        addRenderableWidget(zBox);

        // Shape: icon preview drawn over the right side of the button in render()
        addRenderableWidget(new DVButton(cx - 100, top + 78, 96, 16, Component.translatable("dvmap.waypoint.shape"), b -> {
            List<DVChoicePopup.Option<Waypoint.Shape>> options = new ArrayList<>();
            for (Waypoint.Shape s : Waypoint.Shape.values()) {
                options.add(new DVChoicePopup.Option<>(s, Component.translatable(s.translationKey()),
                        (gr, x, y) -> s.draw(gr, x, y, 2, color)));
            }
            DVChoicePopup.openGrid(b, options, shapeValue, options.size(), s -> shapeValue = s);
        }).menu());

        addRenderableWidget(new DVButton(cx + 4, top + 78, 96, 16, WaypointManager.category(categoryValue).displayName(), b -> {
            DVChoicePopup.open(b, categoryOptions(), categoryValue, id -> {
                WaypointCategory c = WaypointManager.category(id);
                categoryValue = c.id;
                b.setMessage(c.displayName());
                // A new waypoint takes its category's look; an existing one keeps the colour it was given.
                if (existing == null && !c.isGeneral()) {
                    color = c.color;
                    if (c.icon != null) shapeValue = c.icon;
                    rebuildWidgets();
                }
            });
        }).menu()).setTooltip(Tooltip.create(Component.translatable("dvmap.waypoint.category")));

        addRenderableWidget(new DVColorButton(cx - 100, top + 104, 96, 16, color, c -> color = c))
                .setTooltip(Tooltip.create(Component.translatable("dvmap.waypoint.color")));

        addRenderableWidget(new DVSwitch(cx + 100 - DVSwitch.WIDTH, top + 106, Component.translatable("dvmap.waypoint.visible"),
                enabledValue, v -> enabledValue = v));

        int actionY = top + 130;
        if (existing != null) {
            List<DVButton> actions = new ArrayList<>();
            if (WaypointManager.canTeleport(minecraft)) {
                actions.add(new DVButton(0, 0, 0, 16, Component.translatable("dvmap.waypoint.teleport"), b -> {
                    WaypointManager.teleportTo(minecraft, existing);
                    minecraft.setScreen(null);
                }));
            }
            DVButton share = new DVButton(0, 0, 0, 16, Component.translatable("dvmap.waypoint.share"), b -> {
                WaypointShare.share(minecraft, existing);
                minecraft.setScreen(null);
            });
            share.setTooltip(Tooltip.create(Component.translatable("dvmap.waypoint.share.tooltip")));
            actions.add(share);
            actions.add(new DVButton(0, 0, 0, 16, Component.translatable("dvmap.waypoint.delete"), b -> {
                WaypointManager.remove(existing);
                onClose();
            }));
            int w = (200 - 4 * (actions.size() - 1)) / actions.size();
            for (int i = 0; i < actions.size(); i++) {
                DVButton b = actions.get(i);
                b.setPosition(cx - 100 + i * (w + 4), actionY);
                b.setWidth(w);
                addRenderableWidget(b);
            }
            actionY += 24;
        }

        saveButton = addRenderableWidget(new DVButton(cx - 100, actionY, 96, 16, CommonComponents.GUI_DONE, b -> saveAndClose()));
        addRenderableWidget(new DVButton(cx + 4, actionY, 96, 16, CommonComponents.GUI_CANCEL, b -> onClose()));

        setInitialFocus(nameBox);
        validateInputs();
    }

    /** Every category as a menu option (its id), with its icon. */
    static List<DVChoicePopup.Option<String>> categoryOptions() {
        List<DVChoicePopup.Option<String>> options = new ArrayList<>();
        for (WaypointCategory c : WaypointManager.categories()) {
            options.add(new DVChoicePopup.Option<>(c.id, c.displayName(), (g, x, y) -> c.drawIcon(g, x, y, 1)));
        }
        return options;
    }

    private void validateInputs() {
        Integer px = parseInt(xBox);
        Integer py = parseInt(yBox);
        Integer pz = parseInt(zBox);
        boolean ok = nameValue != null && !nameValue.trim().isEmpty() && px != null && py != null && pz != null;
        if (px != null) xValue = px;
        if (py != null) yValue = py;
        if (pz != null) zValue = pz;
        if (saveButton != null) saveButton.active = ok;
    }

    private static Integer parseInt(EditBox box) {
        if (box == null) return null;
        try {
            int val = Integer.parseInt(box.getValue().trim());
            box.setTextColor(0xE0E0E0);
            return val;
        } catch (NumberFormatException e) {
            box.setTextColor(0xFF5555);
            return null;
        }
    }

    private void saveAndClose() {
        int rgb = color;
        boolean keepAuto = autoY && yValue == initialY;
        if (existing != null) {
            existing.autoY = keepAuto;
            existing.name = nameValue.trim();
            existing.x = xValue;
            existing.y = yValue;
            existing.z = zValue;
            existing.color = rgb;
            existing.enabled = enabledValue;
            existing.deathPoint = false;
            existing.shape = shapeValue;
            existing.category = categoryValue;
            WaypointManager.save();
        } else {
            Waypoint wp = new Waypoint(nameValue.trim(), xValue, yValue, zValue, dimension, rgb, enabledValue, false);
            wp.shape = shapeValue;
            wp.category = categoryValue;
            wp.autoY = keepAuto;
            WaypointManager.add(wp);
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2;
        int top = height / 2 - 74;
        int boxBottom = existing != null ? top + 178 : top + 154;

        DVUi.panel(g, cx - 112, top - 16, 224, boxBottom - (top - 16), false);

        g.drawCenteredString(font, title, cx, top - 8, 0xFFFFFF);
        g.drawString(font, Component.translatable("dvmap.waypoint.name"), cx - 100, top + 5, 0xCCCCCC);
        g.drawString(font, Component.literal("X"), cx - 100, top + 39, 0xCCCCCC);
        g.drawString(font, Component.literal("Y"), cx - 31, top + 39, 0xCCCCCC);
        g.drawString(font, Component.literal("Z"), cx + 38, top + 39, 0xCCCCCC);
        g.drawString(font, Component.translatable("dvmap.waypoint.visible"), cx + 4, top + 108, 0xCCCCCC);

        super.render(g, mouseX, mouseY, partialTick);

        // Shape preview inside its button
        shapeValue.draw(g, cx - 20, top + 86, 1, color);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
