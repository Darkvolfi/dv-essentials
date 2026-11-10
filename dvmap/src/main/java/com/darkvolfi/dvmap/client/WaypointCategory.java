package com.darkvolfi.dvmap.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** A waypoint category: the built-in ones a world starts with, plus the player's own. Saved with the waypoints. */
public final class WaypointCategory {
    public static final String GENERAL = "general";
    private static final String[] BUILT_IN = {GENERAL, "home", "mine", "village", "farm", "portal", "danger", "explore"};
    private static final int[] BUILT_IN_COLORS = {0xB0BEC5, 0x66DD66, 0x8D6E63, 0xFFE055, 0x33CCAA, 0x9B6BFF, 0xFF5555, 0x4FC3F7};

    public String id;
    /** Null for a built-in category that keeps its translated name. */
    public String name;
    public int color;
    /** Null for no icon. */
    public Waypoint.Shape icon;

    public WaypointCategory() {}

    public WaypointCategory(String id, String name, int color, Waypoint.Shape icon) {
        this.id = id;
        this.name = name;
        this.color = color & 0xFFFFFF;
        this.icon = icon;
    }

    /** The categories a world has before the player changes them. */
    public static List<WaypointCategory> defaults() {
        List<WaypointCategory> list = new ArrayList<>();
        for (int i = 0; i < BUILT_IN.length; i++) list.add(new WaypointCategory(BUILT_IN[i], null, BUILT_IN_COLORS[i], null));
        return list;
    }

    public boolean isGeneral() {
        return GENERAL.equals(id);
    }

    public String translationKey() {
        return "dvmap.category." + id;
    }

    public Component displayName() {
        if (name != null && !name.isBlank()) return Component.literal(name);
        return I18n.exists(translationKey()) ? Component.translatable(translationKey()) : Component.literal(id);
    }

    /** The category's icon in its colour, or a small colour square when it has none, centred on (cx, cy). */
    public void drawIcon(GuiGraphics g, int cx, int cy, int px) {
        if (icon != null) {
            icon.draw(g, cx, cy, px, color);
        } else {
            int r = 2 * px;
            g.fill(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, 0xFF000000);
            g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF000000 | color);
        }
    }
}
