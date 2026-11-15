package com.darkvolfi.dvskin.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/**
 * A standing player model drawn straight from the textures being picked, so it works on the title screen too,
 * with no player in a world. The cape (or elytra) hangs on its back.
 */
public final class SkinPreview {
    private static PlayerModel<LivingEntity> wide, slim;
    private static ElytraModel<LivingEntity> elytra;

    private SkinPreview() {}

    private static void bake() {
        if (wide != null) return;
        var models = Minecraft.getInstance().getEntityModels();
        wide = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        elytra = new ElytraModel<>(models.bakeLayer(ModelLayers.ELYTRA));
        // Models start out as babies (big head) until a renderer says otherwise
        wide.young = false;
        slim.young = false;
        elytra.young = false;
    }

    /**
     * Draws the model standing on (x, feetY), {@code scale} pixels per block, turned {@code yaw} degrees.
     * {@code cape} and {@code wings} may be null; the wings replace the cape when given.
     */
    public static void render(GuiGraphics g, int x, int feetY, float scale, float yaw,
                              ResourceLocation skin, boolean slimArms, ResourceLocation cape, ResourceLocation wings) {
        bake();
        PlayerModel<LivingEntity> model = slimArms ? slim : wide;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, feetY, 50);
        // Same frame as the inventory's player: model y points down the screen, its face toward the viewer
        pose.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(0, -1.501, 0);
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        int light = LightTexture.FULL_BRIGHT;

        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(skin)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        if (wings != null) {
            pose.pushPose();
            pose.translate(0, 0, 0.125);
            elytra.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(wings)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
        } else if (cape != null) {
            pose.pushPose();
            pose.translate(0, 0, 0.125);
            pose.mulPose(Axis.XP.rotationDegrees(6));
            pose.mulPose(Axis.YP.rotationDegrees(180));
            model.renderCloak(pose, buffers.getBuffer(RenderType.entitySolid(cape)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }
}
