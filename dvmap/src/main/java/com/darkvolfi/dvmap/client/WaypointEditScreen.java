package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class WaypointEditScreen extends Screen {
    private final Screen parent;
    private final Waypoint existing;
    private final String dimension;

    private String nameValue;
    private int xValue;
    private int yValue;
    private int zValue;
    private int colorIndex;
    private boolean enabledValue;
    private Waypoint.Shape shapeValue;
    private Waypoint.Category categoryValue;
    private final int initialY;
    private boolean autoY;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private Button saveButton;

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
            this.colorIndex = findColorIndex(existing.color);
            // A death point becomes a normal waypoint once edited, so offer the regular default shape.
            this.shapeValue = existing.deathPoint ? Waypoint.Shape.STAR : existing.shape();
            this.categoryValue = existing.category();
        } else {
            this.shapeValue = Waypoint.Shape.STAR;
            this.categoryValue = Waypoint.Category.GENERAL;
            this.nameValue = Component.translatable("dvmap.waypoint.default_name").getString();
            this.xValue = defaultX;
            this.yValue = defaultY;
            this.zValue = defaultZ;
            this.enabledValue = true;
            this.colorIndex = (int) (Math.abs(defaultX * 31L + defaultZ) % Waypoint.PALETTE.length);
        }
        this.initialY = this.yValue;
        this.autoY = existing != null && existing.autoY;
    }

    /** The Y is a placeholder; it moves to the surface when the chunk loads, unless the player changes it. */
    public void autoHeight() {
        this.autoY = true;
    }

    private static int findColorIndex(int rgb) {
        for (int i = 0; i < Waypoint.PALETTE.length; i++) {
            if (Waypoint.PALETTE[i] == (rgb & 0xFFFFFF)) return i;
        }
        return 0;
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
        addRenderableWidget(Button.builder(Component.translatable("dvmap.waypoint.shape"), b -> {
            Waypoint.Shape[] all = Waypoint.Shape.values();
            shapeValue = all[(shapeValue.ordinal() + 1) % all.length];
        }).bounds(cx - 100, top + 76, 96, 20).build());

        addRenderableWidget(CycleButton.<Waypoint.Category>builder(c -> Component.translatable(c.translationKey()))
                .withValues(Waypoint.Category.values())
                .withInitialValue(categoryValue)
                .create(cx + 4, top + 76, 96, 20, Component.translatable("dvmap.waypoint.category"), (b, val) -> categoryValue = val));

        addRenderableWidget(Button.builder(Component.translatable("dvmap.waypoint.color"), b -> {
            colorIndex = (colorIndex + 1) % Waypoint.PALETTE.length;
        }).bounds(cx - 100, top + 102, 96, 20).build());

        addRenderableWidget(CycleButton.onOffBuilder(enabledValue)
                .create(cx + 4, top + 102, 96, 20, Component.translatable("dvmap.waypoint.visible"), (b, val) -> enabledValue = val));

        int actionY = top + 130;
        if (existing != null) {
            if (WaypointManager.canTeleport(minecraft)) {
                addRenderableWidget(Button.builder(Component.translatable("dvmap.waypoint.teleport"), b -> {
                    WaypointManager.teleportTo(minecraft, existing);
                    minecraft.setScreen(null);
                }).bounds(cx - 100, actionY, 96, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("dvmap.waypoint.delete"), b -> {
                WaypointManager.remove(existing);
                onClose();
            }).bounds(cx + 4, actionY, 96, 20).build());
            actionY += 26;
        }

        saveButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> saveAndClose())
                .bounds(cx - 100, actionY, 96, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(cx + 4, actionY, 96, 20).build());

        setInitialFocus(nameBox);
        validateInputs();
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
        int rgb = Waypoint.PALETTE[colorIndex];
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
        int boxBottom = existing != null ? top + 188 : top + 162;

        g.fill(cx - 112, top - 16, cx + 112, boxBottom, DVTheme.shade(0xDD1E1E28));
        g.renderOutline(cx - 112, top - 16, 224, boxBottom - (top - 16), 0xFF4A4A5A);

        g.drawCenteredString(font, title, cx, top - 8, 0xFFFFFF);
        g.drawString(font, Component.translatable("dvmap.waypoint.name"), cx - 100, top + 5, 0xCCCCCC);
        g.drawString(font, Component.literal("X"), cx - 100, top + 39, 0xCCCCCC);
        g.drawString(font, Component.literal("Y"), cx - 31, top + 39, 0xCCCCCC);
        g.drawString(font, Component.literal("Z"), cx + 38, top + 39, 0xCCCCCC);

        super.render(g, mouseX, mouseY, partialTick);

        // Shape preview and colour swatch inside their buttons
        shapeValue.draw(g, cx - 14, top + 86, 1, Waypoint.PALETTE[colorIndex]);
        int swatchColor = 0xFF000000 | Waypoint.PALETTE[colorIndex];
        g.fill(cx - 20, top + 106, cx - 8, top + 118, swatchColor);
        g.renderOutline(cx - 21, top + 105, 14, 14, 0xFF000000);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
