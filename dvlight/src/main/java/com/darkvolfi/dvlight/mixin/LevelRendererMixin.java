package com.darkvolfi.dvlight.mixin;

import com.darkvolfi.dvlight.client.DynamicLights;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block light lookup for drawing goes through here: vanilla chunk meshes, Embeddium's chunk meshes
 * (its LightDataAccess calls this method), block entities and particles.
 */
// Applied before other mods (default priority 1000): another dynamic light mod (Sodium Dynamic Lights) sets
// the result at the end of this method too, and whichever runs first decides.
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class LevelRendererMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void dvlight$addMovingLight(BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int packed = cir.getReturnValueI();
        int lit = DynamicLights.apply(packed, pos);
        if (lit != packed) cir.setReturnValue(lit);
    }
}
