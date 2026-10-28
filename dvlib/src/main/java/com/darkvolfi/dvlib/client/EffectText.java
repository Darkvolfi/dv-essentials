package com.darkvolfi.dvlib.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

/** A potion-style effect line: "Regeneration II (0:05)", blue for good effects, red for bad ones. */
public final class EffectText {
    private EffectText() {}

    /** {@code chance} below 1 is appended as a percentage ("Hunger (0:30) 80%"). */
    public static Component line(MobEffectInstance instance, float chance) {
        MobEffect effect = instance.getEffect();
        MutableComponent text = Component.translatable(effect.getDescriptionId());
        if (instance.getAmplifier() > 0) {
            text = Component.translatable("potion.withAmplifier", text,
                    Component.translatable("potion.potency." + instance.getAmplifier()));
        }
        if (instance.getDuration() > 20) {
            text = Component.translatable("potion.withDuration", text, MobEffectUtil.formatDuration(instance, 1.0f));
        }
        if (chance < 1.0f) {
            text = text.append(" " + Math.round(chance * 100) + "%");
        }
        return text.withStyle(effect.getCategory().getTooltipFormatting());
    }
}
