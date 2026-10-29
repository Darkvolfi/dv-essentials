package com.darkvolfi.dvsouls.charm;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A soul distilled down to what its creature can do, ready to go on a soul charm. Keeps only the creature's kind. */
public class EssenceItem extends Item {
    private static final String CREATURE = "Creature";

    public EssenceItem() {
        super(new Properties().stacksTo(16));
    }

    public static ItemStack of(ItemStack essence, EntityType<?> type) {
        essence.getOrCreateTag().putString(CREATURE, String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type)));
        return essence;
    }

    public static @Nullable EntityType<?> creature(ItemStack stack) {
        return stack.hasTag() ? EntityType.byString(stack.getTag().getString(CREATURE)).orElse(null) : null;
    }

    public static @Nullable Ability ability(ItemStack stack) {
        EntityType<?> type = creature(stack);
        return type == null ? null : Ability.of(type);
    }

    @Override
    public Component getName(ItemStack stack) {
        EntityType<?> type = creature(stack);
        return type == null ? super.getName(stack) : Component.translatable("item.dvsouls.soul_essence.of", type.getDescription());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Ability ability = ability(stack);
        if (ability == null) {
            tooltip.add(Component.translatable("dvsouls.no_soul").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(ability.describe().copy().withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("dvsouls.essence.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
