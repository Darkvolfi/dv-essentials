package com.darkvolfi.dvgrave.client;

import com.darkvolfi.dvgrave.GraveBlock;
import com.darkvolfi.dvgrave.GraveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/** The owner's name carved on the front of the stone. */
public class GraveRenderer implements BlockEntityRenderer<GraveBlockEntity> {
    /** The stone's front face is 10 px wide; the name keeps a little margin. */
    private static final float FACE_WIDTH = 9f / 16f;
    private static final float SCALE = 1f / 96f;

    private final Font font;

    public GraveRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(GraveBlockEntity grave, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        String name = grave.ownerName();
        if (name.isEmpty()) return;
        Direction facing = grave.getBlockState().getValue(GraveBlock.FACING);
        int width = font.width(name);
        float scale = Math.min(SCALE, FACE_WIDTH / width);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, 10.5 / 16, 2.0 / 16 + 0.005); // just in front of the south face (z = 10 px)
        pose.scale(scale, -scale, scale);
        font.drawInBatch(name, -width / 2f, -4, 0xFF2A2A2A, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
