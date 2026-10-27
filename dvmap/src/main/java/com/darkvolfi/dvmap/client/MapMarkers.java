package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvmap.MapConfig;
import com.darkvolfi.dvmap.network.PlayerPositionsPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Entity and player markers shared by the minimap and the world map. */
public final class MapMarkers {
    /** Faces are real model renders, so only the nearest ones get one; the rest are dots. */
    private static final int FACE_LIMIT = 40;
    /** Height difference (blocks) from which the above/below arrow shows. */
    private static final int LEVEL_THRESHOLD = 3;
    /** Entity types whose renderer failed once in the GUI; drawn as dots from then on. */
    private static final Set<EntityType<?>> BROKEN = new HashSet<>();

    public enum Kind {
        PLAYER(0x4FC3F7), HOSTILE(0xFF5A5A), NEUTRAL(0xFFC24D), PASSIVE(0x7CE08A), ITEM(0xFFD54F);

        public final int rgb;

        Kind(int rgb) {
            this.rgb = rgb;
        }
    }

    /**
     * @param entity null for players known only from the server
     * @param name   null when hidden or not a player
     */
    public record Marker(Kind kind, double x, double y, double z, Entity entity, ResourceLocation skin, String name) {}

    private MapMarkers() {}

    /** Markers within {@code range} blocks (horizontal) of (cx, cz), nearest to the player first. */
    public static List<Marker> collect(Minecraft mc, float partialTick, double cx, double cz, double range) {
        List<Marker> markers = new ArrayList<>();
        boolean players = MapConfig.SHOW_PLAYERS.get();
        boolean hostiles = MapConfig.SHOW_HOSTILES.get();
        boolean animals = MapConfig.SHOW_ANIMALS.get();
        boolean items = MapConfig.SHOW_ITEMS.get();
        Set<UUID> loadedPlayers = new HashSet<>();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive() || e.isInvisibleTo(mc.player)) continue;
            double x = Mth.lerp(partialTick, e.xo, e.getX());
            double z = Mth.lerp(partialTick, e.zo, e.getZ());
            if (Math.abs(x - cx) > range || Math.abs(z - cz) > range) continue;
            double y = Mth.lerp(partialTick, e.yo, e.getY());

