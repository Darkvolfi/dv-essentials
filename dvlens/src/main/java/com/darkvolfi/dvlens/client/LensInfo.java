package com.darkvolfi.dvlens.client;

import com.darkvolfi.dvlens.LensConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What the info box shows: an optional icon and a list of lines. Built from client-side data only. */
record LensInfo(ItemStack icon, List<Component> lines) {

    static LensInfo lookAt(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit == null || mc.level == null || mc.player == null) return null;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return forBlock(mc.level, mc.player, blockHit);
        }
        if (hit instanceof EntityHitResult entityHit) {
            return forEntity(entityHit.getEntity());
        }
        return null;
    }

    private static LensInfo forBlock(ClientLevel level, LocalPlayer player, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;

        ItemStack icon = state.getCloneItemStack(hit, level, pos, player);
        List<Component> lines = new ArrayList<>();
        lines.add(icon.isEmpty() ? state.getBlock().getName() : icon.getHoverName());

        if (LensConfig.SHOW_GROWTH.get()) {
            Component growth = growth(state);
            if (growth != null) lines.add(growth);
        }
        if (LensConfig.SHOW_HARVEST.get()) {
            Component harvest = harvest(level, player, pos, state);
            if (harvest != null) lines.add(harvest);
        }
        if (LensConfig.SHOW_MOD_NAME.get()) {
            lines.add(modName(ForgeRegistries.BLOCKS.getKey(state.getBlock())));
        }
        return new LensInfo(icon, lines);
    }

    private static LensInfo forEntity(Entity entity) {
        List<Component> lines = new ArrayList<>();
        ItemStack icon = ItemStack.EMPTY;

        if (entity instanceof ItemEntity item) {
            icon = item.getItem();
            lines.add(icon.getHoverName().copy().append(" ×" + icon.getCount()));
        } else {
            lines.add(entity.getDisplayName());
            SpawnEggItem egg = ForgeSpawnEggItem.fromEntityType(entity.getType());
            if (egg == null) egg = SpawnEggItem.byId(entity.getType());
            if (egg != null) icon = new ItemStack(egg);
        }

        if (LensConfig.SHOW_HEALTH.get() && entity instanceof LivingEntity living) {
            lines.add(Component.literal("❤ " + format(living.getHealth()) + " / " + format(living.getMaxHealth()))
                    .withStyle(ChatFormatting.RED));
        }
        if (LensConfig.SHOW_HORSE_STATS.get() && entity instanceof AbstractHorse horse && !(horse instanceof Llama)) {
            lines.add(Component.translatable("dvlens.horse.speed", format(horseSpeed(horse))).withStyle(ChatFormatting.AQUA));
            if (!(horse instanceof Camel)) { // camels dash instead of jumping
                lines.add(Component.translatable("dvlens.horse.jump", format(jumpHeight(horse.getCustomJump()))).withStyle(ChatFormatting.AQUA));
            }
        }
        if (LensConfig.SHOW_MOD_NAME.get()) {
            lines.add(modName(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
        }
        return new LensInfo(icon, lines);
    }

    /** Movement speed attribute to blocks per second (factor from the Minecraft Wiki). */
    private static float horseSpeed(AbstractHorse horse) {
        return (float) (horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * 42.16);
    }

    /** Jump strength attribute to jump height in blocks (fitted curve from the Minecraft Wiki). */
    private static float jumpHeight(double strength) {
        return (float) (-0.1817584952 * strength * strength * strength
                + 3.689713992 * strength * strength
                + 2.128599134 * strength
                - 0.343930367);
    }

    /** Crops and similar blocks expose their growth as an "age" property. */
    private static Component growth(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                if (max == 0) return null;
                int value = state.getValue(age);
                if (value >= max) return Component.translatable("dvlens.growth.mature").withStyle(ChatFormatting.GREEN);
                return Component.translatable("dvlens.growth", value * 100 / max).withStyle(ChatFormatting.GRAY);
            }
        }
        return null;
    }

    private static Component harvest(ClientLevel level, LocalPlayer player, BlockPos pos, BlockState state) {
        if (state.getDestroySpeed(level, pos) < 0) {
            return Component.translatable("dvlens.harvest.unbreakable").withStyle(ChatFormatting.DARK_GRAY);
        }
        // Only blocks that drop nothing without the right tool (stone, ores...) get a line.
        if (!state.requiresCorrectToolForDrops()) return null;

        String tool = toolKey(state);
        MutableComponent line = tool != null
                ? Component.translatable("dvlens.harvest.min", Component.translatable("dvlens.tool." + tool + "." + tierKey(state)))
                : Component.translatable("dvlens.tool.any");

        boolean ok = player.hasCorrectToolForDrops(state);
        return Component.literal(ok ? "✔ " : "✘ ").append(line)
                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static String toolKey(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return "pickaxe";
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return "axe";
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return "shovel";
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return "hoe";
        return null;
    }

    private static String tierKey(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return "diamond";
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return "iron";
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return "stone";
        return "wood";
    }

    private static Component modName(ResourceLocation id) {
        String namespace = id == null ? "minecraft" : id.getNamespace();
        String name = ModList.get().getModContainerById(namespace)
                .map(c -> c.getModInfo().getDisplayName())
                .orElse(namespace);
        return Component.literal(name).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC);
    }

    private static String format(float value) {
        return value == (int) value ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
