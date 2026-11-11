package com.darkvolfi.dvnewcrafts.recipe;

import com.darkvolfi.dvnewcrafts.DVNewCrafts;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * A shaped recipe whose damageable ingredients (e.g. shears) stay in the grid, losing {@code damage} durability
 * instead of being used up. Being a ShapedRecipe, the recipe book and JEI show it like any other.
 */
public class ToolDamagingShapedRecipe extends ShapedRecipe {
    // Thread safe: in singleplayer the client and the server thread both ask for the remaining items.
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private final int damage;

    public ToolDamagingShapedRecipe(ShapedRecipe base, int damage) {
        super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(),
                base.getResultItem(null), base.showNotification());
        this.damage = damage;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(container);
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack stack = container.getItem(i);
            if (!remaining.get(i).isEmpty() || !stack.isDamageableItem()) continue;
            ItemStack tool = stack.copyWithCount(1);
            // hurt() applies Unbreaking and returns true when the tool breaks, which leaves the slot empty.
            remaining.set(i, tool.hurt(damage, RANDOM, null) ? ItemStack.EMPTY : tool);
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DVNewCrafts.TOOL_DAMAGING_SHAPED.get();
    }

    public static class Serializer implements RecipeSerializer<ToolDamagingShapedRecipe> {
        @Override
        public ToolDamagingShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ToolDamagingShapedRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json),
                    Math.max(1, GsonHelper.getAsInt(json, "damage", 1)));
        }

        @Override
        public ToolDamagingShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf);
            return new ToolDamagingShapedRecipe(base, buf.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ToolDamagingShapedRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
            buf.writeVarInt(recipe.damage);
        }
    }
}
