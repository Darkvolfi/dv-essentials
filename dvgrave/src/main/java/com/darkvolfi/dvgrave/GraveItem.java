package com.darkvolfi.dvgrave;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * A stack in a grave and where it was when the player died, so it can go back there.
 *
 * @param slot  {@link #INVENTORY} (index = inventory slot 0..40), {@link #DV_SLOTS} (key = slot type),
 *              {@link #CURIOS} (key = curio slot id, index = its position, cosmetic in {@code extra}),
 *              {@link #COSMETIC_ARMOR} (index 0..3) or {@link #OTHER} (only into the inventory)
 */
public record GraveItem(ItemStack stack, String slot, String key, int index, boolean extra) {
    public static final String INVENTORY = "inventory";
    public static final String DV_SLOTS = "dvslots";
    public static final String CURIOS = "curios";
    public static final String COSMETIC_ARMOR = "cosarmor";
    public static final String OTHER = "other";

    public static GraveItem other(ItemStack stack) {
        return new GraveItem(stack, OTHER, "", 0, false);
    }

    public GraveItem withStack(ItemStack newStack) {
        return new GraveItem(newStack, slot, key, index, extra);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Item", stack.save(new CompoundTag()));
        tag.putString("Slot", slot);
        if (!key.isEmpty()) tag.putString("Key", key);
        tag.putInt("Index", index);
        if (extra) tag.putBoolean("Extra", true);
        return tag;
    }

    public static GraveItem load(CompoundTag tag) {
        return new GraveItem(ItemStack.of(tag.getCompound("Item")), tag.getString("Slot"), tag.getString("Key"),
                tag.getInt("Index"), tag.getBoolean("Extra"));
    }
}
