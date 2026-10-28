package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvlib.client.DVTheme;
import com.darkvolfi.dvmap.DVMap;
import com.darkvolfi.dvmap.MapConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorldMapScreen extends Screen {
    public static final int TOP_BAR_HEIGHT = 28;
    private static final int STATUS_HEIGHT = 18;
    private static final int TOGGLE_HEIGHT = 18;

    private int bottomBarHeight = STATUS_HEIGHT + TOGGLE_HEIGHT + 4;
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
        int y = 4;

        // 1. Center on player
        addRenderableWidget(Button.builder(Component.translatable("dvmap.screen.center"), b -> {
            if (minecraft.player != null) {
                viewX = minecraft.player.getX();
                viewZ = minecraft.player.getZ();
            }
        }).bounds(x, y, 52, 20).build());
        x += 55;

        // 2. Layer mode
        addRenderableWidget(Button.builder(layerButtonLabel(), b -> {
            MapConfig.LayerMode[] modes = MapConfig.LayerMode.values();
            MapConfig.LayerMode next = modes[(MapConfig.LAYER_MODE.get().ordinal() + 1) % modes.length];
            MapConfig.LAYER_MODE.set(next);
            MapConfig.SPEC.save();
            b.setMessage(layerButtonLabel());
        }).bounds(x, y, 76, 20).build());
        x += 79;

        // 3. Chunk grid toggle
        addRenderableWidget(Button.builder(gridButtonLabel(), b -> {
            boolean next = !MapConfig.SHOW_CHUNK_GRID.get();
            MapConfig.SHOW_CHUNK_GRID.set(next);
            MapConfig.SPEC.save();
            b.setMessage(gridButtonLabel());
        }).bounds(x, y, 64, 20).build());
        x += 67;

        // 4. Waypoints list
        addRenderableWidget(Button.builder(Component.translatable("dvmap.screen.waypoints"), b ->
                minecraft.setScreen(new WaypointListScreen(this)))
                .bounds(x, y, 68, 20).build());
        x += 71;

        // 5. Quick add waypoint at player
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            if (minecraft.player != null && minecraft.level != null) {
                minecraft.setScreen(new WaypointEditScreen(this, null,
                        minecraft.player.getBlockX(), minecraft.player.getBlockY(), minecraft.player.getBlockZ(),
                        minecraft.level.dimension().location().toString()));
            }
        }).bounds(x, y, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("dvmap.waypoint.add")))
                .build());

        // Settings button on top-right
        addRenderableWidget(Button.builder(Component.literal("⚙"), b ->
                minecraft.setScreen(new DVConfigScreen(this, DVMap.MOD_ID, MapConfig.SPEC, null, null)))
                .bounds(width - 26, y, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("dvmap.screen.settings")))
                .build());

        // Allow hooks (e.g., DV Team) to add toolbar buttons
        for (MapOverlayHook hook : MapOverlayHook.HOOKS) {
            hook.onWorldMapInit(this, this::addRenderableWidget);
        }

        initToggles();
    }

    /** Quick on/off settings along the bottom; wraps to a second row on narrow screens. */
    private void initToggles() {
        toggleRefreshers.clear();
        boolean server = RemotePlayers.serverHasMod();
        Tooltip needServer = server ? null : Tooltip.create(Component.translatable("dvmap.bar.need_server"));
        List<Button> buttons = new ArrayList<>();
        buttons.add(toggle("dvmap.bar.hostiles", MapConfig.SHOW_HOSTILES, null, null));
        buttons.add(toggle("dvmap.bar.animals", MapConfig.SHOW_ANIMALS, null, null));
        buttons.add(toggle("dvmap.bar.players", MapConfig.SHOW_PLAYERS, null, null));
        buttons.add(toggle("dvmap.bar.day_night", MapConfig.DAY_NIGHT, null, null));
        buttons.add(toggle("dvmap.bar.hide_me", MapConfig.HIDE_ME, needServer, null));
        // Hidden on the map means the name is hidden too: shown as on and locked.
        buttons.add(toggle("dvmap.bar.hide_name", MapConfig.HIDE_MY_NAME, needServer, MapConfig.HIDE_ME));
        buttons.add(toggle("dvmap.bar.structures", MapConfig.SHOW_STRUCTURES, needServer, null));
        Component export = Component.translatable("dvmap.bar.export");
        buttons.add(Button.builder(export, b -> MapExport.export(minecraft))
                .size(font.width(export) + 12, TOGGLE_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("dvmap.bar.export.tooltip"))).build());

        int gap = 3;
        List<List<Button>> rows = new ArrayList<>();
        List<Button> row = new ArrayList<>();
        int rowWidth = 0;
        for (Button b : buttons) {
            if (!row.isEmpty() && rowWidth + gap + b.getWidth() > width - 12) {
                rows.add(row);
                row = new ArrayList<>();
                rowWidth = 0;
            }
            rowWidth += (row.isEmpty() ? 0 : gap) + b.getWidth();
            row.add(b);
        }
        rows.add(row);

        bottomBarHeight = STATUS_HEIGHT + rows.size() * (TOGGLE_HEIGHT + 2) + 2;
        int y = height - bottomBarHeight + STATUS_HEIGHT;
        for (List<Button> r : rows) {
            int total = -gap;
            for (Button b : r) total += b.getWidth() + gap;
            int x = (width - total) / 2;
            for (Button b : r) {
                b.setPosition(x, y);
                x += b.getWidth() + gap;
                addRenderableWidget(b);
            }
            y += TOGGLE_HEIGHT + 2;
        }
    }

    private Button toggle(String key, ForgeConfigSpec.BooleanValue value, Tooltip tooltip, ForgeConfigSpec.BooleanValue forcedBy) {
        Button button = Button.builder(toggleLabel(key, true), b -> {
            value.set(!value.get());
            MapConfig.SPEC.save();
            toggleRefreshers.forEach(Runnable::run);
        }).size(font.width(toggleLabel(key, true)) + 12, TOGGLE_HEIGHT).tooltip(tooltip).build();
        toggleRefreshers.add(() -> {
            boolean forced = forcedBy != null && forcedBy.get();
            button.active = !forced;
            button.setMessage(toggleLabel(key, forced || value.get()));
            if (forced) button.setTooltip(Tooltip.create(Component.translatable("dvmap.bar.name_auto")));
            else button.setTooltip(tooltip);
        });
        toggleRefreshers.get(toggleRefreshers.size() - 1).run();
        return button;
    }

    private static Component toggleLabel(String key, boolean on) {
        return Component.literal(on ? "✔ " : "✘ ").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                .append(Component.translatable(key).withStyle(on ? ChatFormatting.WHITE : ChatFormatting.GRAY));
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
        g.fill(0, 0, width, TOP_BAR_HEIGHT, DVTheme.shade(0xEE1C1A26));
        g.hLine(0, width, TOP_BAR_HEIGHT - 1, 0xFF3E3852);

        g.fill(0, mapBottom(), width, height, DVTheme.shade(0xEE1C1A26));
        g.hLine(0, width, mapBottom(), 0xFF3E3852);

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

        // 4. Hovered chunk subtle highlight
        if (mouseY >= top && mouseY < bottom) {
            int hcx = Mth.floor(screenToWorldX(mouseX)) >> 4;
            int hcz = Mth.floor(screenToWorldZ(mouseY)) >> 4;
            int sx = Math.round(worldToScreenX(hcx << 4));
            int sy = Math.round(worldToScreenY(hcz << 4));
            int sw = Math.max(2, Math.round(16 * zoom));
            g.renderOutline(sx, sy, sw, sw, 0x88FFFFFF);
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
        if (mouseY >= top && mouseY < bottom && hovered == null) {
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

        // 9. Name of the hovered creature or player, above everything
        if (hovered != null) {
            Component name = MapMarkers.nameOf(hovered);
            if (name != null) {
                int w = font.width(name);
                int nx = hoveredPoint[0] - w / 2;
                int ny = hoveredPoint[1] - 18;
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
            // Where you stand (or under a ceiling, like the Nether): your own height. Elsewhere: the surface,
            // or your height for now if that chunk isn't loaded; it moves to the surface once it loads.
            boolean here = Math.abs(wx - minecraft.player.getBlockX()) <= 1 && Math.abs(wz - minecraft.player.getBlockZ()) <= 1;
            boolean surface = !here && !minecraft.level.dimensionType().hasCeiling();
            boolean loaded = MapData.isLoaded(minecraft.level, wx, wz);
            int wy = surface && loaded ? MapData.sampleSurfaceHeight(minecraft.level, wx, wz) : minecraft.player.getBlockY();
            WaypointEditScreen edit = new WaypointEditScreen(this, null, wx, wy, wz, dim.location().toString());
            if (surface && !loaded) edit.autoHeight();
            minecraft.setScreen(edit);
            return true;
        }
        return false;
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
