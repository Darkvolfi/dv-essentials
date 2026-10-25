package com.darkvolfi.dvdark.mixin;

import com.darkvolfi.dvdark.client.DarkClient;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The vanilla drawString overloads that the others funnel into; lighten dark label text on dark panels.
 * (Forge's float overloads have no obfuscated names, and vanilla screens call these.)
 */
@Mixin(GuiGraphics.class)
abstract class GuiGraphicsMixin {
    @ModifyVariable(method = {
            "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
            "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int dvdark$lightLabels(int color) {
        return DarkClient.adjustTextColor(color);
    }
}
