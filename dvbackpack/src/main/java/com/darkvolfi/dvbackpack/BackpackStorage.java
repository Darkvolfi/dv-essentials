package com.darkvolfi.dvbackpack;

import com.darkvolfi.dvbackpack.upgrade.CookingUpgrade;
import com.darkvolfi.dvbackpack.upgrade.UpgradeItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What every backpack holds, by the id written on the backpack item. Keeping it in the world rather than on
 * the item keeps the item small (it is synced every time it moves) and lets one backpack be open in several
 * places at once, e.g. on the ground and by a second player.
 */
public final class BackpackStorage extends SavedData {
    private static final String NAME = DVBackpack.MOD_ID;
    private static final String ID_TAG = "BackpackId";

    /** A backpack's items and the upgrade cards in its upgrade slots. */
    public record Contents(ItemStackHandler items, ItemStackHandler upgrades) {}

    private final Map<UUID, Contents> contents = new HashMap<>();

    public static BackpackStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BackpackStorage::load, BackpackStorage::new, NAME);
    }

    /** The contents of this backpack, created (and the id written on the item) the first time, and grown after a tier upgrade. */
    public Contents contents(ItemStack backpack) {
        BackpackTier tier = ((BackpackItem) backpack.getItem()).tier();
        CompoundTag tag = backpack.getOrCreateTag();
        UUID id;
        if (tag.hasUUID(ID_TAG)) {
            id = tag.getUUID(ID_TAG);
        } else {
            id = UUID.randomUUID();
            tag.putUUID(ID_TAG, id);
        }
        Contents c = contents.computeIfAbsent(id, k -> new Contents(items(tier.slots()), upgrades(tier.upgradeSlots())));
        if (c.items().getSlots() < tier.slots() || c.upgrades().getSlots() < tier.upgradeSlots()) {
            c = new Contents(grow(c.items(), items(tier.slots())), grow(c.upgrades(), upgrades(tier.upgradeSlots())));
            contents.put(id, c);
            setDirty();
        }
        return c;
    }

    private static ItemStackHandler grow(ItemStackHandler from, ItemStackHandler to) {
        if (to.getSlots() <= from.getSlots()) return from;
        for (int i = 0; i < from.getSlots(); i++) to.setStackInSlot(i, from.getStackInSlot(i));
        return to;
    }

    /** Whether two stacks are the same backpack. */
    public static boolean same(ItemStack a, ItemStack b) {
        CompoundTag ta = a.getTag();
        CompoundTag tb = b.getTag();
        return ta != null && tb != null && ta.hasUUID(ID_TAG) && tb.hasUUID(ID_TAG) && ta.getUUID(ID_TAG).equals(tb.getUUID(ID_TAG));
    }

    /** Runs the furnace cards of every backpack, wherever it is: in a hand, on a back, on the ground or in a chest. */
    public void tick(ServerLevel level) {
        for (Contents c : contents.values()) {
            ItemStackHandler upgrades = c.upgrades();
            for (int i = 0; i < upgrades.getSlots(); i++) {
                ItemStack card = upgrades.getStackInSlot(i);
                if (CookingUpgrade.isCooking(card) && CookingUpgrade.tick(card, level)) setDirty();
            }
        }
    }

    private ItemStackHandler items(int size) {
        return new ItemStackHandler(size) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return !(stack.getItem() instanceof BackpackItem);
            }

            @Override
            protected void onContentsChanged(int slot) {
                setDirty();
            }
        };
    }

    private ItemStackHandler upgrades(int size) {
        return new ItemStackHandler(size) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return UpgradeItem.isCard(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                setDirty();
            }
        };
    }

    private static BackpackStorage load(CompoundTag tag) {
        BackpackStorage storage = new BackpackStorage();
        for (Tag t : tag.getList("Backpacks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            CompoundTag items = entry.getCompound("Items");
            ItemStackHandler itemHandler = storage.items(items.getInt("Size"));
            itemHandler.deserializeNBT(items);
            CompoundTag upgrades = entry.getCompound("Upgrades");
            ItemStackHandler upgradeHandler = storage.upgrades(Math.max(1, upgrades.getInt("Size")));
            if (upgrades.contains("Size")) upgradeHandler.deserializeNBT(upgrades);
            storage.contents.put(entry.getUUID("Id"), new Contents(itemHandler, upgradeHandler));
        }
        return storage;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Contents> e : contents.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", e.getKey());
            entry.put("Items", e.getValue().items().serializeNBT());
            entry.put("Upgrades", e.getValue().upgrades().serializeNBT());
            list.add(entry);
        }
        tag.put("Backpacks", list);
        return tag;
    }
}
