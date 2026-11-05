package com.darkvolfi.dvsouls.compat;

import com.darkvolfi.dvsouls.DVSouls;
import com.darkvolfi.dvsouls.Soul;
import com.darkvolfi.dvsouls.SoulFuser;
import com.darkvolfi.dvsouls.SoulFurnace;
import com.darkvolfi.dvsouls.SoulsConfig;
import com.darkvolfi.dvsouls.charm.Ability;
import com.darkvolfi.dvsouls.charm.EssenceItem;
import net.minecraft.world.item.Items;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
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
import mezz.jei.api.runtime.IJeiRuntime;
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
 * Soul Furnace among the smelting recipes, and pages of the Soul Fuser's, Distiller's and Refiner's recipes.
 */
@JeiPlugin
public class SoulsJeiPlugin implements IModPlugin {
    static final RecipeType<Fusion> FUSION = RecipeType.create(DVSouls.MOD_ID, "fusion", Fusion.class);
    static final RecipeType<Fusion> DISTILLING = RecipeType.create(DVSouls.MOD_ID, "distilling", Fusion.class);
    static final RecipeType<Fusion> REFINING = RecipeType.create(DVSouls.MOD_ID, "refining", Fusion.class);

    /** Two things in and one out: two souls and the hybrid's soul, a soul and amethyst and its essence, or a soul and amethyst and pure soul. */
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
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new FusionCategory(gui, FUSION, "container.dvsouls.soul_fuser", DVSouls.SOUL_FUSER.get()),
                new FusionCategory(gui, DISTILLING, "container.dvsouls.soul_distiller", DVSouls.SOUL_DISTILLER.get()),
                new FusionCategory(gui, REFINING, "container.dvsouls.soul_refiner", DVSouls.SOUL_REFINER.get()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Fusion> fusions = new ArrayList<>();
        SoulFuser.hybrids().forEach((pair, hybrid) -> {
            Iterator<EntityType<?>> it = pair.iterator();
            fusions.add(new Fusion(jar(it.next()), jar(it.next()), jar(hybrid.get())));
        });
        registration.addRecipes(FUSION, fusions);
        List<Fusion> distilling = new ArrayList<>();
        for (EntityType<?> type : Ability.creatures()) {
            distilling.add(new Fusion(jar(type), new ItemStack(Items.AMETHYST_SHARD), EssenceItem.of(new ItemStack(DVSouls.SOUL_ESSENCE.get()), type)));
        }
        registration.addRecipes(DISTILLING, distilling);
        // A soul and amethyst give as many souls as it had hearts: a zombie's ten, a bucket of pure soul (at the default 100 mB a soul).
        registration.addRecipes(REFINING, List.of(
                new Fusion(jar(EntityType.ZOMBIE), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(DVSouls.PURE_SOUL_BUCKET.get()))));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(DVSouls.FURNACES.get(SoulFurnace.Kind.FURNACE).get()), RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(new ItemStack(DVSouls.FURNACES.get(SoulFurnace.Kind.SMOKER).get()), RecipeTypes.SMOKING);
        registration.addRecipeCatalyst(new ItemStack(DVSouls.FURNACES.get(SoulFurnace.Kind.BLAST_FURNACE).get()), RecipeTypes.BLASTING);
        registration.addRecipeCatalyst(new ItemStack(DVSouls.SOUL_FUSER.get()), FUSION);
        registration.addRecipeCatalyst(new ItemStack(DVSouls.SOUL_DISTILLER.get()), DISTILLING);
        registration.addRecipeCatalyst(new ItemStack(DVSouls.SOUL_REFINER.get()), REFINING);
    }

    /** The plain full jar, collar shard and essence hold no soul: hidden, so they aren't taken from the list by mistake. */
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
                List.of(new ItemStack(DVSouls.FILLED_SOUL_JAR.get()), new ItemStack(DVSouls.COLLAR_SHARD.get()),
                        new ItemStack(DVSouls.SOUL_ESSENCE.get())));
    }

    /** One row: two in, one out, laid out like the Soul Fuser. */
    private static final class FusionCategory implements IRecipeCategory<Fusion> {
        private final RecipeType<Fusion> type;
        private final String title;
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable slot;

        FusionCategory(IGuiHelper gui, RecipeType<Fusion> type, String title, net.minecraft.world.level.block.Block machine) {
            this.type = type;
            this.title = title;
            background = gui.createBlankDrawable(120, 22);
            icon = gui.createDrawableItemStack(new ItemStack(machine));
            slot = gui.getSlotDrawable();
        }

        @Override
        public RecipeType<Fusion> getRecipeType() {
            return type;
        }

        @Override
        public Component getTitle() {
            return Component.translatable(title);
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

        /** The Refiner's arrow tells how it works: a heart at a time, the jar keeping what is left, the amethyst wearing. */
        @Override
        public List<Component> getTooltipStrings(Fusion fusion, IRecipeSlotsView slots, double mouseX, double mouseY) {
            if (type != REFINING || mouseX < 60 || mouseX > 92) return List.of();
            return List.of(Component.translatable("dvsouls.jei.refining", SoulsConfig.SOULS_PER_AMETHYST.get()));
        }
    }
}
