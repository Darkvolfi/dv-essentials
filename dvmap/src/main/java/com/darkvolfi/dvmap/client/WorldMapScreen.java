package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvlib.client.DVChoicePopup;
import com.darkvolfi.dvlib.client.DVIconButton;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvmap.DVMap;
import com.darkvolfi.dvmap.MapConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorldMapScreen extends Screen {
    public static final int TOP_BAR_HEIGHT = 28;
    private static final int STATUS_HEIGHT = 18;
    private static final int TOOL_SIZE = 18;
    private static final int TOGGLE_SIZE = 16;

    private int bottomBarHeight = STATUS_HEIGHT + TOGGLE_SIZE + 6;
    /** Bottom-bar toggles and how to relabel them after any click (one may depend on another). */
    private final List<Runnable> toggleRefreshers = new ArrayList<>();

    private double viewX;
    private double viewZ;
    private float zoom = 2.0f;
    private boolean initializedCenter;
    private boolean panning;
    private int lastDragChunkX = Integer.MIN_VALUE;
    private int lastDragChunkZ = Integer.MIN_VALUE;

    public WorldMapScreen() {
        super(Component.translatable("dvmap.screen.title"));
    }

    @Override
    protected void init() {
        if (!initializedCenter && minecraft.player != null) {
            viewX = minecraft.player.getX();
            viewZ = minecraft.player.getZ();
            initializedCenter = true;
        }

        int x = 6;
        int y = 5;
        int step = TOOL_SIZE + 3;

        addRenderableWidget(new DVIconButton(x, y, TOOL_SIZE, MapIcons.CENTER, Component.translatable("dvmap.screen.center"), () -> {
            if (minecraft.player != null) {
                viewX = minecraft.player.getX();
                viewZ = minecraft.player.getZ();
            }
        }));
        x += step;

        DVIconButton[] layer = new DVIconButton[1];
        layer[0] = addRenderableWidget(new DVIconButton(x, y, TOOL_SIZE, MapIcons.LAYERS, layerButtonLabel(), () -> {
            List<DVChoicePopup.Option<MapConfig.LayerMode>> options = new ArrayList<>();
            for (MapConfig.LayerMode mode : MapConfig.LayerMode.values()) {
                String key = "dvmap.config.layerMode." + mode.name().toLowerCase(Locale.ROOT);
                options.add(new DVChoicePopup.Option<>(mode, Component.translatable(key)));
            }
            DVChoicePopup.open(layer[0], options, MapConfig.LAYER_MODE.get(), mode -> {
                MapConfig.LAYER_MODE.set(mode);
                MapConfig.SPEC.save();
                layer[0].setTooltip(Tooltip.create(layerButtonLabel()));
            });
        }));
        x += step;

        DVIconButton[] grid = new DVIconButton[1];
        grid[0] = addRenderableWidget(new DVIconButton(x, y, TOOL_SIZE, MapIcons.GRID, gridButtonLabel(), () -> {
            MapConfig.SHOW_CHUNK_GRID.set(!MapConfig.SHOW_CHUNK_GRID.get());
            MapConfig.SPEC.save();
            grid[0].selected(MapConfig.SHOW_CHUNK_GRID.get()).setTooltip(Tooltip.create(gridButtonLabel()));
        }).selected(MapConfig.SHOW_CHUNK_GRID.get()));
        x += step;

        addRenderableWidget(new DVIconButton(x, y, TOOL_SIZE, MapIcons.PIN, Component.translatable("dvmap.screen.waypoints"),
                () -> minecraft.setScreen(new WaypointListScreen(this))));
        x += step;

        addRenderableWidget(new DVIconButton(x, y, TOOL_SIZE, DVUi.PLUS, Component.translatable("dvmap.waypoint.add"), () -> {
            if (minecraft.player != null && minecraft.level != null) {
                minecraft.setScreen(new WaypointEditScreen(this, null,
                        minecraft.player.getBlockX(), minecraft.player.getBlockY(), minecraft.player.getBlockZ(),
                        minecraft.level.dimension().location().toString()));
            }
        }));

        addRenderableWidget(new DVIconButton(width - TOOL_SIZE - 6, y, TOOL_SIZE, DVUi.GEAR, Component.translatable("dvmap.screen.settings"),
                () -> minecraft.setScreen(new DVConfigScreen(this, DVMap.MOD_ID, MapConfig.SPEC, null, null))));

        // Allow hooks (e.g., DV Team) to add toolbar buttons
        for (MapOverlayHook hook : MapOverlayHook.HOOKS) {
            hook.onWorldMapInit(this, this::addRenderableWidget);
        }

        initToggles();
    }

    /** Quick on/off settings as a centred row of icons along the bottom; lit while on. */
    private void initToggles() {
        toggleRefreshers.clear();
        boolean server = RemotePlayers.serverHasMod();
        Component needServer = server ? null : Component.translatable("dvmap.bar.need_server");
        List<DVIconButton> buttons = new ArrayList<>();
        buttons.add(toggle(MapIcons.HOSTILE, "dvmap.bar.hostiles", MapConfig.SHOW_HOSTILES, null, null));
        buttons.add(toggle(MapIcons.ANIMAL, "dvmap.bar.animals", MapConfig.SHOW_ANIMALS, null, null));
        buttons.add(toggle(MapIcons.PLAYER, "dvmap.bar.players", MapConfig.SHOW_PLAYERS, null, null));
        buttons.add(toggle(MapIcons.SUN, "dvmap.bar.day_night", MapConfig.DAY_NIGHT, null, null));
        buttons.add(toggle(MapIcons.FLAG, "dvmap.bar.structures", MapConfig.SHOW_STRUCTURES, needServer, null));
        buttons.add(toggle(MapIcons.HIDE, "dvmap.bar.hide_me", MapConfig.HIDE_ME, needServer, null));
        // Hidden on the map means the name is hidden too: shown as on and locked.
        buttons.add(toggle(MapIcons.TAG, "dvmap.bar.hide_name", MapConfig.HIDE_MY_NAME, needServer, MapConfig.HIDE_ME));
        if (CreateTrains.installed()) buttons.add(toggle(MapIcons.TRAIN, "dvmap.bar.create_trains", MapConfig.SHOW_CREATE_TRAINS, null, null));
        DVIconButton export = new DVIconButton(0, 0, TOGGLE_SIZE, MapIcons.IMAGE, Component.translatable("dvmap.bar.export"),
                () -> MapExport.export(minecraft));
        export.setTooltip(Tooltip.create(Component.translatable("dvmap.bar.export").withStyle(ChatFormatting.BOLD)
                .append(Component.literal("\n")).append(Component.translatable("dvmap.bar.export.tooltip")
                        .withStyle(s -> s.withBold(false).withColor(ChatFormatting.GRAY)))));
        buttons.add(export);

        int gap = 3;
        bottomBarHeight = STATUS_HEIGHT + TOGGLE_SIZE + 6;
        int x = (width - buttons.size() * (TOGGLE_SIZE + gap) + gap) / 2;
        int y = height - TOGGLE_SIZE - 4;
        for (DVIconButton b : buttons) {
            b.setPosition(x, y);
            x += TOGGLE_SIZE + gap;
            addRenderableWidget(b);
        }
    }

    private DVIconButton toggle(String[] icon, String key, ForgeConfigSpec.BooleanValue value, Component note, ForgeConfigSpec.BooleanValue forcedBy) {
        DVIconButton button = new DVIconButton(0, 0, TOGGLE_SIZE, icon, Component.translatable(key), () -> {
            value.set(!value.get());
            MapConfig.SPEC.save();
            toggleRefreshers.forEach(Runnable::run);
        });
        toggleRefreshers.add(() -> {
            boolean forced = forcedBy != null && forcedBy.get();
            boolean on = forced || value.get();
            button.active = !forced;
            button.selected(on);
            MutableComponent tip = Component.translatable(key).withStyle(ChatFormatting.BOLD)
                    .append(Component.literal("\n"))
                    .append(Component.translatable(on ? "options.on" : "options.off")
                            .withStyle(s -> s.withBold(false).withColor(on ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
            Component extra = forced ? Component.translatable("dvmap.bar.name_auto") : note;
            if (extra != null) tip.append(Component.literal("\n")).append(extra.copy().withStyle(s -> s.withBold(false).withColor(ChatFormatting.GOLD)));
            button.setTooltip(Tooltip.create(tip));
        });
        toggleRefreshers.get(toggleRefreshers.size() - 1).run();
        return button;
    }

    private Component layerButtonLabel() {
        String key = "dvmap.config.layerMode." + MapConfig.LAYER_MODE.get().name().toLowerCase(Locale.ROOT);
        return Component.translatable("dvmap.screen.layer", Component.translatable(key));
    }

    private Component gridButtonLabel() {
        return Component.translatable(MapConfig.SHOW_CHUNK_GRID.get() ? "dvmap.screen.grid_on" : "dvmap.screen.grid_off");
    }

    public int mapTop() {
        return TOP_BAR_HEIGHT;
    }

    public int mapBottom() {
        return height - bottomBarHeight;
    }

    public int mapCenterX() {
        return width / 2;
    }

    public int mapCenterY() {
        return (mapTop() + mapBottom()) / 2;
    }

    public double screenToWorldX(double mouseX) {
        return viewX + (mouseX - mapCenterX()) / zoom;
    }

    public double screenToWorldZ(double mouseY) {
        return viewZ + (mouseY - mapCenterY()) / zoom;
    }

    public float worldToScreenX(double wx) {
        return mapCenterX() + (float) ((wx - viewX) * zoom);
    }

    public float worldToScreenY(double wz) {
        return mapCenterY() + (float) ((wz - viewZ) * zoom);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF100E16);

        if (minecraft.level != null && minecraft.player != null) {
            MapData.ensureSession(minecraft);
            renderMapCanvas(g, mouseX, mouseY, partialTick);
        }

        // Top and bottom bars
        g.fill(0, 0, width, TOP_BAR_HEIGHT, DVUi.PANEL);
        g.hLine(0, width, TOP_BAR_HEIGHT - 1, DVUi.rim());

        g.fill(0, mapBottom(), width, height, DVUi.PANEL);
        g.hLine(0, width, mapBottom(), DVUi.rim());

        renderBottomStatus(g, mouseX, mouseY);

        for (MapOverlayHook hook : MapOverlayHook.HOOKS) {
            hook.renderWorldMapExtra(g, this, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderMapCanvas(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int top = mapTop();
        int bottom = mapBottom();
        int cx = mapCenterX();
        int cy = mapCenterY();

        boolean cave = MapData.isCaveModeActive(minecraft);
        int minWx = Mth.floor(screenToWorldX(0)) - 16;
        int maxWx = Mth.ceil(screenToWorldX(width)) + 16;
        int minWz = Mth.floor(screenToWorldZ(top)) - 16;
        int maxWz = Mth.ceil(screenToWorldZ(bottom)) + 16;

        int minRx = minWx >> MapData.REGION_SHIFT;
        int maxRx = maxWx >> MapData.REGION_SHIFT;
        int minRz = minWz >> MapData.REGION_SHIFT;
        int maxRz = maxWz >> MapData.REGION_SHIFT;

        g.enableScissor(0, top, width, bottom);

        // 1. Discovered region tiles
        float light = cave ? 1.0f : MapData.dayLight(minecraft, partialTick);
        RenderSystem.setShaderColor(light, light, light, 1.0f);
        for (int rz = minRz; rz <= maxRz; rz++) {
            for (int rx = minRx; rx <= maxRx; rx++) {
                MapData.RegionTile tile = MapData.getTile(rx, rz, cave);
                if (tile == null) continue;
                int sx = Math.round(worldToScreenX(rx * MapData.REGION_SIZE));
                int sy = Math.round(worldToScreenY(rz * MapData.REGION_SIZE));
                int drawSize = Math.round(MapData.REGION_SIZE * zoom);
                g.blit(tile.location, sx, sy, drawSize, drawSize, 0, 0, MapData.REGION_SIZE, MapData.REGION_SIZE, MapData.REGION_SIZE, MapData.REGION_SIZE);
            }
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 2. Chunk grid
        int minCx = minWx >> 4;
        int maxCx = maxWx >> 4;
        int minCz = minWz >> 4;
        int maxCz = maxWz >> 4;
        if (MapConfig.SHOW_CHUNK_GRID.get() && zoom >= 0.5f) {
            for (int chunkX = minCx; chunkX <= maxCx; chunkX++) {
                int sx = Math.round(worldToScreenX(chunkX << 4));
                g.vLine(sx, top, bottom, (chunkX & 31) == 0 ? 0x66FFD54F : 0x33FFFFFF);
            }
            for (int chunkZ = minCz; chunkZ <= maxCz; chunkZ++) {
                int sy = Math.round(worldToScreenY(chunkZ << 4));
                g.hLine(0, width, sy, (chunkZ & 31) == 0 ? 0x66FFD54F : 0x33FFFFFF);
            }
        }

        // 3. External chunk overlays (DV Team claims & selection)
        ResourceKey<Level> dim = minecraft.level.dimension();
        for (MapOverlayHook hook : MapOverlayHook.HOOKS) {
            hook.renderChunkOverlay(g, dim, minCx, minCz, maxCx, maxCz, cx, cy, viewX, viewZ, zoom, false);
        }
        CreateTrains.renderTracks(g, this, minecraft.level, minCx, minCz, maxCx, maxCz, zoom);

        // 4. Hovered block frame; its chunk faintly, a little stronger when blocks get too small to see
        if (mouseY >= top && mouseY < bottom) {
            int hbx = Mth.floor(screenToWorldX(mouseX));
            int hbz = Mth.floor(screenToWorldZ(mouseY));
            int sx = Math.round(worldToScreenX((hbx >> 4) << 4));
            int sy = Math.round(worldToScreenY((hbz >> 4) << 4));
            int sw = Math.max(2, Math.round(16 * zoom));
            g.renderOutline(sx, sy, sw, sw, zoom >= 2.0f ? 0x33FFFFFF : 0x66FFFFFF);
            if (zoom >= 1.0f) {
                int bx = Math.round(worldToScreenX(hbx));
                int by = Math.round(worldToScreenY(hbz));
                int bw = Math.max(2, Math.round(worldToScreenX(hbx + 1)) - bx);
                g.renderOutline(bx, by, bw, bw, 0xDDFFFFFF);
            }
        }

        // 5. Structures, then waypoints on top
        for (StructureMarkers.Entry s : StructureMarkers.forDimension(dim)) {
            int sx = Math.round(worldToScreenX(s.x + 0.5));
            int sy = Math.round(worldToScreenY(s.z + 0.5));
            StructureMarkers.draw(g, sx, sy + 6, 2);
            if (zoom >= 1.0f || isMouseNear(mouseX, mouseY, sx, sy, 8)) {
                g.drawCenteredString(font, s.name(), sx, sy + 9, 0xFF000000 | StructureMarkers.color());
            }
        }
        List<Waypoint> waypoints = WaypointManager.forDimension(dim);
        for (Waypoint wp : waypoints) {
            if (!wp.enabled) continue;
            int sx = Math.round(worldToScreenX(wp.x + 0.5));
            int sy = Math.round(worldToScreenY(wp.z + 0.5));
            wp.shape().draw(g, sx, sy, 2, wp.color);
            if (zoom >= 0.75f || isMouseNear(mouseX, mouseY, sx, sy, 8)) {
                g.drawCenteredString(font, wp.label(), sx, sy + 9, 0xFF000000 | wp.color);
            }
        }

        // 6. Creatures and other players
        double halfView = Math.max(width, bottom - top) / 2.0 / zoom + 8;
        List<MapMarkers.Marker> markers = MapMarkers.collect(minecraft, partialTick, viewX, viewZ, halfView);
        List<int[]> points = new ArrayList<>(markers.size());
        MapMarkers.Marker hovered = null;
        int[] hoveredPoint = null;
        for (MapMarkers.Marker m : markers) {
            int[] p = {Math.round(worldToScreenX(m.x())), Math.round(worldToScreenY(m.z()))};
            points.add(p);
            if (hovered == null && isMouseNear(mouseX, mouseY, p[0], p[1], 6)) {
                hovered = m;
                hoveredPoint = p;
            }
        }
        MapMarkers.drawAll(g, markers, points, 8,Mth.lerp(partialTick, minecraft.player.yo, minecraft.player.getY()));
        Entity train = CreateTrains.renderTrains(g, this, minecraft.level, partialTick, mouseX, mouseY);
        if (hovered != null) train = null;
        for (int i = 0; i < markers.size(); i++) {
            MapMarkers.Marker m = markers.get(i);
            if (m.kind() == MapMarkers.Kind.PLAYER && m.name() != null && m != hovered) {
                g.drawCenteredString(font, m.name(), points.get(i)[0], points.get(i)[1] + 8, 0xFFFFFFFF);
            }
        }

        // 7. Local player marker
        MapMarkers.drawArrow(g, Math.round(worldToScreenX(minecraft.player.getX())),
                Math.round(worldToScreenY(minecraft.player.getZ())), minecraft.player.getViewYRot(partialTick) + 180.0f);

        // 8. Coordinates of the hovered square, next to it
        if (mouseY >= top && mouseY < bottom && hovered == null && train == null) {
            int wx = Mth.floor(screenToWorldX(mouseX));
            int wz = Mth.floor(screenToWorldZ(mouseY));
            String wy = MapData.isLoaded(minecraft.level, wx, wz)
                    ? String.valueOf(MapData.sampleSurfaceHeight(minecraft.level, wx, wz)) : "?";
            String coords = wx + ", " + wy + ", " + wz;
            int w = font.width(coords);
            int lx = Math.min(mouseX + 10, width - w - 4);
            int ly = Math.max(mouseY - 14, top + 2);
            g.fill(lx - 3, ly - 2, lx + w + 3, ly + 10, 0xCC15121C);
            g.drawString(font, coords, lx, ly, 0xFFE6E6F0);
        }

        // 9. Name of the hovered creature, player or train, above everything
        if (hovered != null || train != null) {
            Component name = hovered != null ? MapMarkers.nameOf(hovered) : train.getDisplayName();
            if (name != null) {
                int w = font.width(name);
                int nx = (hovered != null ? hoveredPoint[0] : Math.round(worldToScreenX(train.getX()))) - w / 2;
                int ny = (hovered != null ? hoveredPoint[1] : Math.round(worldToScreenY(train.getZ()))) - 18;
                g.fill(nx - 3, ny - 2, nx + w + 3, ny + 10, 0xCC15121C);
                g.drawString(font, name, nx, ny, 0xFFFFFFFF);
            }
        }

        g.disableScissor();
    }

    private void renderBottomStatus(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft.level == null || mouseY < mapTop() || mouseY >= mapBottom()) {
            g.drawString(font, Component.translatable("dvmap.screen.hint"), 8, mapBottom() + 7, 0xAAAAAA);
            return;
        }
        int wx = Mth.floor(screenToWorldX(mouseX));
        int wz = Mth.floor(screenToWorldZ(mouseY));
        int wy = MapData.sampleSurfaceHeight(minecraft.level, wx, wz);
        int cx = wx >> 4;
        int cz = wz >> 4;

        StringBuilder sb = new StringBuilder();
        sb.append("X: ").append(wx).append("  Y: ").append(wy).append("  Z: ").append(wz);
        sb.append("   [Chunk ").append(cx).append(", ").append(cz).append("]");

        Holder<Biome> biome = minecraft.level.getBiome(new BlockPos(wx, wy, wz));
        biome.unwrapKey().ifPresent(key -> {
            String langKey = "biome." + key.location().getNamespace() + "." + key.location().getPath();
            String biomeName = I18n.exists(langKey) ? I18n.get(langKey) : key.location().getPath();
            sb.append("   •   ").append(biomeName);
        });

        MutableComponent line = Component.literal(sb.toString()).withStyle(ChatFormatting.WHITE);
        ResourceKey<Level> dim = minecraft.level.dimension();
        for (MapOverlayHook hook : MapOverlayHook.HOOKS) {
            Component extra = hook.getChunkHoverText(dim, cx, cz);
            if (extra != null) {
                line.append(Component.literal("   •   ").withStyle(ChatFormatting.GRAY)).append(extra);
            }
        }
        g.drawString(font, line, 8, mapBottom() + 7, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (mouseY < mapTop() || mouseY >= mapBottom() || minecraft.level == null) return false;

        int wx = Mth.floor(screenToWorldX(mouseX));
        int wz = Mth.floor(screenToWorldZ(mouseY));
        int cx = wx >> 4;
        int cz = wz >> 4;
        ResourceKey<Level> dim = minecraft.level.dimension();

        // Check if clicking an existing waypoint
        if (button == 0) {
            Waypoint clickedWp = findWaypointAt(mouseX, mouseY);
            if (clickedWp != null) {
                minecraft.setScreen(new WaypointEditScreen(this, clickedWp, clickedWp.x, clickedWp.y, clickedWp.z, clickedWp.dimension));
                return true;
            }
        }

        // Let hooks (e.g., DV Team claim mode) handle canvas click if active
        for (MapOverlayHook hook : MapOverlayHook.HOOKS) {
            if (hook.onWorldMapClick(this, dim, cx, cz, wx, wz, button)) {
                lastDragChunkX = cx;
                lastDragChunkZ = cz;
                return true;
            }
        }

        if (button == 0 || button == 2) {
            panning = true;
            return true;
        }
        if (button == 1) {
            openMenu(mouseX, mouseY, wx, wz, dim, findWaypointAt(mouseX, mouseY));
            return true;
        }
        return false;
    }

    /** The right-click menu: actions for the waypoint under the cursor, or for the clicked block; then the hooks' lines. */
    private void openMenu(double mouseX, double mouseY, int wx, int wz, ResourceKey<Level> dim, Waypoint wp) {
        List<DVChoicePopup.Option<Runnable>> options = new ArrayList<>();
        boolean canTp = WaypointManager.canTeleport(minecraft);
        // The menu opens at the cursor: an invisible anchor there.
        DVButton anchor = new DVButton((int) mouseX, (int) mouseY, 1, 0, Component.empty(), b -> {});
        if (wp != null) {
            option(options, "dvmap.waypoint.edit_short",
                    () -> minecraft.setScreen(new WaypointEditScreen(this, wp, wp.x, wp.y, wp.z, wp.dimension)));
            if (canTp) option(options, "dvmap.waypoint.teleport", () -> {
                WaypointManager.teleportTo(minecraft, wp);
                minecraft.setScreen(null);
            });
            option(options, "dvmap.waypoint.share", () -> {
                WaypointShare.share(minecraft, wp);
                minecraft.setScreen(null);
            });
            option(options, "dvmap.menu.move", () -> DVChoicePopup.open(anchor, WaypointEditScreen.categoryOptions(), wp.category().id, id -> {
                wp.category = id;
                WaypointManager.save();
            }));
            option(options, "dvmap.waypoint.delete", () -> WaypointManager.remove(wp));
        } else {
            String dimId = dim.location().toString();
            // Where you stand (or under a ceiling, like the Nether): your own height. Elsewhere: the surface,
            // or your height for now if that chunk isn't loaded; a waypoint moves to the surface once it loads.
            boolean here = Math.abs(wx - minecraft.player.getBlockX()) <= 1 && Math.abs(wz - minecraft.player.getBlockZ()) <= 1;
            boolean surface = !here && !minecraft.level.dimensionType().hasCeiling();
            boolean loaded = MapData.isLoaded(minecraft.level, wx, wz);
            int wy = surface && loaded ? MapData.sampleSurfaceHeight(minecraft.level, wx, wz) : minecraft.player.getBlockY();
            option(options, "dvmap.menu.add_here", () -> {
                WaypointEditScreen edit = new WaypointEditScreen(this, null, wx, wy, wz, dimId);
                if (surface && !loaded) edit.autoHeight();
                minecraft.setScreen(edit);
            });
            if (canTp) option(options, "dvmap.menu.teleport_here", () -> {
                WaypointManager.teleport(minecraft, dimId, wx, String.valueOf(wy), wz);
                minecraft.setScreen(null);
            });
            option(options, "dvmap.menu.share_here", () -> {
                WaypointShare.shareLocation(minecraft, wx, wy, wz, dimId);
                minecraft.setScreen(null);
            });
            option(options, "dvmap.menu.copy", () -> minecraft.keyboardHandler.setClipboard(wx + " " + wy + " " + wz));
        }
        for (MapOverlayHook hook : MapOverlayHook.HOOKS) {
            hook.addMapMenuOptions(this, dim, wx, wz, wp, o -> options.add(new DVChoicePopup.Option<>(o.action(), o.name())));
        }
        DVChoicePopup.open(anchor, options, null, Runnable::run);
    }

    private static void option(List<DVChoicePopup.Option<Runnable>> options, String key, Runnable action) {
        options.add(new DVChoicePopup.Option<>(action, Component.translatable(key)));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning) {
            viewX -= dragX / zoom;
            viewZ -= dragY / zoom;
            return true;
        }
        if (mouseY >= mapTop() && mouseY < mapBottom() && minecraft.level != null) {
            int cx = Mth.floor(screenToWorldX(mouseX)) >> 4;
            int cz = Mth.floor(screenToWorldZ(mouseY)) >> 4;
            if (cx != lastDragChunkX || cz != lastDragChunkZ) {
                ResourceKey<Level> dim = minecraft.level.dimension();
                for (MapOverlayHook hook : MapOverlayHook.HOOKS) {
                    if (hook.onWorldMapDrag(this, dim, cx, cz, button)) {
                        lastDragChunkX = cx;
                        lastDragChunkZ = cz;
                        return true;
                    }
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panning = false;
        lastDragChunkX = Integer.MIN_VALUE;
        lastDragChunkZ = Integer.MIN_VALUE;
        for (MapOverlayHook hook : MapOverlayHook.HOOKS) {
            hook.onWorldMapRelease(this, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= mapTop() && mouseY < mapBottom()) {
            double beforeWx = screenToWorldX(mouseX);
            double beforeWz = screenToWorldZ(mouseY);
            float factor = delta > 0 ? 1.25f : 0.8f;
            zoom = Mth.clamp(zoom * factor, 0.25f, 8.0f);
            viewX = beforeWx - (mouseX - mapCenterX()) / zoom;
            viewZ = beforeWz - (mouseY - mapCenterY()) / zoom;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MapClient.OPEN_MAP_KEY != null && MapClient.OPEN_MAP_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Waypoint findWaypointAt(double mouseX, double mouseY) {
        if (minecraft.level == null) return null;
        for (Waypoint wp : WaypointManager.forDimension(minecraft.level.dimension())) {
            if (!wp.enabled) continue;
            float sx = worldToScreenX(wp.x + 0.5);
            float sy = worldToScreenY(wp.z + 0.5);
            if (isMouseNear(mouseX, mouseY, sx, sy, 6)) return wp;
        }
        return null;
    }

    private static boolean isMouseNear(double mx, double my, float sx, float sy, int radius) {
        return Math.abs(mx - sx) <= radius && Math.abs(my - sy) <= radius;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
