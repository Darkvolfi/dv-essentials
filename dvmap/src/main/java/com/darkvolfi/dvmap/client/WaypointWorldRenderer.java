package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvmap.DVMap;
import com.darkvolfi.dvmap.MapConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = DVMap.MOD_ID, value = Dist.CLIENT)
public final class WaypointWorldRenderer {
    private WaypointWorldRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!MapConfig.SHOW_WAYPOINTS_WORLD.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        List<Waypoint> waypoints = WaypointManager.forDimension(mc.level.dimension());
        if (waypoints.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        // 1. Draw vertical light beams for waypoints within 256 blocks
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        for (Waypoint wp : waypoints) {
            if (!wp.enabled) continue;
            double dx = (wp.x + 0.5) - camPos.x;
            double dz = (wp.z + 0.5) - camPos.z;
            double horizDistSq = dx * dx + dz * dz;
            if (horizDistSq < 9.0 || horizDistSq > 256.0 * 256.0) continue;

            float r = ((wp.color >> 16) & 0xFF) / 255.0f;
            float g = ((wp.color >> 8) & 0xFF) / 255.0f;
            float b = (wp.color & 0xFF) / 255.0f;
            float alpha = 0.28f;

            poseStack.pushPose();
            poseStack.translate(dx, -camPos.y, dz);
            Matrix4f mat = poseStack.last().pose();
            float minY = mc.level.getMinBuildHeight();
            float maxY = mc.level.getMaxBuildHeight();
            float half = 0.22f;

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.getBuilder();
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            addQuad(buf, mat, -half, minY, -half, half, maxY, -half, r, g, b, alpha);
            addQuad(buf, mat, half, minY, -half, half, maxY, half, r, g, b, alpha);
            addQuad(buf, mat, half, minY, half, -half, maxY, half, r, g, b, alpha);
            addQuad(buf, mat, -half, minY, half, -half, maxY, -half, r, g, b, alpha);
            tess.end();
            poseStack.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // The icons are drawn on the HUD so terrain never hides them; this frame's camera is kept to project them.
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3 cameraPos;

    @SubscribeEvent
    public static void captureCamera(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        VIEW.set(event.getPoseStack().last().pose());
        PROJECTION.set(event.getProjectionMatrix());
        cameraPos = event.getCamera().getPosition();
    }

    @Mod.EventBusSubscriber(modid = DVMap.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerOverlay(RegisterGuiOverlaysEvent event) {
            event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "waypoint_icons", WaypointWorldRenderer::renderIcons);
        }
    }

    /** Small shape per waypoint, visible through terrain; looking at one shows its name and distance. */
    private static void renderIcons(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!MapConfig.SHOW_WAYPOINTS_WORLD.get() || cameraPos == null || mc.level == null || mc.player == null
                || mc.options.hideGui) return;

        Waypoint aimed = null;
        int[] aimedPoint = null;
        double aimedOff = 7.0;
        for (Waypoint wp : WaypointManager.forDimension(mc.level.dimension())) {
            if (!wp.enabled) continue;
            double dx = wp.x + 0.5 - cameraPos.x;
            double dy = wp.y + 1.2 - cameraPos.y;
            double dz = wp.z + 0.5 - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz < 4.0) continue;

            Vector4f clip = new Vector4f((float) dx, (float) dy, (float) dz, 1.0f);
            VIEW.transform(clip);
            PROJECTION.transform(clip);
            if (clip.w() <= 0.05f) continue; // behind the camera
            float ndcX = clip.x() / clip.w();
            float ndcY = clip.y() / clip.w();
            if (Math.abs(ndcX) > 1.05f || Math.abs(ndcY) > 1.05f) continue;

            int sx = Math.round((ndcX + 1.0f) * 0.5f * width);
            int sy = Math.round((1.0f - ndcY) * 0.5f * height);
            wp.shape().draw(g, sx, sy, 1, wp.color);

            double off = Math.hypot(sx - width / 2.0, sy - height / 2.0);
            if (off < aimedOff) {
                aimedOff = off;
                aimed = wp;
                aimedPoint = new int[]{sx, sy};
            }
        }

        if (aimed != null) {
            double dist = Math.sqrt(mc.player.distanceToSqr(aimed.x + 0.5, aimed.y, aimed.z + 0.5));
            Component text = aimed.label().copy().append(" · " + Math.round(dist) + "m");
            int w = mc.font.width(text);
            int lx = aimedPoint[0] - w / 2;
            int ly = aimedPoint[1] - 17;
            g.fill(lx - 3, ly - 2, lx + w + 3, ly + 10, 0xAA15121C);
            g.drawString(mc.font, text, lx, ly, 0xFF000000 | aimed.color);
        }
    }

    private static void addQuad(BufferBuilder buf, Matrix4f mat,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(mat, x2, y1, z2).color(r, g, b, a).endVertex();
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
        buf.vertex(mat, x1, y2, z1).color(r, g, b, a).endVertex();
    }
}
