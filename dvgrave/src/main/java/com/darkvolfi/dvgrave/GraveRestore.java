package com.darkvolfi.dvgrave;

import com.darkvolfi.dvgrave.compat.CosArmorCompat;
import com.darkvolfi.dvgrave.compat.CuriosCompat;
import com.darkvolfi.dvgrave.compat.SlotsCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/** Puts a grave's stacks back: each into the slot it came from when that slot is free, the rest into the inventory. */
public final class GraveRestore {
    private GraveRestore() {}

    public static void restore(ServerPlayer player, List<GraveItem> items) {
        // Own slots first, so loose items don't take a slot an equipped item needs.
        List<ItemStack> rest = new ArrayList<>();
        for (GraveItem item : items) {
            if (!backInPlace(player, item)) rest.add(item.stack());
        }
        for (ItemStack stack : rest) ItemHandlerHelper.giveItemToPlayer(player, stack);
        player.inventoryMenu.broadcastChanges();
    }

    private static boolean backInPlace(ServerPlayer player, GraveItem item) {
        ItemStack stack = item.stack();
        try {
            return switch (item.slot()) {
                case GraveItem.INVENTORY -> {
                    Inventory inventory = player.getInventory();
                    if (item.index() < 0 || item.index() >= inventory.getContainerSize() || !inventory.getItem(item.index()).isEmpty()) yield false;
                    inventory.setItem(item.index(), stack);
                    yield true;
                }
                case GraveItem.DV_SLOTS -> ModList.get().isLoaded("dvslots") && SlotsCompat.restore(player, item);
                case GraveItem.CURIOS -> ModList.get().isLoaded("curios") && CuriosCompat.restore(player, item);
                case GraveItem.COSMETIC_ARMOR -> ModList.get().isLoaded("cosmeticarmorreworked") && CosArmorCompat.restore(player, item);
                default -> false;
            };
        } catch (RuntimeException e) {
            // Another mod's API changed or refused: the stack still comes back, into the inventory.
            DVGraveLog.warn("Could not put {} back into its {} slot: {}", stack, item.slot(), e.toString());
            return false;
        }
    }
}
