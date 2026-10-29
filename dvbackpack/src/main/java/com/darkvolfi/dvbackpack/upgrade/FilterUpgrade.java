package com.darkvolfi.dvbackpack.upgrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * The filter on an automatic card, saved on the card: up to {@link #SIZE} items, and whether the card acts
 * only on those ("only") or on everything but those ("except"). The pickup card also keeps its magnet switch here.
 */
public final class FilterUpgrade {
    public static final int SIZE = 9;

    private FilterUpgrade() {}

    private static CompoundTag tag(ItemStack card) {
        return card.getOrCreateTagElement("Filter");
    }

    private static ItemStackHandler items(ItemStack card) {
        ItemStackHandler items = new ItemStackHandler(SIZE);
        CompoundTag tag = tag(card);
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        return items;
    }

    public static ItemStack get(ItemStack card, int slot) {
        return items(card).getStackInSlot(slot);
    }

    public static void set(ItemStack card, int slot, ItemStack stack) {
        ItemStackHandler items = items(card);
        items.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        tag(card).put("Items", items.serializeNBT());
    }

    /** Adds the item to the first free place, unless it is listed already. */
    public static boolean add(ItemStack card, ItemStack stack) {
        ItemStackHandler items = items(card);
        int free = -1;
        for (int i = 0; i < SIZE; i++) {
            ItemStack listed = items.getStackInSlot(i);
            if (ItemStack.isSameItem(listed, stack)) return false;
            if (listed.isEmpty() && free < 0) free = i;
        }
        if (free < 0) return false;
        set(card, free, stack);
        return true;
    }

    /** "Only these" rather than "everything except these". The void card starts as "only" (it destroys nothing until told). */
    public static boolean only(ItemStack card) {
        CompoundTag tag = tag(card);
        return tag.contains("Only") ? tag.getBoolean("Only") : UpgradeItem.typeOf(card) == UpgradeType.VOID;
    }

    public static void toggleOnly(ItemStack card) {
        tag(card).putBoolean("Only", !only(card));
    }

    public static boolean magnet(ItemStack card) {
        return tag(card).getBoolean("Magnet");
    }

    public static void toggleMagnet(ItemStack card) {
        tag(card).putBoolean("Magnet", !magnet(card));
    }

    /** Whether the card acts on this item. */
    public static boolean matches(ItemStack card, ItemStack stack) {
        ItemStackHandler items = items(card);
        boolean listed = false;
        for (int i = 0; i < SIZE && !listed; i++) listed = ItemStack.isSameItem(items.getStackInSlot(i), stack);
        return listed == only(card);
    }
}
