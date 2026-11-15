package com.darkvolfi.dvslots.mixin;

import com.darkvolfi.dvslots.slot.SlotData;
import com.darkvolfi.dvslots.slot.SlotType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.world.item.ElytraItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Like an elytra in the chest slot, one on the back hides the cape: the back elytra already wears the cape's texture.
 * require = 0 so a clash with another mod only brings the overlap back instead of stopping the game.
 */
@Mixin(CapeLayer.class)
abstract class CapeLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void dvslots$hideUnderBackElytra(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                             float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                             float netHeadYaw, float headPitch, CallbackInfo ci) {
        SlotData data = SlotData.get(player);
        if (data != null && data.get(SlotType.BACK).getItem() instanceof ElytraItem) ci.cancel();
    }
}
