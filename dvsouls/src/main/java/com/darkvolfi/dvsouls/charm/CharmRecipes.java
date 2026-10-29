package com.darkvolfi.dvsouls.charm;

import com.darkvolfi.dvsouls.DVSouls;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** The crafting recipes that change a soul charm: putting essences on it or taking one off, and the next tier. */
public final class CharmRecipes {
    private CharmRecipes() {}

    /**
     * A charm with essences around it in the grid gets them added, if it has room. A charm alone, holding essences,
     * gives back its last one: the essence stays in the grid where the charm was.
     */
    public static class Essences extends CustomRecipe {
        public Essences(ResourceLocation id, CraftingBookCategory category) {
            super(id, category);
        }

        /** The charm's slot and the essences' creatures, or null if the grid isn't this recipe. */
        private record Grid(int charm, List<EntityType<?>> added) {}

        private static Grid read(CraftingContainer grid) {
            int charm = -1;
            List<EntityType<?>> added = new ArrayList<>();
            for (int i = 0; i < grid.getContainerSize(); i++) {
                ItemStack stack = grid.getItem(i);
                if (stack.isEmpty()) continue;
                if (stack.getItem() instanceof SoulCharm && charm < 0) {
                    charm = i;
                } else if (stack.is(DVSouls.SOUL_ESSENCE.get()) && EssenceItem.ability(stack) != null) {
                    added.add(EssenceItem.creature(stack));
                } else {
                    return null;
                }
            }
            if (charm < 0) return null;
            ItemStack stack = grid.getItem(charm);
            int held = SoulCharm.essences(stack).size();
            if (added.isEmpty() ? held == 0 : held + added.size() > SoulCharm.capacity(stack)) return null;
            return new Grid(charm, added);
        }

        @Override
        public boolean matches(CraftingContainer grid, Level level) {
            return read(grid) != null;
        }

        @Override
        public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
            Grid read = read(grid);
            if (read == null) return ItemStack.EMPTY;
            ItemStack charm = grid.getItem(read.charm());
            List<EntityType<?>> essences = SoulCharm.essences(charm);
            if (read.added().isEmpty()) essences.remove(essences.size() - 1);
            else essences.addAll(read.added());
            return SoulCharm.withEssences(charm, essences);
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
            NonNullList<ItemStack> remaining = super.getRemainingItems(grid);
            Grid read = read(grid);
            if (read != null && read.added().isEmpty()) {
                List<EntityType<?>> essences = SoulCharm.essences(grid.getItem(read.charm()));
                remaining.set(read.charm(), EssenceItem.of(new ItemStack(DVSouls.SOUL_ESSENCE.get()), essences.get(essences.size() - 1)));
            }
            return remaining;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width * height >= 1;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return DVSouls.CHARM_ESSENCES.get();
        }
    }

    /** A shaped recipe that makes a charm the next tier, keeping its essences. */
    public static class Upgrade extends ShapedRecipe {
        public Upgrade(ShapedRecipe shaped) {
            super(shaped.getId(), shaped.getGroup(), shaped.category(), shaped.getWidth(), shaped.getHeight(),
                    shaped.getIngredients(), shaped.getResultItem(RegistryAccess.EMPTY), shaped.showNotification());
        }

        @Override
        public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
            ItemStack result = super.assemble(grid, access);
            for (int i = 0; i < grid.getContainerSize(); i++) {
                ItemStack old = grid.getItem(i);
                if (old.getItem() instanceof SoulCharm && old.hasTag()) {
                    result.setTag(old.getTag().copy());
                    break;
                }
            }
            return result;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return DVSouls.CHARM_UPGRADE.get();
        }

        public static final class Serializer implements RecipeSerializer<Upgrade> {
            @Override
            public Upgrade fromJson(ResourceLocation id, JsonObject json) {
                return new Upgrade(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
            }

            @Override
            public Upgrade fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
                return new Upgrade(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf));
            }

            @Override
            public void toNetwork(FriendlyByteBuf buf, Upgrade recipe) {
                RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
            }
        }
    }
}
