package com.darkvolfi.dvbackpack.upgrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A furnace, smoker or blast furnace that lives on its card: the input, fuel and output slots, the fire and the
 * progress are all saved in the card's NBT, so they move with the card and cook wherever the backpack is.
 */
public final class CookingUpgrade {
    public static final int INPUT = 0;
    public static final int FUEL = 1;
    public static final int OUTPUT = 2;

    private CookingUpgrade() {}

    public static boolean isCooking(ItemStack card) {
        UpgradeType type = UpgradeItem.typeOf(card);
        return type != null && type.cooking() != null;
    }

    private static CompoundTag tag(ItemStack card) {
        return card.getOrCreateTagElement("Cooking");
    }

    private static ItemStackHandler slots(ItemStack card) {
        ItemStackHandler slots = new ItemStackHandler(3);
        CompoundTag tag = tag(card);
        if (tag.contains("Slots")) slots.deserializeNBT(tag.getCompound("Slots"));
        return slots;
    }

    public static ItemStack get(ItemStack card, int slot) {
        return slots(card).getStackInSlot(slot);
    }

    public static void set(ItemStack card, int slot, ItemStack stack) {
        ItemStackHandler slots = slots(card);
        slots.setStackInSlot(slot, stack);
        tag(card).put("Slots", slots.serializeNBT());
    }

    /** "burn", "burnMax", "cook", "cookTotal" for the panel's flame and arrow. */
    public static int value(ItemStack card, String key) {
        return tag(card).getInt(key);
    }

    /** Experience waiting for whoever takes the output, then cleared. */
    public static float takeExperience(ItemStack card) {
        CompoundTag tag = tag(card);
        float xp = tag.getFloat("xp");
        tag.remove("xp");
        return xp;
    }

    public static boolean isFuel(ItemStack card, ItemStack stack) {
        return isCooking(card) && ((UpgradeItem) card.getItem()).burnTime(stack) > 0;
    }

    private static Optional<? extends AbstractCookingRecipe> recipe(ItemStack card, ItemStack input, Level level) {
        RecipeType<? extends AbstractCookingRecipe> type = UpgradeItem.typeOf(card).cooking();
        return level.getRecipeManager().getRecipeFor(type, new SimpleContainer(input), level);
    }

    public static boolean canCook(ItemStack card, ItemStack stack, Level level) {
        return recipe(card, stack, level).isPresent();
    }

    /** One tick of cooking (as many steps as the card is fast); returns whether anything changed. */
    public static boolean tick(ItemStack card, ServerLevel level) {
        boolean changed = false;
        for (int i = ((UpgradeItem) card.getItem()).speed(); i > 0; i--) changed |= step(card, level);
        return changed;
    }

    private static boolean step(ItemStack card, ServerLevel level) {
        CompoundTag tag = tag(card);
        int burn = tag.getInt("burn");
        int cook = tag.getInt("cook");
        ItemStackHandler slots = slots(card);
        ItemStack input = slots.getStackInSlot(INPUT);
        if (burn <= 0 && cook == 0 && input.isEmpty()) return false; // cold and empty: nothing to do

        boolean changed = false;
        if (burn > 0) {
            burn--;
            changed = true;
        }
        AbstractCookingRecipe recipe = input.isEmpty() ? null : recipe(card, input, level).orElse(null);
        ItemStack result = recipe == null ? ItemStack.EMPTY : recipe.getResultItem(level.registryAccess());
        ItemStack output = slots.getStackInSlot(OUTPUT);
        boolean fits = !result.isEmpty() && (output.isEmpty()
                || ItemStack.isSameItemSameTags(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize());

        if (burn <= 0 && fits) {
            ItemStack fuel = slots.getStackInSlot(FUEL);
            int fuelTime = ((UpgradeItem) card.getItem()).burnTime(fuel);
            if (fuelTime > 0) {
                burn = fuelTime;
                tag.putInt("burnMax", fuelTime);
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuel = fuel.copy();
                fuel.shrink(1);
                slots.setStackInSlot(FUEL, fuel.isEmpty() ? remainder : fuel);
                changed = true;
            }
        }

        if (burn > 0 && fits) {
            int total = recipe.getCookingTime();
            tag.putInt("cookTotal", total);
            if (++cook >= total) {
                cook = 0;
                slots.setStackInSlot(OUTPUT, output.isEmpty() ? result.copy() : output.copyWithCount(output.getCount() + result.getCount()));
                slots.setStackInSlot(INPUT, input.copyWithCount(input.getCount() - 1));
                tag.putFloat("xp", tag.getFloat("xp") + recipe.getExperience());
            }
            changed = true;
        } else if (cook > 0) {
            cook = 0;
            changed = true;
        }

        if (changed) {
            tag.putInt("burn", burn);
            tag.putInt("cook", cook);
            tag.put("Slots", slots.serializeNBT());
        }
        return changed;
    }

    /** Tooltip lines: what is in the card's slots. */
    public static List<Component> describe(ItemStack card) {
        List<Component> lines = new ArrayList<>();
        if (!card.hasTag()) return lines;
        ItemStackHandler slots = slots(card);
        for (int i = 0; i < 3; i++) {
            ItemStack stack = slots.getStackInSlot(i);
            if (!stack.isEmpty()) lines.add(Component.literal(stack.getCount() + " × ").append(stack.getHoverName()));
        }
        return lines;
    }
}
