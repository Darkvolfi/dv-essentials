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
 * Right-clicking an elytra puts it in the back slot instead of swapping out your chestplate.
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
        if (data == null || !SlotType.BACK.accepts(held) || !data.get(SlotType.BACK).isEmpty()) return;

        if (!player.level().isClientSide) {
            data.set(SlotType.BACK, held.split(1));
            player.playSound(SoundEvents.ARMOR_EQUIP_ELYTRA, 1f, 1f);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
    }
}
