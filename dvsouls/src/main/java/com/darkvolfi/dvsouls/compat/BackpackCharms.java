package com.darkvolfi.dvsouls.compat;

import com.darkvolfi.dvbackpack.WornBackpack;
import com.darkvolfi.dvsouls.charm.SoulCharm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/** DV Backpack: soul charms work from the upgrade slots of the backpack a player wears. Only loaded with DV Backpack. */
public final class BackpackCharms {
    private BackpackCharms() {}

    public static List<ItemStack> charms(ServerPlayer player) {
        List<ItemStack> charms = new ArrayList<>();
        ItemStackHandler upgrades = WornBackpack.upgrades(player);
        if (upgrades == null) return charms;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (upgrades.getStackInSlot(i).getItem() instanceof SoulCharm) charms.add(upgrades.getStackInSlot(i));
        }
        return charms;
    }
}
