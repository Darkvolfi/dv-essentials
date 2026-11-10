package com.darkvolfi.dvfood.client;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Health points (half hearts) that the effects of a held potion or food would add or remove,
 * following the vanilla tick rules: Regeneration every 50>>amp ticks, Poison 25>>amp, Wither 40>>amp.
 */
final class EffectPreview {
    final int gain;
    final int loss;
    final int absorption;

    private EffectPreview(int gain, int loss, int absorption) {
        this.gain = gain;
        this.loss = loss;
        this.absorption = absorption;
    }

    /** Collects effects from the held potion (drunk, splash or lingering) and guaranteed food effects. */
    static EffectPreview of(LocalPlayer player, FoodProperties heldFood, float health, float maxHealth) {
        List<MobEffectInstance> effects = new ArrayList<>();
        double instantScale = 1.0;
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof PotionItem) {
                boolean lingering = stack.getItem() instanceof LingeringPotionItem;
                // A lingering cloud applies a quarter of the duration and half of instant effects.
                for (MobEffectInstance e : PotionUtils.getMobEffects(stack)) {
                    effects.add(lingering
                            ? new MobEffectInstance(e.getEffect(), Math.max(e.getDuration() / 4, 1), e.getAmplifier())
                            : e);
                }
                if (lingering) instantScale = 0.5;
                break;
            }
        }
        if (heldFood != null) {
            for (Pair<MobEffectInstance, Float> pair : heldFood.getEffects()) {
                if (pair.getSecond() >= 1.0f) effects.add(pair.getFirst());
            }
        }

        int gain = 0, loss = 0, absorption = 0, poison = 0;
        for (MobEffectInstance e : effects) {
            int amp = e.getAmplifier();
            if (e.getEffect() == MobEffects.HEAL) {
                gain += (int) (instantScale * (4 << amp) + 0.5);
            } else if (e.getEffect() == MobEffects.HARM) {
                loss += (int) (instantScale * (6 << amp) + 0.5);
            } else if (e.getEffect() == MobEffects.ABSORPTION) {
                absorption += 4 * (amp + 1);
            } else if (e.getDuration() > 0) {
                if (e.getEffect() == MobEffects.REGENERATION) gain += ticks(e, 50);
                else if (e.getEffect() == MobEffects.POISON) poison += ticks(e, 25);
                else if (e.getEffect() == MobEffects.WITHER) loss += ticks(e, 40);
            }
        }
        // Poison cannot take the last half heart.
        loss += Math.min(poison, Math.max((int) Math.ceil(health) - loss - 1, 0));
        gain = Math.min(gain, Math.max((int) Math.ceil(maxHealth - health), 0));
        loss = Math.min(loss, (int) Math.ceil(health));
        return new EffectPreview(gain, loss, absorption);
    }

    /** Number of ticks in the effect's duration on which it fires, as in MobEffect.isDurationEffectTick. */
    private static int ticks(MobEffectInstance e, int base) {
        int interval = base >> e.getAmplifier();
        return interval > 0 ? e.getDuration() / interval : e.getDuration();
    }
}
