package com.darkvolfi.dvbackpack.compat;

import com.darkvolfi.dvslots.slot.SlotData;
import com.darkvolfi.dvslots.slot.SlotType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** DV Slots integration; only touched after checking that DV Slots is loaded. */
public final class SlotsCompat {
    private SlotsCompat() {}

    public static ItemStack backpack(Player player) {
        SlotData data = SlotData.get(player);
        return data == null ? ItemStack.EMPTY : data.get(SlotType.BACKPACK);
    }

    public static ItemStack takeBackpack(Player player) {
        SlotData data = SlotData.get(player);
        if (data == null) return ItemStack.EMPTY;
        ItemStack stack = data.get(SlotType.BACKPACK);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        data.set(SlotType.BACKPACK, ItemStack.EMPTY);
        data.markDirty();
        return stack;
    }

    public static void changed(Player player) {
        SlotData data = SlotData.get(player);
        if (data != null) data.markDirty();
    }

    public static boolean equip(Player player, ItemStack backpack) {
        SlotData data = SlotData.get(player);
        if (data == null || !data.get(SlotType.BACKPACK).isEmpty()) return false;
        data.set(SlotType.BACKPACK, backpack);
        data.markDirty();
        return true;
    }
}
