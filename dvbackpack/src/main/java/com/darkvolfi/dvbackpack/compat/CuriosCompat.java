package com.darkvolfi.dvbackpack.compat;

import com.darkvolfi.dvbackpack.BackpackItem;
import com.darkvolfi.dvbackpack.DVBackpack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Curios integration through its public API, looked up by name so DV Backpack needs no Curios at build time.
 * The backpacks become curios through the {@code curios:back} item tag; this only finds the one worn.
 */
public final class CuriosCompat {
    private static Method getInventory;
    private static Method getCurios;
    private static Method getStacks;
    private static boolean broken;

    private CuriosCompat() {}

    private static boolean init() {
        if (getInventory != null) return true;
        if (broken) return false;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            getInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            getCurios = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("getCurios");
            getStacks = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler").getMethod("getStacks");
            return true;
        } catch (ReflectiveOperationException e) {
            broken = true;
            DVBackpack.LOG.warn("Curios API not found, backpacks can't be worn as curios: {}", e.toString());
            return false;
        }
    }

    public static ItemStack backpack(Player player) {
        return find(player, false);
    }

    public static ItemStack takeBackpack(Player player) {
        return find(player, true);
    }

    /** The backpack in a back slot; with {@code take}, the slot is emptied. */
    private static ItemStack find(Player player, boolean take) {
        if (!init()) return ItemStack.EMPTY;
        try {
            Object handler = ((LazyOptional<?>) getInventory.invoke(null, player)).orElse(null);
            if (handler == null) return ItemStack.EMPTY;
            Object back = ((Map<?, ?>) getCurios.invoke(handler)).get("back");
            if (back == null) return ItemStack.EMPTY;
            IItemHandler stacks = (IItemHandler) getStacks.invoke(back);
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!(stack.getItem() instanceof BackpackItem)) continue;
                if (take && stacks instanceof IItemHandlerModifiable modifiable) modifiable.setStackInSlot(i, ItemStack.EMPTY);
                return stack;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            DVBackpack.LOG.warn("Could not read curios: {}", e.toString());
        }
        return ItemStack.EMPTY;
    }
}
