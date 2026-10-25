package com.darkvolfi.dvslots.slot;

import com.darkvolfi.dvslots.DVSlots;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Right-clicking an item that fits an empty DV slot puts it there: an elytra goes to the back
 * slot instead of swapping out your chestplate, a saddle (used in the air) goes to the saddle slot.
 * Runs on both sides so the client doesn't also play the vanilla equip.
 */
@Mod.EventBusSubscriber(modid = DVSlots.MOD_ID)
public final class QuickEquip {
    private QuickEquip() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        SlotData data = SlotData.get(player);
        if (data == null) return;

        for (SlotType type : SlotType.values()) {
            if (!type.accepts(held) || !data.get(type).isEmpty()) continue;
            if (!player.level().isClientSide) {
                data.set(type, held.split(1));
                player.playSound(type == SlotType.BACK ? SoundEvents.ARMOR_EQUIP_ELYTRA : SoundEvents.ARMOR_EQUIP_GENERIC, 1f, 1f);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
            return;
        }
    }
}
