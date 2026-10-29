package com.darkvolfi.dvdark.mixin;

import com.darkvolfi.dvdark.client.DarkTextures;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Recolours the sprites of GUI atlases (JEI's, for one) before they are stitched: a sprite's image doesn't know
 * which atlas it is for until here, and the mipmaps are made from it during stitching.
 */
@Mixin(SpriteLoader.class)
abstract class SpriteLoaderMixin {
    @Shadow @Final private ResourceLocation location;

    @Inject(method = "stitch", at = @At("HEAD"))
    private void dvdark$darken(List<SpriteContents> sprites, int mipLevel, Executor executor,
                               CallbackInfoReturnable<SpriteLoader.Preparations> info) {
        if (!DarkTextures.shouldDarkenAtlas(location)) return;
        for (SpriteContents sprite : sprites) DarkTextures.darken(sprite.getOriginalImage());
    }
}
