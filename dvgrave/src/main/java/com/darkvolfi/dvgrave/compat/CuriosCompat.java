package com.darkvolfi.dvgrave.compat;

import com.darkvolfi.dvgrave.DVGraveLog;
import com.darkvolfi.dvgrave.GraveItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Curios integration through its public API, looked up by name so DV Grave needs no Curios at build time.
 * Only touched after checking that Curios is loaded; any failure falls back to the inventory.
 */
public final class CuriosCompat {
    private static Method getInventory;
    private static Method getCurios;
    private static Method getStacks;
    private static Method getCosmeticStacks;
    private static boolean broken;

    private CuriosCompat() {}

    private static boolean init() {
        if (getInventory != null) return true;
        if (broken) return false;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            getInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            getCurios = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("getCurios");
            Class<?> stacks = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            getStacks = stacks.getMethod("getStacks");
            getCosmeticStacks = stacks.getMethod("getCosmeticStacks");
            return true;
        } catch (ReflectiveOperationException e) {
            broken = true;
            DVGraveLog.warn("Curios API not found, curios go back into the inventory: {}", e.toString());
            return false;
        }
    }

    /** Curio slot id → its stacks handler, or null. */
    private static Map<?, ?> curios(ServerPlayer player) throws ReflectiveOperationException {
        LazyOptional<?> inventory = (LazyOptional<?>) getInventory.invoke(null, player);
        Object handler = inventory.orElse(null);
        return handler == null ? null : (Map<?, ?>) getCurios.invoke(handler);
    }

    private static IItemHandlerModifiable handler(Object stacksHandler, boolean cosmetic) throws ReflectiveOperationException {
        return (IItemHandlerModifiable) (cosmetic ? getCosmeticStacks : getStacks).invoke(stacksHandler);
    }

    public static void snapshot(ServerPlayer player, List<GraveItem> out) {
        if (!init()) return;
        try {
            Map<?, ?> curios = curios(player);
            if (curios == null) return;
            for (Map.Entry<?, ?> e : curios.entrySet()) {
                for (boolean cosmetic : new boolean[]{false, true}) {
                    IItemHandlerModifiable stacks = handler(e.getValue(), cosmetic);
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (!stack.isEmpty()) out.add(new GraveItem(stack.copy(), GraveItem.CURIOS, (String) e.getKey(), i, cosmetic));
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            DVGraveLog.warn("Could not read curios: {}", e.toString());
        }
    }

    public static boolean restore(ServerPlayer player, GraveItem item) {
        if (!init()) return false;
        try {
            Map<?, ?> curios = curios(player);
            Object stacksHandler = curios == null ? null : curios.get(item.key());
            if (stacksHandler == null) return false;
            IItemHandlerModifiable stacks = handler(stacksHandler, item.extra());
            if (item.index() >= stacks.getSlots() || !stacks.getStackInSlot(item.index()).isEmpty()) return false;
            stacks.setStackInSlot(item.index(), item.stack());
            return true;
        } catch (ReflectiveOperationException e) {
            DVGraveLog.warn("Could not put a curio back: {}", e.toString());
            return false;
        }
    }
}
