package com.darkvolfi.dvdark.mixin;

import com.darkvolfi.dvdark.client.DarkTextures;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Recolours GUI textures (and turns the title panorama to night) right after they are read, before they are uploaded to the GPU. */
@Mixin(SimpleTexture.class)
abstract class SimpleTextureMixin {
    @Shadow @Final protected ResourceLocation location;

    @ModifyVariable(method = "load", at = @At("STORE"))
    private NativeImage dvdark$darken(NativeImage image) {
        if (DarkTextures.shouldDarken(location)) DarkTextures.darken(image);
        int face = DarkTextures.nightFace(location);
        if (face >= 0) DarkTextures.night(image, face);
        return image;
    }
}
