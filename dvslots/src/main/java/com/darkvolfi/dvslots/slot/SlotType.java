package com.darkvolfi.dvslots.slot;

import com.darkvolfi.dvslots.DVSlots;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The DV equipment slots. The order here is the order of the slots in storage and on screen. */
public enum SlotType {
    /** Elytra (or anything tagged dvslots:back), worn alongside a chestplate. */
    BACK("back", ItemTags.create(new ResourceLocation(DVSlots.MOD_ID, "back"))),
    /** Saddle that goes onto a tamed horse when you mount it and comes back when you dismount. */
    SADDLE("saddle", ItemTags.create(new ResourceLocation(DVSlots.MOD_ID, "saddle")));

    private final String id;
    private final TagKey<Item> tag;

    SlotType(String id, TagKey<Item> tag) {
        this.id = id;
        this.tag = tag;
    }

    public String id() {
        return id;
    }

    public boolean accepts(ItemStack stack) {
        return stack.is(tag) || (this == BACK && stack.getItem() instanceof ElytraItem);
    }
}
