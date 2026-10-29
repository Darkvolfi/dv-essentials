package com.darkvolfi.dvsouls.compat;

import com.darkvolfi.dvsouls.DVSouls;
import com.darkvolfi.dvsouls.Soul;
import com.darkvolfi.dvsouls.SoulFuser;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Shows DV Science of Souls in JEI, when JEI is installed (JEI finds this class itself; nothing else loads it): the
 * Soul Furnace among the smelting recipes, and a page of the Soul Fuser's recipes, two souls and the hybrid they make.
 */
@JeiPlugin
public class SoulsJeiPlugin implements IModPlugin {
    static final RecipeType<Fusion> FUSION = RecipeType.create(DVSouls.MOD_ID, "fusion", Fusion.class);

    /** Two souls in and the hybrid's soul out, each as a jar. */
    record Fusion(ItemStack first, ItemStack second, ItemStack hybrid) {}

    private static ItemStack jar(EntityType<?> type) {
        return Soul.put(new ItemStack(DVSouls.FILLED_SOUL_JAR.get()), Soul.ofType(type));
    }

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(DVSouls.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FusionCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Fusion> fusions = new ArrayList<>();
        SoulFuser.hybrids().forEach((pair, hybrid) -> {
            Iterator<EntityType<?>> it = pair.iterator();
            fusions.add(new Fusion(jar(it.next()), jar(it.next()), jar(hybrid.get())));
        });
        registration.addRecipes(FUSION, fusions);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(DVSouls.SOUL_FURNACE.get()), RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(new ItemStack(DVSouls.SOUL_FUSER.get()), FUSION);
    }

    /** One row: soul + soul -> hybrid, laid out like the Soul Fuser. */
    private static final class FusionCategory implements IRecipeCategory<Fusion> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable slot;

        FusionCategory(IGuiHelper gui) {
            background = gui.createBlankDrawable(120, 22);
            icon = gui.createDrawableItemStack(new ItemStack(DVSouls.SOUL_FUSER.get()));
            slot = gui.getSlotDrawable();
        }

        @Override
        public RecipeType<Fusion> getRecipeType() {
            return FUSION;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("container.dvsouls.soul_fuser");
        }

        @Override
        public IDrawable getBackground() {
            return background;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Fusion fusion, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 3, 3).setBackground(slot, -1, -1).addItemStack(fusion.first());
            builder.addSlot(RecipeIngredientRole.INPUT, 35, 3).setBackground(slot, -1, -1).addItemStack(fusion.second());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 3).setBackground(slot, -1, -1).addItemStack(fusion.hybrid());
        }

        @Override
        public void draw(Fusion fusion, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            g.drawString(font, "+", 25, 7, 0xFF808080, false);
            g.drawString(font, "→", 72, 7, 0xFF808080, false);
        }
    }
}
