package com.darkvolfi.dvbackpack;

import com.darkvolfi.dvbackpack.compat.CuriosCompat;
import com.darkvolfi.dvbackpack.compat.SlotsCompat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * The backpack a player wears: DV Slots' backpack slot first, then the chest slot, then a Curios back slot.
 * Works on both sides (the client draws it on the player's back).
 */
public final class WornBackpack {
    private static final boolean SLOTS = ModList.get().isLoaded("dvslots");
    private static final boolean CURIOS = ModList.get().isLoaded("curios");

    private WornBackpack() {}

    public static ItemStack find(Player player) {
        if (SLOTS) {
            ItemStack stack = SlotsCompat.backpack(player);
            if (stack.getItem() instanceof BackpackItem) return stack;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof BackpackItem) return chest;
        if (CURIOS) return CuriosCompat.backpack(player);
        return ItemStack.EMPTY;
    }

    /** Takes the worn backpack off and returns it (empty if none was worn). */
    public static ItemStack takeOff(Player player) {
        if (SLOTS) {
            ItemStack stack = SlotsCompat.takeBackpack(player);
            if (!stack.isEmpty()) return stack;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof BackpackItem) {
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            return chest;
        }
        if (CURIOS) return CuriosCompat.takeBackpack(player);
        return ItemStack.EMPTY;
    }

    /** After changing the worn backpack's NBT in place: DV Slots only sends its slots when told they changed. */
    public static void changed(Player player) {
        if (SLOTS) SlotsCompat.changed(player);
    }

    /** Puts a backpack on, where there is room: DV Slots first, then the chest slot. */
    public static boolean equip(Player player, ItemStack backpack) {
        if (SLOTS && SlotsCompat.equip(player, backpack)) return true;
        if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            player.setItemSlot(EquipmentSlot.CHEST, backpack);
            return true;
        }
        return false;
    }
}
