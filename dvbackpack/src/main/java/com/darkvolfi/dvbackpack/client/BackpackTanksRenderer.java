package com.darkvolfi.dvbackpack.client;

import com.darkvolfi.dvbackpack.BackpackBlock;
import com.darkvolfi.dvbackpack.BackpackBlockEntity;
import com.darkvolfi.dvbackpack.BackpackItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

/** The tanks beside a backpack set down on the ground (the backpack itself is a plain block model). */
public class BackpackTanksRenderer implements BlockEntityRenderer<BackpackBlockEntity> {
    public BackpackTanksRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BackpackBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        List<FluidStack> tanks = BackpackItem.tanks(entity.backpack());
        if (tanks.isEmpty()) return;
        pose.pushPose();
        // Turned like the block model: its blockstate turns a north-facing model by toYRot() around the centre.
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getBlockState().getValue(BackpackBlock.FACING).toYRot() + 180));
        pose.translate(-0.5, 0, -0.5);
        TankRender.render(pose, buffers, light, tanks, 0);
        pose.popPose();
    }
}
