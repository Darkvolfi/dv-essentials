package com.darkvolfi.dvsouls.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Draws what lies in a soul machine's slots as the items themselves (like a potion in an item frame), so a jar's soul
 * has its creature's colours: each slot at its place in the model, turned with the block.
 */
public class MachineRenderer<T extends BlockEntity & Container> implements BlockEntityRenderer<T> {
    /**
     * Where a slot's item is drawn, in the model's pixels with its front to the north: the item's middle, its size (a
     * pixel of the item's texture is {@code scale} of the model's) and how far it is turned (degrees, about the front).
     */
    public record Place(int slot, float x, float y, float z, float scale, float roll) {}

    private final Place[] places;

    private MachineRenderer(Place[] places) {
        this.places = places;
    }

    public static <T extends BlockEntity & Container> BlockEntityRendererProvider<T> of(Place... places) {
        return context -> new MachineRenderer<>(places);
    }

    @Override
    public void render(T machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float facing = machine.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot();
        for (Place place : places) {
            ItemStack stack = machine.getItem(place.slot());
            if (stack.isEmpty()) continue;
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - facing));
            pose.translate(place.x() / 16 - 0.5, place.y() / 16, place.z() / 16 - 0.5);
            pose.mulPose(Axis.ZP.rotationDegrees(place.roll()));
            pose.scale(place.scale(), place.scale(), place.scale());
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, machine.getLevel(), 0);
            pose.popPose();
        }
    }
}
