package com.darkvolfi.dvnewcrafts;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/** Copper sits between stone and iron: for tools, and for armour between leather and iron. */
final class CopperMaterials {
    private CopperMaterials() {}

    static final Tier TOOLS = new Tier() {
        @Override public int getUses() { return 190; }
        @Override public float getSpeed() { return 5.0f; }
        @Override public float getAttackDamageBonus() { return 1.0f; }
        /** Like stone: mines iron ore, not diamond. */
        @Override public int getLevel() { return 1; }
        @Override public int getEnchantmentValue() { return 13; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.COPPER_INGOT); }
    };

    static final ArmorMaterial ARMOR = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            // The game's per-piece base durability (13, 15, 16, 11) times 11: leather is 5, iron 15.
            return switch (type) {
                case HELMET -> 13;
                case CHESTPLATE -> 15;
                case LEGGINGS -> 16;
                case BOOTS -> 11;
            } * 11;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> 2;
                case CHESTPLATE -> 4;
                case LEGGINGS -> 3;
                case BOOTS -> 1;
            };
        }

        @Override public int getEnchantmentValue() { return 13; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.COPPER_INGOT); }
        /** Points the game at textures/models/armor/copper_layer_1.png and _2 in this mod. */
        @Override public String getName() { return DVNewCrafts.MOD_ID + ":copper"; }
        @Override public float getToughness() { return 0; }
        @Override public float getKnockbackResistance() { return 0; }
    };
}
