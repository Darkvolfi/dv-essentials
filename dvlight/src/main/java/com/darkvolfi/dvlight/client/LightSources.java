package com.darkvolfi.dvlight.client;

import com.darkvolfi.dvlight.LightConfig;
import com.darkvolfi.dvlight.mixin.MinecartFurnaceAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** How much light a thing gives off. */
final class LightSources {
    /** {@code held}: the light comes from what a creature holds, so it can be a cone. */
    record Glow(int level, boolean held) {
        static final Glow NONE = new Glow(0, false);
    }

    private static List<? extends String> itemSource, entitySource;
    private static Map<ResourceLocation, Integer> items = Map.of(), entities = Map.of();

    private LightSources() {}

    static Glow of(Entity entity) {
        refreshLists();
        int level = 0;
        boolean held = false;

        Integer listed = entities.get(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
        if (listed != null) level = listed;
        if (entity.displayFireAnimation()) level = Math.max(level, 14);
        if (entity instanceof ItemEntity item) level = Math.max(level, of(item.getItem()));
        if (entity instanceof LivingEntity living) {
            int hands = Math.max(of(living.getMainHandItem()), of(living.getOffhandItem()));
            if (hands > level) { level = hands; held = true; }
        }
        if (entity instanceof Creeper creeper) {
            level = Math.max(level, Math.round(creeper.getSwelling(1f) * 12));
        }
        if (entity instanceof MinecartFurnace cart && ((MinecartFurnaceAccessor) cart).dvlight$hasFuel()) {
            level = Math.max(level, 13);
        }
        return level <= 0 ? Glow.NONE : new Glow(Math.min(level, 15), held);
    }

    private static int of(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Integer listed = items.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        int level;
        if (listed != null) level = listed;
        else if (stack.getItem() instanceof BlockItem block)
            level = block.getBlock().defaultBlockState().getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        else level = 0;
        if (LightConfig.ENCHANTED_GLOW.get() && stack.hasFoil()) level = Math.max(level, 5);
        return level;
    }

    private static void refreshLists() {
        List<? extends String> itemList = LightConfig.ITEM_LIGHTS.get(), entityList = LightConfig.ENTITY_LIGHTS.get();
        if (itemList != itemSource) { items = parse(itemList); itemSource = itemList; }
        if (entityList != entitySource) { entities = parse(entityList); entitySource = entityList; }
    }

    private static Map<ResourceLocation, Integer> parse(List<? extends String> entries) {
        Map<ResourceLocation, Integer> map = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.lastIndexOf('=');
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, eq).trim().toLowerCase(Locale.ROOT));
            if (id != null) map.put(id, Math.min(15, Integer.parseInt(entry.substring(eq + 1).trim())));
        }
        return map;
    }
}
