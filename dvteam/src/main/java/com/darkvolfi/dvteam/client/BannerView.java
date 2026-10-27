package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvteam.data.BannerData;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.world.level.block.entity.BannerBlockEntity;

/** Draws a clan flag flat in a GUI, the way the loom shows its result. 20x40 pixels at scale 1. */
public final class BannerView {
    private static ModelPart flag;

    private BannerView() {}

    /** Top-left at (left, top), {@code height} pixels tall (width is half of it). */
    public static void draw(GuiGraphics g, BannerData banner, int left, int top, int height) {
        if (flag == null) flag = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.BANNER).getChild("flag");
        float k = height / 40.0f;
        PoseStack pose = g.pose();
        pose.pushPose();
        // The flag box spans x -10..10 and y -32..8 model pixels once moved up by 32.
        pose.translate(left + 10 * k, top + 32 * k, 100);
        pose.scale(16 * k, 16 * k, -0.67f);
        flag.xRot = 0.0f;
        flag.y = -32.0f;
        Lighting.setupForFlatItems();
        BannerRenderer.renderPatterns(pose, g.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY, flag,
                ModelBakery.BANNER_BASE, true, BannerBlockEntity.createPatterns(banner.base, banner.patternsTag()));
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }
}
