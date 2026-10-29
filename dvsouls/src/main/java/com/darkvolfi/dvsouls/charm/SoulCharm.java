package com.darkvolfi.dvsouls.charm;

import net.minecraft.ChatFormatting;
import com.darkvolfi.dvsouls.DVSouls;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A soul charm: holds soul essences (gold one, diamond two, netherite three) and gives their abilities while it sits in
 * the upgrade slot of a worn DV Backpack, where its tab opens a panel of its essence places. Without DV Backpack it
 * works anywhere in the inventory, and essences are added in the crafting grid (the charm alone gives back its last).
 */
public class SoulCharm extends Item {
    private static final String ESSENCES = "Essences";

    public enum Tier {
        GOLD(1), DIAMOND(2), NETHERITE(3);

        private final int capacity;

        Tier(int capacity) {
            this.capacity = capacity;
        }

        public int capacity() {
            return capacity;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Tier tier;

    public SoulCharm(Tier tier) {
        super(tier == Tier.NETHERITE ? new Properties().stacksTo(1).fireResistant() : new Properties().stacksTo(1));
        this.tier = tier;
    }

    public static int capacity(ItemStack charm) {
        return charm.getItem() instanceof SoulCharm item ? item.tier.capacity() : 0;
    }

    /** The creatures whose essences the charm holds, in the order they were added. */
    public static List<EntityType<?>> essences(ItemStack charm) {
        List<EntityType<?>> types = new ArrayList<>();
        if (!charm.hasTag()) return types;
        ListTag list = charm.getTag().getList(ESSENCES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) EntityType.byString(list.getString(i)).ifPresent(types::add);
        return types;
    }

    public static ItemStack withEssences(ItemStack charm, List<EntityType<?>> types) {
        ItemStack copy = charm.copyWithCount(1);
        ListTag list = new ListTag();
        for (EntityType<?> type : types) list.add(StringTag.valueOf(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type))));
        if (list.isEmpty()) {
            copy.removeTagKey(ESSENCES);
        } else {
            copy.getOrCreateTag().put(ESSENCES, list);
        }
        return copy;
    }

    /**
     * The charm's essence places, for DV Backpack's panel: each holds one essence, kept in the charm's list at its
     * index ("" for an empty place).
     */
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        LazyOptional<IItemHandler> places = LazyOptional.of(() -> new Places(stack));
        return new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                return ForgeCapabilities.ITEM_HANDLER.orEmpty(cap, places);
            }
        };
    }

    private record Places(ItemStack charm) implements IItemHandlerModifiable {
        private ListTag list() {
            return charm.hasTag() ? charm.getTag().getList(ESSENCES, Tag.TAG_STRING) : new ListTag();
        }

        @Override
        public int getSlots() {
            return capacity(charm);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ListTag list = list();
            if (slot >= list.size()) return ItemStack.EMPTY;
            return EntityType.byString(list.getString(slot))
                    .map(type -> EssenceItem.of(new ItemStack(DVSouls.SOUL_ESSENCE.get()), type)).orElse(ItemStack.EMPTY);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            ListTag list = list();
            while (list.size() <= slot) list.add(StringTag.valueOf(""));
            EntityType<?> type = stack.isEmpty() ? null : EssenceItem.creature(stack);
            list.set(slot, StringTag.valueOf(type == null ? "" : String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type))));
            while (!list.isEmpty() && list.getString(list.size() - 1).isEmpty()) list.remove(list.size() - 1);
            if (list.isEmpty()) charm.removeTagKey(ESSENCES);
            else charm.getOrCreateTag().put(ESSENCES, list);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack) || !getStackInSlot(slot).isEmpty()) return stack;
            if (!simulate) setStackInSlot(slot, stack.copyWithCount(1));
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack held = getStackInSlot(slot);
            if (amount <= 0 || held.isEmpty()) return ItemStack.EMPTY;
            if (!simulate) setStackInSlot(slot, ItemStack.EMPTY);
            return held;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < getSlots() && stack.is(DVSouls.SOUL_ESSENCE.get()) && EssenceItem.ability(stack) != null;
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !essences(stack).isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        List<EntityType<?>> types = essences(stack);
        for (EntityType<?> type : types) {
            Ability ability = Ability.of(type);
            Component line = Component.literal("• ").append(type.getDescription());
            if (ability != null) line = line.copy().append(Component.literal(": ")).append(ability.describe());
            tooltip.add(line.copy().withStyle(ChatFormatting.BLUE));
        }
        for (int i = types.size(); i < tier.capacity(); i++) {
            tooltip.add(Component.translatable("dvsouls.charm.empty_slot").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(ModList.get().isLoaded("dvbackpack") ? "dvsouls.charm.hint.backpack" : "dvsouls.charm.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
