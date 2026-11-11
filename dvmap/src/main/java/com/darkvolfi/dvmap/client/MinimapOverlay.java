package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvlib.client.DVTheme;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvmap.DVMap;
import com.darkvolfi.dvmap.MapConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = DVMap.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MinimapOverlay {
    private MinimapOverlay() {}

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("minimap", MinimapOverlay::render);
    }

    /** How much of the top-right corner the minimap takes (0 when it is elsewhere or hidden). */
    static int topRightWidth() {
        Minecraft mc = Minecraft.getInstance();
        if (!MapConfig.MINIMAP_ENABLED.get() || MapConfig.MINIMAP_CORNER.get() != MapConfig.Corner.TOP_RIGHT) return 0;
        if (mc.level == null || mc.options.hideGui || mc.options.renderDebug || mc.screen instanceof WorldMapScreen) return 0;
        return MapConfig.MINIMAP_SIZE.get() / 2 * 2 + 8 + 4; // size + margin + frame and gap
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        if (!MapConfig.MINIMAP_ENABLED.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.options.renderDebug) return;
        if (mc.screen instanceof WorldMapScreen) return;

        MapData.ensureSession(mc);

        int halfSize = MapConfig.MINIMAP_SIZE.get() / 2;
        int size = halfSize * 2;
        int margin = 8;
        MapConfig.Corner corner = MapConfig.MINIMAP_CORNER.get();
        MapConfig.Shape shape = MapConfig.MINIMAP_SHAPE.get();
        boolean circle = shape == MapConfig.Shape.CIRCLE;

        List<InfoLine> infoLines = buildInfoLines(mc);
        int textBlockHeight = infoLines.size() * 10;

        int left;
        int top;
        switch (corner) {
            case TOP_LEFT -> {
                left = margin;
                top = margin;
            }
            case BOTTOM_RIGHT -> {
                left = screenWidth - size - margin;
                top = screenHeight - size - margin - textBlockHeight - 4;
            }
            case BOTTOM_LEFT -> {
                left = margin;
                top = screenHeight - size - margin - textBlockHeight - 4;
            }
            default -> {
                left = screenWidth - size - margin;
                top = margin;
            }
        }

        int cx = left + halfSize;
        int cy = top + halfSize;

        double px = Mth.lerp(partialTick, mc.player.xo, mc.player.getX());
        double pz = Mth.lerp(partialTick, mc.player.zo, mc.player.getZ());
        float yaw = mc.player.getViewYRot(partialTick);
        boolean rotate = MapConfig.ROTATE_WITH_PLAYER.get();
        float scale = switch (MapConfig.MINIMAP_ZOOM.get()) {
            case 1 -> 0.5f;
            case 3 -> 2.0f;
            case 4 -> 3.0f;
            case 5 -> 4.0f;
            case 6 -> 6.0f;
            default -> 1.0f;
        };

        // Frame & background
        int borderCol = DVTheme.shade(0xFF3A3548);
        int bgCol = 0xEE12101A;
        if (circle) {
            drawDisc(g, cx, cy, halfSize + 3, 0xFF1A1624);
            drawDisc(g, cx, cy, halfSize + 2, borderCol);
            drawDisc(g, cx, cy, halfSize, bgCol);
        } else {
            g.fill(left - 2, top - 2, left + size + 2, top + size + 2, borderCol);
            g.fill(left, top, left + size, top + size, bgCol);
            g.renderOutline(left - 2, top - 2, size + 4, size + 4, 0xFF1A1624);
        }

        boolean cave = MapData.isCaveModeActive(mc);
        double worldRadius = (halfSize / scale) * (rotate ? 1.42 : 1.05);
        int minWx = Mth.floor(px - worldRadius);
        int maxWx = Mth.ceil(px + worldRadius);
        int minWz = Mth.floor(pz - worldRadius);
        int maxWz = Mth.ceil(pz + worldRadius);

        int minRx = minWx >> MapData.REGION_SHIFT;
        int maxRx = maxWx >> MapData.REGION_SHIFT;
        int minRz = minWz >> MapData.REGION_SHIFT;
        int maxRz = maxWz >> MapData.REGION_SHIFT;

        g.enableScissor(left, top, left + size, top + size);
        // Round map: the corners of the square are closed off in the depth buffer, so nothing is drawn there.
        if (circle) maskCorners(g, cx, cy, halfSize, true);
        PoseStack pose = g.pose();
        pose.pushPose();
        if (rotate) {
            pose.translate(cx, cy, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(-yaw - 180.0f));
            pose.translate(-cx, -cy, 0);
        }

        // 1. Region tiles
        float light = cave ? 1.0f : MapData.dayLight(mc, partialTick);
        RenderSystem.setShaderColor(light, light, light, 1.0f);
        for (int rz = minRz; rz <= maxRz; rz++) {
            for (int rx = minRx; rx <= maxRx; rx++) {
                MapData.RegionTile tile = MapData.getTile(rx, rz, cave);
                if (tile == null) continue;
                float tileWorldX = rx * MapData.REGION_SIZE;
                float tileWorldZ = rz * MapData.REGION_SIZE;
                int sx = Math.round(cx + (float) (tileWorldX - px) * scale);
                int sy = Math.round(cy + (float) (tileWorldZ - pz) * scale);
                int drawSize = Math.round(MapData.REGION_SIZE * scale);
                g.blit(tile.location, sx, sy, drawSize, drawSize, 0, 0, MapData.REGION_SIZE, MapData.REGION_SIZE, MapData.REGION_SIZE, MapData.REGION_SIZE);
                if (!cave) {
                    MapData.drawGlow(g, tile, light, sx, sy, drawSize);
                    RenderSystem.setShaderColor(light, light, light, 1.0f);
                }
            }
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 2. Chunk grid
        if (MapConfig.SHOW_CHUNK_GRID.get()) {
            int minCx = minWx >> 4;
            int maxCx = maxWx >> 4;
            int minCz = minWz >> 4;
            int maxCz = maxWz >> 4;
            for (int chunkX = minCx; chunkX <= maxCx; chunkX++) {
                int sx = Math.round(cx + (float) ((chunkX << 4) - px) * scale);
                g.vLine(sx, top - halfSize, top + size + halfSize, 0x44FFFFFF);
            }
            for (int chunkZ = minCz; chunkZ <= maxCz; chunkZ++) {
                int sy = Math.round(cy + (float) ((chunkZ << 4) - pz) * scale);
                g.hLine(left - halfSize, left + size + halfSize, sy, 0x44FFFFFF);
            }
        }

        // 3. External overlays (DV Team claims)
        ResourceKey<Level> dim = mc.level.dimension();
        for (MapOverlayHook hook : MapOverlayHook.HOOKS) {
            hook.renderChunkOverlay(g, dim, minWx >> 4, minWz >> 4, maxWx >> 4, maxWz >> 4,
                    cx, cy, px, pz, scale, true);
        }

        // 3b. Create tracks and trains, turned with the map (the rotated square reaches ~1.42 half sizes out)
        float reach = halfSize * 1.42f;
        CreateTrains.View view = new CreateTrains.View(cx, cy, px, pz, scale, cx - reach, cy - reach, cx + reach, cy + reach);
        CreateTrains.renderTracks(g, view, mc.level, minWx >> 4, minWz >> 4, maxWx >> 4, maxWz >> 4, scale);
        CreateTrains.renderTrains(g, view, mc.level, partialTick, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2);

        pose.popPose();

        // 4. Structures and entity radar: upright icons at rotated positions
        renderStructures(mc, g, cx, cy, halfSize - 4, px, pz, yaw, rotate, scale, circle);
        renderEntityRadar(mc, g, partialTick, cx, cy, halfSize, px, pz, yaw, rotate, scale, circle);
        if (circle) maskCorners(g, cx, cy, halfSize, false);
        g.disableScissor();

        // 5. Waypoints on minimap (clipped to minimap rim)
        if (MapConfig.SHOW_WAYPOINTS_MINIMAP.get()) {
            renderMinimapWaypoints(mc, g, cx, cy, halfSize - 3, px, pz, yaw, rotate, scale, circle);
        }

        // 6. Player arrow at centre
        MapMarkers.drawArrow(g, cx, cy, rotate ? 0.0f : (yaw + 180.0f));

        // 7. Cardinal directions N / S / E / W
        drawCompassLabels(mc.font, g, cx, cy, halfSize - 3, rotate ? (-yaw - 180.0f) : 0.0f);

        // 8. Info lines below the minimap
        Font font = mc.font;
        // One left-aligned column, centred under the map as a whole but kept on screen, so icons line up.
        int textY = top + size + 4;
        int blockW = 0;
        for (InfoLine line : infoLines) blockW = Math.max(blockW, infoWidth(font, line));
        int textX = Mth.clamp(cx - blockW / 2, margin, screenWidth - blockW - margin);
        for (InfoLine line : infoLines) {
            drawInfoLine(g, font, line, textX, textY);
            textY += 10;
        }
    }

    private static int infoWidth(Font font, InfoLine line) {
        return line.icon()[0].length() * INFO_ICON_PX + INFO_ICON_GAP + font.width(line.text());
    }

    private static void renderEntityRadar(Minecraft mc, GuiGraphics g, float partialTick, int cx, int cy, int halfSize,
                                          double px, double pz, float yaw, boolean rotate, float scale, boolean circle) {
        double range = halfSize / scale * (rotate ? 1.42 : 1.0) + 2;
        List<MapMarkers.Marker> markers = MapMarkers.collect(mc, partialTick, px, pz, range);
        if (markers.isEmpty()) return;

        double rad = rotate ? Math.toRadians(-yaw - 180.0f) : 0.0;
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        int edge = halfSize - 4;
        List<int[]> points = new ArrayList<>(markers.size());
        for (MapMarkers.Marker m : markers) {
            double dx = (m.x() - px) * scale;
            double dz = (m.z() - pz) * scale;
            double rx = dx * cos - dz * sin;
            double rz = dx * sin + dz * cos;
            boolean inside = circle ? Math.hypot(rx, rz) <= edge : Math.max(Math.abs(rx), Math.abs(rz)) <= edge;
            points.add(inside ? new int[]{cx + (int) Math.round(rx), cy + (int) Math.round(rz)} : null);
        }
        MapMarkers.drawAll(g, markers, points, 8, Mth.lerp(partialTick, mc.player.yo, mc.player.getY()));
    }

    private static void renderStructures(Minecraft mc, GuiGraphics g, int cx, int cy, int edge,
                                         double px, double pz, float yaw, boolean rotate, float scale, boolean circle) {
        double rad = rotate ? Math.toRadians(-yaw - 180.0f) : 0.0;
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        for (StructureMarkers.Entry s : StructureMarkers.forDimension(mc.level.dimension())) {
            double dx = (s.x + 0.5 - px) * scale;
            double dz = (s.z + 0.5 - pz) * scale;
            double rx = dx * cos - dz * sin;
            double rz = dx * sin + dz * cos;
            boolean inside = circle ? Math.hypot(rx, rz) <= edge : Math.max(Math.abs(rx), Math.abs(rz)) <= edge;
            if (inside) StructureMarkers.draw(g, cx + (int) Math.round(rx), cy + (int) Math.round(rz) + 3, 1);
        }
    }

    private static void renderMinimapWaypoints(Minecraft mc, GuiGraphics g, int cx, int cy, int maxOffset,
                                               double px, double pz, float yaw, boolean rotate, float scale, boolean circle) {
        double rad = rotate ? Math.toRadians(-yaw - 180.0f) : 0.0;
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        for (Waypoint wp : WaypointManager.forDimension(mc.level.dimension())) {
            if (!wp.enabled) continue;
            double dx = (wp.x + 0.5 - px) * scale;
            double dz = (wp.z + 0.5 - pz) * scale;
            double rx = dx * cos - dz * sin;
            double rz = dx * sin + dz * cos;

            if (circle) {
                double dist = Math.hypot(rx, rz);
                if (dist > maxOffset) {
                    rx = rx / dist * maxOffset;
                    rz = rz / dist * maxOffset;
                }
            } else {
                double maxCoord = Math.max(Math.abs(rx), Math.abs(rz));
                if (maxCoord > maxOffset) {
                    rx = rx / maxCoord * maxOffset;
                    rz = rz / maxCoord * maxOffset;
                }
            }

            int sx = cx + (int) Math.round(rx);
            int sy = cy + (int) Math.round(rz);
            wp.shape().draw(g, sx, sy, 1, wp.color);
        }
    }

    private static void drawCompassLabels(Font font, GuiGraphics g, int cx, int cy, int radius, float rotDeg) {
        String[] dirs = {"N", "E", "S", "W"};
        int[] cols = {0xFFFF6666, 0xFFE0E0E0, 0xFFE0E0E0, 0xFFE0E0E0};
        for (int i = 0; i < 4; i++) {
            double rad = Math.toRadians(i * 90.0 - 90.0 + rotDeg);
            int lx = cx + (int) Math.round(Math.cos(rad) * radius) - 2;
            int ly = cy + (int) Math.round(Math.sin(rad) * radius) - 3;
            g.drawString(font, dirs[i], lx, ly, cols[i], true);
        }
    }

    /** One line under the minimap: a small icon in its own colour, then the value. */
    private record InfoLine(String[] icon, int iconColor, String text) {}

    private static final int INFO_ICON_PX = 1;
    private static final int INFO_ICON_GAP = 3;

    private static List<InfoLine> buildInfoLines(Minecraft mc) {
        List<InfoLine> lines = new ArrayList<>(4);
        BlockPos pos = mc.player.blockPosition();
        if (MapConfig.SHOW_COORDINATES.get()) {
            lines.add(new InfoLine(MapIcons.COORDS, 0xFFE6E6F0, pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        }
        if (MapConfig.SHOW_PORTAL_COORDINATES.get()) {
            // Nether blocks are 8 overworld blocks wide; the line shows where a portal would lead.
            // Purple portal: Nether coordinates; green portal: Overworld coordinates.
            ResourceKey<Level> dim = mc.level.dimension();
            if (dim == Level.OVERWORLD) {
                lines.add(new InfoLine(MapIcons.PORTAL, 0xFFB050F0, Math.floorDiv(pos.getX(), 8) + ", " + Math.floorDiv(pos.getZ(), 8)));
            } else if (dim == Level.NETHER) {
                lines.add(new InfoLine(MapIcons.PORTAL, 0xFF60D060, pos.getX() * 8 + ", " + pos.getZ() * 8));
            }
        }
        if (MapConfig.SHOW_BIOME.get()) {
            Holder<Biome> biomeHolder = mc.level.getBiome(pos);
            biomeHolder.unwrapKey().ifPresent(key -> {
                String langKey = "biome." + key.location().getNamespace() + "." + key.location().getPath();
                int grass = 0xFF000000 | biomeHolder.value().getFoliageColor();
                lines.add(new InfoLine(MapIcons.BIOME, grass, I18n.exists(langKey) ? I18n.get(langKey) : key.location().getPath()));
            });
        }
        if (MapConfig.SHOW_TIME.get()) {
            long dayTime = (mc.level.getDayTime() + 6000L) % 24000L;
            int hours = (int) (dayTime / 1000L);
            int minutes = (int) ((dayTime % 1000L) * 60L / 1000L);
            boolean day = hours >= 6 && hours < 18;
            lines.add(new InfoLine(day ? MapIcons.SUN : MapIcons.MOON, day ? 0xFFFFD040 : 0xFFB0C0FF,
                    String.format(Locale.ROOT, "%02d:%02d", hours, minutes)));
        }
        return lines;
    }

    private static void drawInfoLine(GuiGraphics g, Font font, InfoLine line, int x, int y) {
        int iconW = line.icon()[0].length() * INFO_ICON_PX;
        DVUi.icon(g, line.icon(), x + iconW / 2, y + 4, INFO_ICON_PX, line.iconColor());
        g.drawString(font, line.text(), x + iconW + INFO_ICON_GAP, y, 0xE6E6F0);
    }

    /** Half the width of a disc of radius {@code r} on the pixel row {@code dy} from its middle. */
    private static int discHalfWidth(int r, int dy) {
        double mid = dy + 0.5;
        return (int) Math.round(Math.sqrt(Math.max(0, r * r - mid * mid)));
    }

    private static void drawDisc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy < r; dy++) {
            int halfW = discHalfWidth(r, dy);
            g.fill(cx - halfW, cy + dy, cx + halfW, cy + dy + 1, color);
        }
    }

    /**
     * Closes ({@code on}) or opens again the part of the minimap's square outside its disc. It writes only depth: in
     * front of everything while the map is drawn, so the map's pixels there fail the depth test, and far behind
     * afterwards, so the rest of the HUD draws there as usual.
     */
    private static void maskCorners(GuiGraphics g, int cx, int cy, int r, boolean on) {
        g.flush();
        Matrix4f matrix = g.pose().last().pose();
        float z = on ? 900.0f : -900.0f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int dy = -r; dy < r; dy++) {
            int halfW = discHalfWidth(r, dy);
            int y0 = cy + dy, y1 = y0 + 1;
            maskQuad(buf, matrix, cx - r, y0, cx - halfW, y1, z);
            maskQuad(buf, matrix, cx + halfW, y0, cx + r, y1, z);
        }
        Tesselator.getInstance().end();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    private static void maskQuad(BufferBuilder buf, Matrix4f matrix, int x0, int y0, int x1, int y1, float z) {
        if (x1 <= x0) return;
        buf.vertex(matrix, x0, y0, z).color(0, 0, 0, 255).endVertex();
        buf.vertex(matrix, x0, y1, z).color(0, 0, 0, 255).endVertex();
        buf.vertex(matrix, x1, y1, z).color(0, 0, 0, 255).endVertex();
        buf.vertex(matrix, x1, y0, z).color(0, 0, 0, 255).endVertex();
    }
}
