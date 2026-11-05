package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVChoicePopup;
import com.darkvolfi.dvlib.client.DVIconButton;
import com.darkvolfi.dvlib.client.DVUi;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Waypoints and places in a DV panel: a tab per dimension at the top, then sections (own waypoints, automatic
 * points, structures, and one per map hook such as DV Team's settlements) that fold up with the triangle on
 * their heading.
 */
public class WaypointListScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int PAD = 10;
    private static final int ICON = 14;
    private static final int INDENT = 12;
    private static final int TAB_HEIGHT = 14;
    private static final String[] OPEN = {".......", "#######", ".#####.", "..###..", "...#...", ".......", "......."};
    private static final String[] CLOSED = {".#.....", ".##....", ".###...", ".####..", ".###...", ".##....", ".#....."};
    private static final String[] SPAWN = {"...#...", "..###..", ".#####.", "#######", ".#...#.", ".#.#.#.", ".#####."};
    private static final List<String> BASE_DIMENSIONS = List.of(
            Level.OVERWORLD.location().toString(), Level.NETHER.location().toString(), Level.END.location().toString());

    /** Folded sections, kept while the game runs. */
    private static final Set<String> FOLDED = new HashSet<>();
    /** Null shows every category. */
    private static Waypoint.Category filter;

    private enum Kind { HEADER, WAYPOINT, PLACE, STRUCTURE }

    /** One line of the list: a section heading or one of its entries. */
    private record Row(Kind kind, String section, Component title, int count,
                       Waypoint waypoint, MapOverlayHook.Place place, StructureMarkers.Entry structure) {
        static Row header(String section, Component title, int count) {
            return new Row(Kind.HEADER, section, title, count, null, null, null);
        }
    }

    private final Screen parent;
    private String dimension;
    private final List<Row> rows = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;

    public WaypointListScreen(Screen parent) {
        super(Component.translatable("dvmap.waypoint.list_title"));
        this.parent = parent;
        Minecraft mc = Minecraft.getInstance();
        this.dimension = mc.level != null ? mc.level.dimension().location().toString() : BASE_DIMENSIONS.get(0);
    }

    private int panelWidth() {
        return Math.min(width - 20, 320);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int tabsTop() {
        return 8 + 26;
    }

    private int rowsTop() {
        return tabsTop() + TAB_HEIGHT + 8;
    }

    private int maxRows() {
        return Math.max(1, (height - 8 - 6 - rowsTop()) / ROW_HEIGHT);
    }

    private List<String> dimensions() {
        Set<String> dims = new LinkedHashSet<>(BASE_DIMENSIONS);
        for (Waypoint wp : WaypointManager.all()) dims.add(wp.dimension);
        dims.addAll(StructureMarkers.dimensions());
        dims.add(dimension);
        return new ArrayList<>(dims);
    }

    private static Component dimensionName(String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        if (rl == null) return Component.literal(dim);
        String key = "dvmap.dimension." + rl.getNamespace() + "." + rl.getPath();
        if (I18n.exists(key)) return Component.translatable(key);
        String words = rl.getPath().replace('_', ' ');
        return Component.literal(words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1));
    }

    private void buildRows() {
        rows.clear();
        List<Row> own = new ArrayList<>();
        List<Row> auto = new ArrayList<>();
        for (Waypoint wp : WaypointManager.all()) {
            if (!dimension.equals(wp.dimension)) continue;
            if (wp.deathPoint) {
                auto.add(new Row(Kind.WAYPOINT, "auto", null, 0, wp, null, null));
            } else if (filter == null || wp.category() == filter) {
                own.add(new Row(Kind.WAYPOINT, "own", null, 0, wp, null, null));
            }
        }
        if (minecraft.level != null && dimension.equals(Level.OVERWORLD.location().toString())) {
            BlockPos spawn = minecraft.level.getSharedSpawnPos();
            auto.add(new Row(Kind.PLACE, "auto", null, 0, null, new MapOverlayHook.Place(
                    Component.translatable("dvmap.waypoint.spawn"), SPAWN, 0x7CE08A, spawn.getX(), spawn.getY(), spawn.getZ()), null));
        }
        List<Row> structures = new ArrayList<>();
        for (StructureMarkers.Entry e : StructureMarkers.forDimension(dimension)) {
            structures.add(new Row(Kind.STRUCTURE, "structures", null, 0, null, null, e));
        }
        section("own", Component.translatable("dvmap.waypoint.section.own"), own);
        section("auto", Component.translatable("dvmap.waypoint.section.auto"), auto);
        section("structures", Component.translatable("dvmap.waypoint.section.structures"), structures);

        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimension));
        for (int i = 0; i < MapOverlayHook.HOOKS.size(); i++) {
            MapOverlayHook hook = MapOverlayHook.HOOKS.get(i);
            Component title = hook.placesTitle();
            if (title == null) continue;
            String id = "hook" + i;
            List<Row> places = new ArrayList<>();
            for (MapOverlayHook.Place p : hook.places(key)) places.add(new Row(Kind.PLACE, id, null, 0, null, p, null));
            section(id, title, places);
        }
    }

    private void section(String id, Component title, List<Row> entries) {
        rows.add(Row.header(id, title, entries.size()));
        if (!FOLDED.contains(id)) rows.addAll(entries);
    }

    @Override
    protected void init() {
        clearWidgets();
        buildRows();
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

        // One tab per dimension, sharing the panel's width.
        List<String> dims = dimensions();
        int inner = panelWidth() - 2 * PAD;
        int tabW = Math.min(90, (inner - (dims.size() - 1) * 2) / dims.size());
        for (int i = 0; i < dims.size(); i++) {
            String dim = dims.get(i);
            Component name = dimensionName(dim);
            if (dim.equals(dimension)) name = name.copy().withStyle(ChatFormatting.BOLD);
            addRenderableWidget(new DVButton(l + PAD + i * (tabW + 2), tabsTop(), tabW, TAB_HEIGHT, name, b -> {
                dimension = dim;
                scrollOffset = 0;
                init();
            }));
        }

        maxScroll = Math.max(0, rows.size() - maxRows());
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        boolean canTp = WaypointManager.canTeleport(minecraft);

        for (int i = 0; i < maxRows() && (i + scrollOffset) < rows.size(); i++) {
            Row row = rows.get(i + scrollOffset);
            if (row.kind() == Kind.HEADER) continue;
            int y = rowsTop() + i * ROW_HEIGHT + (ROW_HEIGHT - ICON) / 2;
            int x = r - PAD - ICON;
            Waypoint wp = row.waypoint();

            if (wp != null) {
                addRenderableWidget(new DVIconButton(x, y, ICON, DVUi.CLOSE, Component.translatable("dvmap.waypoint.delete"), () -> {
                    WaypointManager.remove(wp);
                    init();
                }));
                x -= ICON + 2;
                addRenderableWidget(new DVIconButton(x, y, ICON, MapIcons.EDIT, Component.translatable("dvmap.waypoint.edit_short"),
                        () -> minecraft.setScreen(new WaypointEditScreen(this, wp, wp.x, wp.y, wp.z, wp.dimension))));
                x -= ICON + 2;
            }
            if (canTp) {
                addRenderableWidget(new DVIconButton(x, y, ICON, MapIcons.TELEPORT, Component.translatable("dvmap.waypoint.teleport"), () -> {
                    teleport(row);
                    minecraft.setScreen(null);
                }));
                x -= ICON + 2;
            }
            if (wp != null) {
                addRenderableWidget(new DVIconButton(x, y, ICON, DVUi.EYE, Component.translatable("dvmap.waypoint.visible"), () -> {
                    wp.enabled = !wp.enabled;
                    WaypointManager.save();
                    init();
                }).selected(wp.enabled));
            }
        }
    }

    private void teleport(Row row) {
        if (row.waypoint() != null) {
            WaypointManager.teleportTo(minecraft, row.waypoint());
        } else if (row.place() != null) {
            MapOverlayHook.Place p = row.place();
            WaypointManager.teleport(minecraft, dimension, p.x(), p.y() == MapOverlayHook.NO_Y ? "~" : String.valueOf(p.y()), p.z());
        } else {
            WaypointManager.teleport(minecraft, dimension, row.structure().x, "~", row.structure().z);
        }
    }

    private static Component filterLabel() {
        return filter == null ? Component.translatable("dvmap.category.all") : Component.translatable(filter.translationKey());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int i = (int) Math.floor((mouseY - rowsTop()) / ROW_HEIGHT);
        int l = left();
        if (button == 0 && mouseY >= rowsTop() && i < maxRows() && i + scrollOffset < rows.size()
                && mouseX >= l && mouseX < l + panelWidth()) {
            Row row = rows.get(i + scrollOffset);
            if (row.kind() == Kind.HEADER) {
                if (!FOLDED.remove(row.section())) FOLDED.add(row.section());
                init();
                return true;
            }
        }
        return false;
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

        // Underline the open dimension's tab.
        List<String> dims = dimensions();
        int inner = w - 2 * PAD;
        int tabW = Math.min(90, (inner - (dims.size() - 1) * 2) / dims.size());
        int tab = dims.indexOf(dimension);
        g.fill(l + PAD + tab * (tabW + 2), tabsTop() + TAB_HEIGHT + 1, l + PAD + tab * (tabW + 2) + tabW, tabsTop() + TAB_HEIGHT + 3,
                DVUi.accent());

        for (int i = 0; i < maxRows() && (i + scrollOffset) < rows.size(); i++) {
            Row row = rows.get(i + scrollOffset);
            int y = rowsTop() + i * ROW_HEIGHT;
            boolean hover = mouseY >= y && mouseY < y + ROW_HEIGHT && mouseX >= l && mouseX < l + w;
            if (row.kind() == Kind.HEADER) {
                if (hover) g.fill(l + 4, y, l + w - 4, y + ROW_HEIGHT, 0x22FFFFFF);
                Component heading = row.title().copy().withStyle(ChatFormatting.BOLD);
                int x = l + PAD;
                g.drawString(font, heading, x, y + 6, 0xFFE6E6F0);
                x += font.width(heading) + 4;
                g.drawString(font, "(" + row.count() + ")", x, y + 6, 0xFF6A6478);
                g.hLine(l + PAD, l + w - PAD - 14, y + ROW_HEIGHT - 2, DVUi.rim());
                DVUi.icon(g, FOLDED.contains(row.section()) ? CLOSED : OPEN, l + w - PAD - 5, y + ROW_HEIGHT / 2, 1, 0xFFE6E6F0);
                continue;
            }
            if (hover) g.fill(l + 4 + INDENT, y, l + w - 4, y + ROW_HEIGHT, 0x22FFFFFF);
            drawEntry(g, row, l + PAD + INDENT, y);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawEntry(GuiGraphics g, Row row, int x, int y) {
        Component name;
        String coords;
        int text = 0xFFE6E6F0;
        int cy = y + ROW_HEIGHT / 2;
        if (row.waypoint() != null) {
            Waypoint wp = row.waypoint();
            wp.shape().draw(g, x + 4, cy, 1, wp.enabled ? wp.color : 0x666666);
            name = wp.label();
            coords = wp.x + ", " + wp.y + ", " + wp.z;
            if (!wp.enabled) text = 0xFF777777;
        } else if (row.place() != null) {
            MapOverlayHook.Place p = row.place();
            DVUi.icon(g, p.icon(), x + 4, cy, 1, 0xFF000000 | p.color());
            name = p.name();
            coords = p.y() == MapOverlayHook.NO_Y ? p.x() + ", " + p.z() : p.x() + ", " + p.y() + ", " + p.z();
        } else {
            StructureMarkers.Entry e = row.structure();
            StructureMarkers.draw(g, x + 4, cy + 4, 1);
            name = e.name();
            coords = e.x + ", " + e.z;
        }
        g.drawString(font, name, x + 14, y + 6, text);
        g.drawString(font, coords, x + 18 + font.width(name), y + 6, 0xFF6A6478);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
