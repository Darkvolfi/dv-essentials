package com.darkvolfi.dvgrave.compat;

import com.darkvolfi.dvgrave.DVGraveLog;
import com.darkvolfi.dvgrave.GraveItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Cosmetic Armor Reworked integration through its API class, looked up by name (no build-time dependency).
 * Only touched after checking that the mod is loaded; any failure falls back to the inventory.
 */
public final class CosArmorCompat {
    private static Method getStacks;
    private static boolean broken;

    private CosArmorCompat() {}

    private static IItemHandlerModifiable stacks(ServerPlayer player) throws ReflectiveOperationException {
        if (getStacks == null) {
            if (broken) return null;
            try {
                getStacks = Class.forName("lain.mods.cos.api.CosArmorAPI").getMethod("getCAStacks", UUID.class);
            } catch (ReflectiveOperationException e) {
                broken = true;
                DVGraveLog.warn("Cosmetic Armor API not found, cosmetic armor goes back into the inventory: {}", e.toString());
                return null;
            }
        }
        Object stacks = getStacks.invoke(null, player.getUUID());
        return stacks instanceof IItemHandlerModifiable handler ? handler : null;
    }

    public static void snapshot(ServerPlayer player, List<GraveItem> out) {
        try {
            IItemHandlerModifiable stacks = stacks(player);
            if (stacks == null) return;
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty()) out.add(new GraveItem(stack.copy(), GraveItem.COSMETIC_ARMOR, "", i, false));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            DVGraveLog.warn("Could not read cosmetic armor: {}", e.toString());
        }
    }

    public static boolean restore(ServerPlayer player, GraveItem item) {
        try {
            IItemHandlerModifiable stacks = stacks(player);
            if (stacks == null || item.index() >= stacks.getSlots() || !stacks.getStackInSlot(item.index()).isEmpty()) return false;
            stacks.setStackInSlot(item.index(), item.stack());
            return true;
        } catch (ReflectiveOperationException e) {
            DVGraveLog.warn("Could not put cosmetic armor back: {}", e.toString());
            return false;
        }
    }
}
