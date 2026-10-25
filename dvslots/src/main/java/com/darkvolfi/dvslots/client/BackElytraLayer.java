package com.darkvolfi.dvslots.client;

import com.darkvolfi.dvslots.slot.SlotData;
import com.darkvolfi.dvslots.slot.SlotType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;

/** Draws the back-slot elytra, unless the chest slot already shows one. */
public class BackElytraLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WINGS = new ResourceLocation("textures/entity/elytra.png");
    private final ElytraModel<AbstractClientPlayer> model;

    public BackElytraLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new ElytraModel<>(models.bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ElytraItem) return;
        SlotData data = SlotData.get(player);
        if (data == null) return;
        ItemStack back = data.get(SlotType.BACK);
        if (back.isEmpty()) return;

        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.125F);
        getParentModel().copyPropertiesTo(model);
        model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffers,
                RenderType.armorCutoutNoCull(texture(player)), false, back.hasFoil());
        model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    // Same choice as the vanilla elytra: skin's elytra, then cape, then the default wings.
    private static ResourceLocation texture(AbstractClientPlayer player) {
        if (player.isElytraLoaded() && player.getElytraTextureLocation() != null) return player.getElytraTextureLocation();
        if (player.isCapeLoaded() && player.getCloakTextureLocation() != null && player.isModelPartShown(PlayerModelPart.CAPE)) {
            return player.getCloakTextureLocation();
        }
        return WINGS;
    }
}
