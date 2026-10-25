package com.darkvolfi.dvslots.slot;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;

/** A player's DV slots, attached as a capability and saved with the player. */
public class SlotData implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<SlotData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    private final ItemStackHandler items = new ItemStackHandler(SlotType.values().length) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return SlotType.values()[slot].accepts(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            dirty = true;
        }
    };
    private final LazyOptional<SlotData> self = LazyOptional.of(() -> this);
    private boolean dirty;

    public static SlotData get(Player player) {
        return player.getCapability(CAPABILITY).orElse(null);
    }

    /** The elytra in the back slot, or empty if there is none that can fly right now. */
    public static ItemStack flyableBack(LivingEntity entity) {
        if (!(entity instanceof Player player)) return ItemStack.EMPTY;
        SlotData data = get(player);
        if (data == null) return ItemStack.EMPTY;
        ItemStack back = data.get(SlotType.BACK);
        return back.canElytraFly(entity) ? back : ItemStack.EMPTY;
    }

    /**
     * Used by the flight mixins in place of {@code getItemBySlot}: the chest item as usual,
     * unless it can't fly and the back slot holds an elytra that can.
     */
    public static ItemStack chestOrBack(LivingEntity entity, EquipmentSlot slot) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (slot != EquipmentSlot.CHEST || stack.canElytraFly(entity)) return stack;
        ItemStack back = flyableBack(entity);
        return back.isEmpty() ? stack : back;
    }

    public ItemStackHandler items() {
        return items;
    }

    public ItemStack get(SlotType type) {
        return items.getStackInSlot(type.ordinal());
    }

    public void set(SlotType type, ItemStack stack) {
        items.setStackInSlot(type.ordinal(), stack);
    }

    public void markDirty() {
        dirty = true;
    }

    /** Returns whether the contents changed since the last call. */
    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return CAPABILITY.orEmpty(cap, self);
    }

    @Override
    public CompoundTag serializeNBT() {
        return items.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        items.deserializeNBT(tag);
        // A save from a build with fewer slots must not shrink the handler.
        if (items.getSlots() != SlotType.values().length) {
            ItemStack[] old = new ItemStack[items.getSlots()];
            for (int i = 0; i < old.length; i++) old[i] = items.getStackInSlot(i);
            items.setSize(SlotType.values().length);
            for (int i = 0; i < Math.min(old.length, items.getSlots()); i++) items.setStackInSlot(i, old[i]);
        }
    }
}
