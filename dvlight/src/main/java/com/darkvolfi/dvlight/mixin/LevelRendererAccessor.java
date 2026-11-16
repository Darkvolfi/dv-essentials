package com.darkvolfi.dvlight.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Marks a chunk section for a rebuild the way a block the player changes does: rebuilt at once on the next
 * frame instead of queued behind other work, so a moving light doesn't trail behind. Embeddium takes over
 * this same method.
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Invoker("setSectionDirty")
    void dvlight$setSectionDirty(int x, int y, int z, boolean playerChanged);
}