            if (e instanceof AbstractClientPlayer p) {
                loadedPlayers.add(p.getUUID());
                if (!players || RemotePlayers.isHidden(p.getUUID())) continue;
                String name = RemotePlayers.isNameHidden(p.getUUID()) ? null : p.getGameProfile().getName();
                markers.add(new Marker(Kind.PLAYER, x, y, z, p, p.getSkinTextureLocation(), name));
            } else if (e instanceof ItemEntity) {
                if (items) markers.add(new Marker(Kind.ITEM, x, y, z, e, null, null));
            } else if (e instanceof LivingEntity living) {
                Kind kind = kindOf(living);
                if (kind == Kind.HOSTILE ? hostiles : animals) markers.add(new Marker(kind, x, y, z, e, null, null));
            }
        }

        if (players) {
            ClientPacketListener connection = mc.getConnection();
            for (PlayerPositionsPacket.Entry p : RemotePlayers.players()) {
                if (loadedPlayers.contains(p.id())) continue;
                if (Math.abs(p.x() - cx) > range || Math.abs(p.z() - cz) > range) continue;
                PlayerInfo info = connection != null ? connection.getPlayerInfo(p.id()) : null;
                ResourceLocation skin = info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(p.id());
                String name = p.nameHidden() ? null : p.name();
                markers.add(new Marker(Kind.PLAYER, p.x() + 0.5, p.y(), p.z() + 0.5, null, skin, name));
            }
        }

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        markers.sort(Comparator.comparingDouble(m -> (m.x - px) * (m.x - px) + (m.z - pz) * (m.z - pz)));
        return markers;
    }

    private static Kind kindOf(LivingEntity e) {
        if (e instanceof NeutralMob neutral) {
            return neutral.getRemainingPersistentAngerTime() > 0 ? Kind.HOSTILE : Kind.NEUTRAL;
        }
        if (e instanceof Enemy) return Kind.HOSTILE;
        return Kind.PASSIVE;
    }

    /** Draws markers (already sorted nearest first) centred on their screen points. */
    public static void drawAll(GuiGraphics g, List<Marker> markers, List<int[]> points, int size, double playerY) {
        boolean faces = MapConfig.MOB_FACES.get();
        // Far markers first, so near ones end up on top.
        for (int i = markers.size() - 1; i >= 0; i--) {
            int[] p = points.get(i);
            if (p != null) draw(g, markers.get(i), p[0], p[1], size, faces && i < FACE_LIMIT, playerY);
        }
    }

    public static void draw(GuiGraphics g, Marker m, int cx, int cy, int size, boolean face, double playerY) {
        int half = size / 2;
        int x0 = cx - half;
        int y0 = cy - half;
        int ring = 0x70000000 | m.kind.rgb;

        if (m.kind == Kind.PLAYER) {
            g.fill(x0 - 1, y0 - 1, x0 + size + 1, y0 + size + 1, 0xFF000000 | m.kind.rgb);
            PlayerFaceRenderer.draw(g, m.skin, x0, y0, size);
        } else if (m.kind == Kind.ITEM && m.entity instanceof ItemEntity item) {
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(x0, y0, 0);
            pose.scale(size / 16.0f, size / 16.0f, 1.0f);
            g.renderItem(item.getItem(), 0, 0);
            pose.popPose();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        } else if (face && m.entity instanceof LivingEntity living && !BROKEN.contains(living.getType())) {
            g.fill(x0, y0, x0 + size, y0 + size, 0x66000000);
            drawMobFace(g, living, cx, cy, size);
            drawRing(g, x0, y0, size, ring);
        } else {
            g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF000000);
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF000000 | m.kind.rgb);
        }

        int dy = (int) Math.round(m.y - playerY);
        if (dy >= LEVEL_THRESHOLD) drawTriangle(g, cx, y0 - 5, true, 0xE0000000 | m.kind.rgb);
        else if (dy <= -LEVEL_THRESHOLD) drawTriangle(g, cx, y0 + size + 2, false, 0xE0000000 | m.kind.rgb);
    }

    public static Component nameOf(Marker m) {
        if (m.kind == Kind.PLAYER) return m.name != null ? Component.literal(m.name) : null;
        return m.entity != null ? m.entity.getDisplayName() : null;
    }

    /** Thin rounded outline just outside the icon, tinted by kind. */
    private static void drawRing(GuiGraphics g, int x0, int y0, int size, int color) {
        g.hLine(x0, x0 + size - 1, y0 - 1, color);
        g.hLine(x0, x0 + size - 1, y0 + size, color);
        g.vLine(x0 - 1, y0 - 1, y0 + size, color);
        g.vLine(x0 + size, y0 - 1, y0 + size, color);
    }

    /** 5x3 pixel triangle; {@code up} points up, its top row at {@code top}. */
    private static void drawTriangle(GuiGraphics g, int cx, int top, boolean up, int color) {
        for (int row = 0; row < 3; row++) {
            int w = up ? row : 2 - row;
            g.fill(cx - w, top + row, cx + w + 1, top + row + 1, color);
        }
    }

    private static void drawMobFace(GuiGraphics g, LivingEntity e, int cx, int cy, int size) {
        // A sleeping (or swimming, crouching…) mob has a tiny box and lies sideways, which zoomed into one
        // corner of its face. Stand it up just for this render; client-side only, restored below.
        Pose pose = e.getPose();
        if (pose != Pose.STANDING) e.setPose(Pose.STANDING);
        try {
            drawStandingFace(g, e, cx, cy, size);
        } finally {
            if (pose != Pose.STANDING) e.setPose(pose);
        }
    }

    private static void drawStandingFace(GuiGraphics g, LivingEntity e, int cx, int cy, int size) {
        // Scale so the whole head fits with a little margin; big blob-like mobs (slimes, ghasts) show whole.
        float head = Mth.clamp(Math.min(e.getBbWidth(), e.getBbHeight()) * 1.15f, 0.4f, 4.0f);
        int scale = Math.max(1, Math.round(size / head));
        int half = size / 2;
        // Eyes sit in the lower half of most heads (villagers especially): aim between the eyes and the top.
        float centerY = (e.getEyeHeight() + e.getBbHeight()) / 2.0f;

        float bodyRot = e.yBodyRot, bodyRotO = e.yBodyRotO, headRot = e.yHeadRot, headRotO = e.yHeadRotO;
        float xRot = e.getXRot(), xRotO = e.xRotO, yRot = e.getYRot();
        // Face the viewer, like the inventory preview.
        e.yBodyRot = e.yBodyRotO = e.yHeadRot = e.yHeadRotO = 180.0f;
        e.setXRot(0.0f);
        e.xRotO = 0.0f;
        e.setYRot(180.0f);
        g.enableScissor(cx - half, cy - half, cx - half + size, cy - half + size);
        try {
            InventoryScreen.renderEntityInInventory(g, cx, cy + Math.round(centerY * scale), scale,
                    new Quaternionf().rotateZ((float) Math.PI), null, e);
        } catch (RuntimeException ex) {
            BROKEN.add(e.getType());
        } finally {
            g.disableScissor();
            e.yBodyRot = bodyRot;
            e.yBodyRotO = bodyRotO;
            e.yHeadRot = headRot;
            e.yHeadRotO = headRotO;
            e.setXRot(xRot);
            e.xRotO = xRotO;
            e.setYRot(yRot);
        }
        // Each face renders in depth; clear so the next icon is not hidden behind this one.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** The local player: a white pixel arrow with a dark outline, pointing at {@code angleDeg} (0 = up). */
    public static void drawArrow(GuiGraphics g, int cx, int cy, float angleDeg) {
        // Half-widths per row, top to bottom; the last two rows form the notch.
        int[] widths = {0, 1, 1, 2, 2, 3, 3};
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx + 0.5f, cy + 0.5f, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angleDeg));
        int top = -5;
        for (int pass = 0; pass < 2; pass++) {
            int grow = pass == 0 ? 1 : 0;
            int color = pass == 0 ? 0xFF15121C : 0xFFFFFFFF;
            for (int r = 0; r < widths.length; r++) {
                int w = widths[r] + grow;
                g.fill(-w, top + r - grow, w + 1, top + r + 1, color);
            }
            // Notch: two feet with a gap in the middle.
            g.fill(-4 - grow, top + 7, -1 + grow, top + 8 + grow, color);
            g.fill(2 - grow, top + 7, 5 + grow, top + 8 + grow, color);
        }
        // Shade the right half for a little depth.
        for (int r = 1; r < widths.length; r++) g.fill(1, top + r, widths[r] + 1, top + r + 1, 0xFFD6D3E0);
        g.fill(2, top + 7, 5, top + 8, 0xFFD6D3E0);
        pose.popPose();
    }
}
