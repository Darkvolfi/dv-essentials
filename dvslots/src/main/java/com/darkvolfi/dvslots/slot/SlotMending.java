package com.darkvolfi.dvslots.slot;

import com.darkvolfi.dvslots.DVSlots;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Mending for what is worn in the DV slots (the elytra on your back). The game only mends the items it equips, so
 * before it handles an orb, one damaged Mending item is picked among the equipped ones and the DV slots alike. If it
 * is a DV slot item, the orb repairs it here and the game gets the experience that is left.
 */
@Mod.EventBusSubscriber(modid = DVSlots.MOD_ID)
public final class SlotMending {
    /** ExperienceOrb.value (SRG name, so it is found outside the dev environment too). */
    private static final String ORB_VALUE = "f_20770_";

    private SlotMending() {}

    @SubscribeEvent
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        SlotData data = SlotData.get(player);
        if (data == null) return;
        List<ItemStack> ours = new ArrayList<>();
        for (SlotType type : SlotType.values()) {
            ItemStack stack = data.get(type);
            if (mendable(stack)) ours.add(stack);
        }
        if (ours.isEmpty()) return;

        // Same chance for every candidate, as the game's own random pick among equipped items.
        int theirs = 0;
        for (ItemStack stack : Enchantments.MENDING.getSlotItems(player).values()) {
            if (mendable(stack)) theirs++;
        }
        int pick = player.getRandom().nextInt(ours.size() + theirs);
        if (pick >= ours.size()) return;

        ExperienceOrb orb = event.getOrb();
        ItemStack stack = ours.get(pick);
        int value = orb.getValue();
        int repair = Math.min((int) (value * stack.getXpRepairRatio()), stack.getDamageValue());
        if (repair <= 0) return;
        stack.setDamageValue(stack.getDamageValue() - repair);
        int used = Math.min(value, (int) Math.ceil(repair / stack.getXpRepairRatio()));
        try {
            ObfuscationReflectionHelper.setPrivateValue(ExperienceOrb.class, orb, value - used, ORB_VALUE);
        } catch (RuntimeException e) {
            // Field not found: the repair stays, the player also gets the full experience. Better than a crash.
        }
        data.markDirty();
    }

    private static boolean mendable(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamaged() && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, stack) > 0;
    }
}
