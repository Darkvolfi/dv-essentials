package com.darkvolfi.dvslots.slot;

import com.darkvolfi.dvslots.DVSlots;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mounting a tamed, unsaddled horse (donkey, mule, camel...) puts the saddle from the
 * saddle slot on it; dismounting takes that saddle back. Saddles the horse already had are left alone.
 */
@Mod.EventBusSubscriber(modid = DVSlots.MOD_ID)
public final class AutoSaddle {
    private static final int SADDLE_SLOT = 400; // AbstractHorse.getSlot: the saddle
    private static final String TAG = DVSlots.MOD_ID + "_auto_saddle";

    private AutoSaddle() {}

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.getLevel().isClientSide || event.isCanceled()) return;
        if (!(event.getEntityMounting() instanceof Player player) || !(event.getEntityBeingMounted() instanceof AbstractHorse horse)) return;
        SlotData data = SlotData.get(player);
        if (data == null || !horse.isTamed() || !horse.isSaddleable()) return;

        if (event.isMounting()) {
            ItemStack saddle = data.get(SlotType.SADDLE);
            if (saddle.isEmpty() || horse.isSaddled()) return;
            if (horse.getSlot(SADDLE_SLOT).set(saddle.copy())) {
                data.set(SlotType.SADDLE, ItemStack.EMPTY);
                horse.getPersistentData().putBoolean(TAG, true);
                horse.level().playSound(null, horse, SoundEvents.HORSE_SADDLE, SoundSource.NEUTRAL, 0.5f, 1f);
            }
        } else if (horse.getPersistentData().getBoolean(TAG)) {
            SlotAccess slot = horse.getSlot(SADDLE_SLOT);
            ItemStack saddle = slot.get();
            if (saddle.isEmpty()) { // someone took it off meanwhile
                horse.getPersistentData().remove(TAG);
                return;
            }
            // Slot already filled: leave it on the horse (still tagged) rather than lose it.
            if (!data.get(SlotType.SADDLE).isEmpty()) return;
            if (slot.set(ItemStack.EMPTY)) {
                data.set(SlotType.SADDLE, saddle);
                horse.getPersistentData().remove(TAG);
            }
        }
    }
}
