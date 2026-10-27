package com.darkvolfi.dvmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class WaypointListScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int TOP = 36;

    private final Screen parent;
    private List<Waypoint> waypoints = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    /** Null shows every category. */
    private static Waypoint.Category filter;

    public WaypointListScreen(Screen parent) {
        super(Component.translatable("dvmap.waypoint.list_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (minecraft.level != null) {
            waypoints = WaypointManager.forDimension(minecraft.level.dimension());
            if (filter != null) waypoints.removeIf(wp -> wp.category() != filter);
        }

        addRenderableWidget(Button.builder(filterLabel(), b -> {
            Waypoint.Category[] all = Waypoint.Category.values();
            int next = filter == null ? 0 : filter.ordinal() + 1;
            filter = next < all.length ? all[next] : null;
            scrollOffset = 0;
            init();
        }).bounds(width / 2 + 50, 8, 100, 20).build());
        int viewHeight = height - TOP - 40;
        int maxRows = Math.max(1, viewHeight / ROW_HEIGHT);
        maxScroll = Math.max(0, waypoints.size() - maxRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int cx = width / 2;
        boolean canTp = WaypointManager.canTeleport(minecraft);

        for (int i = 0; i < maxRows && (i + scrollOffset) < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i + scrollOffset);
            int y = TOP + i * ROW_HEIGHT;

            // Toggle visibility
            addRenderableWidget(Button.builder(Component.literal(wp.enabled ? "✔" : "✘"), b -> {
                wp.enabled = !wp.enabled;
                WaypointManager.save();
                init();
            }).bounds(cx + 22, y, 22, 20).build());

            // Teleport
            if (canTp) {
                addRenderableWidget(Button.builder(Component.translatable("dvmap.waypoint.tp_short"), b -> {
                    WaypointManager.teleportTo(minecraft, wp);
                    minecraft.setScreen(null);
                }).bounds(cx + 46, y, 36, 20).build());
            }

            // Edit
            addRenderableWidget(Button.builder(Component.translatable("dvmap.waypoint.edit_short"), b ->
                    minecraft.setScreen(new WaypointEditScreen(this, wp, wp.x, wp.y, wp.z, wp.dimension)))
                    .bounds(cx + 84, y, 42, 20).build());

            // Delete
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                WaypointManager.remove(wp);
                init();
            }).bounds(cx + 128, y, 22, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("dvmap.waypoint.add"), b -> {
            if (minecraft.player != null && minecraft.level != null) {
                minecraft.setScreen(new WaypointEditScreen(this, null,
                        minecraft.player.getBlockX(), minecraft.player.getBlockY(), minecraft.player.getBlockZ(),
                        minecraft.level.dimension().location().toString()));
            }
        }).bounds(cx - 154, height - 28, 150, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx + 4, height - 28, 150, 20).build());
    }

    private static Component filterLabel() {
        Component value = filter == null
                ? Component.translatable("dvmap.category.all")
                : Component.translatable(filter.translationKey());
        return Component.translatable("dvmap.waypoint.filter", value);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxScroll);
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2;
        g.drawCenteredString(font, title, cx, 14, 0xFFFFFF);

        if (waypoints.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("dvmap.waypoint.empty"), cx, height / 2 - 10, 0xA0A0A0);
        } else {
            int viewHeight = height - TOP - 40;
            int maxRows = Math.max(1, viewHeight / ROW_HEIGHT);
            for (int i = 0; i < maxRows && (i + scrollOffset) < waypoints.size(); i++) {
                Waypoint wp = waypoints.get(i + scrollOffset);
                int y = TOP + i * ROW_HEIGHT;
                g.fill(cx - 150, y, cx + 150, y + 20, 0x55000000);
                wp.shape().draw(g, cx - 140, y + 10, 1, wp.enabled ? wp.color : 0x666666);
                Component line = wp.label().copy().append(" (" + wp.x + ", " + wp.y + ", " + wp.z + ")");
                g.drawString(font, line, cx - 128, y + 6, wp.enabled ? 0xFFFFFF : 0x777777);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
