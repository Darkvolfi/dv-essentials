package com.darkvolfi.dvfood.client;

import com.darkvolfi.dvfood.DVFood;
import com.darkvolfi.dvfood.FoodConfig;
import com.darkvolfi.dvlib.client.EffectText;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Lists the effects a food gives right under its name, like a potion: "Regeneration II (0:05)",
 * blue for good effects, red for bad ones, with the chance when it isn't certain ("Hunger (0:30) 80%").
 */
@Mod.EventBusSubscriber(modid = DVFood.MOD_ID, value = Dist.CLIENT)
public final class FoodEffectsTooltip {
    private FoodEffectsTooltip() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!FoodConfig.SHOW_EFFECTS.get()) return;
        FoodProperties props = event.getItemStack().getFoodProperties(event.getEntity());
        if (props == null || props.getEffects().isEmpty()) return;

        List<Component> lines = event.getToolTip();
        int at = Math.min(1, lines.size());
        for (Pair<MobEffectInstance, Float> pair : props.getEffects()) {
            lines.add(at++, EffectText.line(pair.getFirst(), pair.getSecond()));
        }
    }
}
