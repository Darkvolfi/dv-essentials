package com.darkvolfi.dvbackpack.upgrade;

import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** The kinds of upgrade card. Each opens a panel from its tab: a tool, or the filter of an automatic card. */
public enum UpgradeType {
    CRAFTING(null, false),
    FURNACE(RecipeType.SMELTING, false),
    SMOKER(RecipeType.SMOKING, false),
    BLAST_FURNACE(RecipeType.BLASTING, false),
    /** Picked-up items go into the backpack; can also pull items in. */
    PICKUP(null, true),
    /** Picked-up items on its list are destroyed. */
    VOID(null, true),
    /** Eats food from the backpack when hungry. */
    FEEDING(null, true),
    /** Refills the held stack from the backpack when it runs out. */
    REFILL(null, true),
    /** A fluid tank. */
    TANK(null, false);

    private final @Nullable RecipeType<? extends AbstractCookingRecipe> cooking;
    private final boolean filter;

    UpgradeType(@Nullable RecipeType<? extends AbstractCookingRecipe> cooking, boolean filter) {
        this.cooking = cooking;
        this.filter = filter;
    }

    /** What this card cooks with, or null if it doesn't cook. */
    public @Nullable RecipeType<? extends AbstractCookingRecipe> cooking() {
        return cooking;
    }

    /** Whether this card works on its own and has a filter (see {@link FilterUpgrade}). */
    public boolean filter() {
        return filter;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
