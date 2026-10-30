package com.darkvolfi.dvsouls.client;

import com.darkvolfi.dvsouls.Corrupted;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Draws a corrupted creature as the creature it was (its puppet, drawn by that creature's own renderer), every colour
 * darkened towards a dried-blood red. Its red eyes and smoke are particles, from the entity itself.
 */
final class CorruptedRenderer extends EntityRenderer<Corrupted> {
    CorruptedRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.5f;
    }

    @Override
    public void render(Corrupted corrupted, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        LivingEntity puppet = corrupted.puppet();
        if (puppet != null) {
            entityRenderDispatcher.getRenderer(puppet).render(puppet, yaw, partialTick, pose, type -> new Tint(buffers.getBuffer(type)), light);
        }
        super.render(corrupted, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Corrupted corrupted) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    /** Passes vertices on with their colour darkened and reddened. */
    private record Tint(VertexConsumer inner) implements VertexConsumer {
        private static final float RED = 0.55f, GREEN = 0.2f, BLUE = 0.22f;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            inner.color(Math.round(red * RED), Math.round(green * GREEN), Math.round(blue * BLUE), alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            inner.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            inner.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            inner.defaultColor(Math.round(red * RED), Math.round(green * GREEN), Math.round(blue * BLUE), alpha);
        }

        @Override
        public void unsetDefaultColor() {
            inner.unsetDefaultColor();
        }
    }
}
