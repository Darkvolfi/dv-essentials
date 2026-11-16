package com.darkvolfi.dvlight.mixin;

import com.darkvolfi.dvlight.client.DynamicLights;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Creatures, dropped items and the player's own hand are lit by moving lights too. */
@Mixin(value = EntityRenderer.class, priority = 900)
public abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private void dvlight$addMovingLight(T entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        int packed = cir.getReturnValueI();
        int lit = DynamicLights.apply(packed, BlockPos.containing(entity.getLightProbePosition(partialTick)));
        if (lit != packed) cir.setReturnValue(lit);
    }
}
