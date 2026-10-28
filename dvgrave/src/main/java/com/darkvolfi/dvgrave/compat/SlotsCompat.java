package com.darkvolfi.dvgrave.compat;

import com.darkvolfi.dvgrave.GraveItem;
import com.darkvolfi.dvslots.slot.SlotData;
import com.darkvolfi.dvslots.slot.SlotType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** DV Slots integration; only touched after checking that DV Slots is loaded. */
public final class SlotsCompat {
    private SlotsCompat() {}

    public static void snapshot(ServerPlayer player, List<GraveItem> out) {
        SlotData data = SlotData.get(player);
        if (data == null) return;
        for (SlotType type : SlotType.values()) {
            ItemStack stack = data.get(type);
            if (!stack.isEmpty()) out.add(new GraveItem(stack.copy(), GraveItem.DV_SLOTS, type.name(), 0, false));
        }
    }

    public static boolean restore(ServerPlayer player, GraveItem item) {
        SlotData data = SlotData.get(player);
        if (data == null) return false;
        SlotType type = SlotType.valueOf(item.key());
        if (!data.get(type).isEmpty()) return false;
        data.set(type, item.stack());
        data.markDirty();
        return true;
    }
}
