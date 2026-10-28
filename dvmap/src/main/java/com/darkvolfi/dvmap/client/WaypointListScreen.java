package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVChoicePopup;
import com.darkvolfi.dvlib.client.DVIconButton;
import com.darkvolfi.dvlib.client.DVUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** The waypoints of this dimension in a DV panel; each row has icon buttons for show/hide, teleport, edit and delete. */
public class WaypointListScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int PAD = 10;
    private static final int ICON = 14;

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

    private int panelWidth() {
        return Math.min(width - 20, 320);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int rowsTop() {
        return 8 + 28;
    }

    private int maxRows() {
        return Math.max(1, (height - 8 - 6 - rowsTop()) / ROW_HEIGHT);
    }

    @Override
    protected void init() {
        clearWidgets();
        if (minecraft.level != null) {
            waypoints = WaypointManager.forDimension(minecraft.level.dimension());
            if (filter != null) waypoints.removeIf(wp -> wp.category() != filter);
        }
        int l = left();
        int r = l + panelWidth();
        int top = 8;

        addRenderableWidget(new DVIconButton(r - 20, top + 6, ICON, DVUi.CLOSE, CommonComponents.GUI_DONE, this::onClose));
        addRenderableWidget(new DVIconButton(r - 38, top + 6, ICON, DVUi.PLUS, Component.translatable("dvmap.waypoint.add"), () -> {
            if (minecraft.player != null && minecraft.level != null) {
                minecraft.setScreen(new WaypointEditScreen(this, null,
                        minecraft.player.getBlockX(), minecraft.player.getBlockY(), minecraft.player.getBlockZ(),
                        minecraft.level.dimension().location().toString()));
            }
        }));
        addRenderableWidget(new DVButton(r - 42 - 90, top + 5, 88, 16, filterLabel(), b -> {
            List<DVChoicePopup.Option<Waypoint.Category>> options = new ArrayList<>();
            options.add(new DVChoicePopup.Option<>(null, Component.translatable("dvmap.category.all")));
            for (Waypoint.Category c : Waypoint.Category.values()) {
                options.add(new DVChoicePopup.Option<>(c, Component.translatable(c.translationKey())));
            }
            DVChoicePopup.open(b, options, filter, c -> {
                filter = c;
                scrollOffset = 0;
                init();
            });
        }).menu());

        maxScroll = Math.max(0, waypoints.size() - maxRows());
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        boolean canTp = WaypointManager.canTeleport(minecraft);

        for (int i = 0; i < maxRows() && (i + scrollOffset) < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i + scrollOffset);
            int y = rowsTop() + i * ROW_HEIGHT + (ROW_HEIGHT - ICON) / 2;
            int x = r - PAD - ICON;

            addRenderableWidget(new DVIconButton(x, y, ICON, DVUi.CLOSE, Component.translatable("dvmap.waypoint.delete"), () -> {
                WaypointManager.remove(wp);
                init();
            }));
            x -= ICON + 2;
            addRenderableWidget(new DVIconButton(x, y, ICON, MapIcons.EDIT, Component.translatable("dvmap.waypoint.edit_short"),
                    () -> minecraft.setScreen(new WaypointEditScreen(this, wp, wp.x, wp.y, wp.z, wp.dimension))));
            x -= ICON + 2;
            if (canTp) {
                addRenderableWidget(new DVIconButton(x, y, ICON, MapIcons.TELEPORT, Component.translatable("dvmap.waypoint.teleport"), () -> {
                    WaypointManager.teleportTo(minecraft, wp);
                    minecraft.setScreen(null);
                }));
                x -= ICON + 2;
            }
            addRenderableWidget(new DVIconButton(x, y, ICON, DVUi.EYE, Component.translatable("dvmap.waypoint.visible"), () -> {
                wp.enabled = !wp.enabled;
                WaypointManager.save();
                init();
            }).selected(wp.enabled));
        }
    }

    private static Component filterLabel() {
        return filter == null ? Component.translatable("dvmap.category.all") : Component.translatable(filter.translationKey());
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
        int l = left();
        int w = panelWidth();
        DVUi.panel(g, l, 8, w, height - 16, false);
        g.drawString(font, title, l + PAD, 8 + 9, 0xFFE6E6F0);
        g.hLine(l + PAD, l + w - PAD, rowsTop() - 4, DVUi.rim());

        if (waypoints.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("dvmap.waypoint.empty"), l + w / 2, rowsTop() + 12, 0xA0A0A0);
        }
        for (int i = 0; i < maxRows() && (i + scrollOffset) < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i + scrollOffset);
            int y = rowsTop() + i * ROW_HEIGHT;
            if (mouseY >= y && mouseY < y + ROW_HEIGHT && mouseX >= l && mouseX < l + w) {
                g.fill(l + 4, y, l + w - 4, y + ROW_HEIGHT, 0x22FFFFFF);
            }
            wp.shape().draw(g, l + PAD + 4, y + ROW_HEIGHT / 2, 1, wp.enabled ? wp.color : 0x666666);
            g.drawString(font, wp.label(), l + PAD + 14, y + 6, wp.enabled ? 0xFFE6E6F0 : 0xFF777777);
            String coords = wp.x + ", " + wp.y + ", " + wp.z;
            int coordsX = l + PAD + 18 + font.width(wp.label());
            g.drawString(font, coords, coordsX, y + 6, 0xFF6A6478);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
