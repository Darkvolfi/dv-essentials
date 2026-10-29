package com.darkvolfi.dvbackpack;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** A shaped recipe that turns a backpack into the next tier, keeping its id (so its contents) and name. */
public class TierUpgradeRecipe extends ShapedRecipe {
    public TierUpgradeRecipe(ShapedRecipe shaped) {
        super(shaped.getId(), shaped.getGroup(), shaped.category(), shaped.getWidth(), shaped.getHeight(),
                shaped.getIngredients(), shaped.getResultItem(RegistryAccess.EMPTY), shaped.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        ItemStack result = super.assemble(container, access);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack old = container.getItem(i);
            if (old.getItem() instanceof BackpackItem && old.hasTag()) {
                result.setTag(old.getTag().copy());
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DVBackpack.TIER_UPGRADE.get();
    }

    public static final class Serializer implements RecipeSerializer<TierUpgradeRecipe> {
        @Override
        public TierUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new TierUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public TierUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new TierUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, TierUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
        }
    }
}
