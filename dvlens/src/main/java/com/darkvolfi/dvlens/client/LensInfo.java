package com.darkvolfi.dvlens.client;

import com.darkvolfi.dvlens.LensConfig;
import com.darkvolfi.dvlens.network.BlockInfoReply;
import com.darkvolfi.dvlens.network.EntityInfoReply;
import com.darkvolfi.dvlens.network.MachineInfo;
import com.darkvolfi.dvlib.client.EffectText;
import com.darkvolfi.dvlib.client.MobFace;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What the info box shows: an icon (item, or the entity's face), a list of lines and, for blocks that need a
 * tool, that tool with a tick or cross for the held item, plus a row of item icons (container contents,
 * armor stand gear). Built from client-side data, plus what the server
 * sent for the looked-at entity or block when it has DV Lens.
 */
record LensInfo(ItemStack icon, LivingEntity face, List<Component> lines, ItemStack tool, boolean toolOk, List<ItemStack> items) {
    /** Note block note 0 is F♯; the names repeat every octave. */
    private static final String[] NOTE_NAMES = {"F♯", "G", "G♯", "A", "A♯", "B", "C", "C♯", "D", "D♯", "E", "F"};
    private static final int MAX_COMMAND = 40;

    static LensInfo lookAt(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit == null || mc.level == null || mc.player == null) return null;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return forBlock(mc.level, mc.player, blockHit);
        }
        if (hit instanceof EntityHitResult entityHit) {
            return forEntity(mc, entityHit.getEntity());
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
        if (LensConfig.SHOW_REDSTONE.get()) redstone(level, pos, state, lines);
        if (LensConfig.SHOW_SPAWNER.get() && level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            Entity mob = spawner.getSpawner().getOrCreateDisplayEntity(level, level.getRandom(), pos);
            if (mob != null) {
                lines.add(Component.translatable("dvlens.spawner", mob.getType().getDescription()).withStyle(ChatFormatting.GRAY));
            }
        }
        BlockInfoReply server = ServerInfo.get(pos);
        if (LensConfig.SHOW_DETAILS.get()) blockDetails(state, server, lines);
        List<ItemStack> items = List.of();
        if (LensConfig.SHOW_CONTENTS.get() && server != null) {
            items = server.items();
            progressLines(server, lines);
        }
        if (LensConfig.SHOW_MACHINES.get() && server != null) machineLines(server.machine(), lines);

        ItemStack tool = ItemStack.EMPTY;
        boolean toolOk = false;
        if (LensConfig.SHOW_HARVEST.get()) {
            if (state.getDestroySpeed(level, pos) < 0) {
                lines.add(Component.translatable("dvlens.harvest.unbreakable").withStyle(ChatFormatting.DARK_GRAY));
            } else if (state.requiresCorrectToolForDrops()) {
                // Only blocks that drop nothing without the right tool (stone, ores...) get a tool icon.
                toolOk = player.hasCorrectToolForDrops(state);
                Item item = minimumTool(state);
                if (item != null) {
                    tool = new ItemStack(item);
                } else {
                    lines.add(Component.translatable("dvlens.tool.any").withStyle(toolOk ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
            }
        }
        if (LensConfig.SHOW_MOD_NAME.get()) {
            lines.add(modName(ForgeRegistries.BLOCKS.getKey(state.getBlock())));
        }
        return new LensInfo(icon, null, lines, tool, toolOk, items);
    }

    private static LensInfo forEntity(Minecraft mc, Entity entity) {
        List<Component> lines = new ArrayList<>();
        ItemStack icon = ItemStack.EMPTY;
        LivingEntity face = null;

        if (entity instanceof ItemEntity item) {
            icon = item.getItem();
            lines.add(icon.getHoverName().copy().append(" ×" + icon.getCount()));
        } else {
            lines.add(entity.getDisplayName());
            if (LensConfig.SHOW_FACE.get() && entity instanceof LivingEntity living && MobFace.canDraw(living)) {
                face = living;
            } else {
                SpawnEggItem egg = ForgeSpawnEggItem.fromEntityType(entity.getType());
                if (egg == null) egg = SpawnEggItem.byId(entity.getType());
                if (egg != null) icon = new ItemStack(egg);
            }
        }

        EntityInfoReply server = ServerInfo.get(entity);
        List<ItemStack> items = List.of();
        if (LensConfig.SHOW_DETAILS.get()) {
            entityDetails(entity, lines);
            if (entity instanceof ItemFrame frame && !frame.getItem().isEmpty()) icon = frame.getItem();
            if (entity instanceof ArmorStand stand) {
                List<ItemStack> gear = new ArrayList<>();
                for (ItemStack stack : stand.getAllSlots()) if (!stack.isEmpty()) gear.add(stack);
                items = gear;
            }
        }
        if (LensConfig.SHOW_VILLAGER.get() && entity instanceof VillagerDataHolder villager) {
            lines.add(villagerLine(villager.getVillagerData()));
        }
        if (LensConfig.SHOW_HEALTH.get() && entity instanceof LivingEntity living) {
            lines.add(Component.literal("❤ " + format(living.getHealth()) + " / " + format(living.getMaxHealth()))
                    .withStyle(ChatFormatting.RED));
        }
        if (LensConfig.SHOW_EFFECTS.get() && server != null) {
            for (MobEffectInstance effect : server.effects()) lines.add(EffectText.line(effect, 1.0f));
        }
        if (LensConfig.SHOW_GROWTH.get() && entity instanceof AgeableMob mob) {
            Component age = ageLine(mob, server);
            if (age != null) lines.add(age);
        }
        if (LensConfig.SHOW_OWNER.get() && entity instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            String owner = ownerName(mc, pet.getOwnerUUID(), server);
            lines.add((owner.isEmpty() ? Component.translatable("dvlens.tamed") : Component.translatable("dvlens.owner", owner))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
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
        return new LensInfo(icon, face, lines, ItemStack.EMPTY, false, items);
    }

    private static void blockDetails(BlockState state, BlockInfoReply server, List<Component> lines) {
        if (state.getBlock() instanceof NoteBlock) {
            int note = state.getValue(NoteBlock.NOTE);
            String instrument = state.getValue(NoteBlock.INSTRUMENT).getSerializedName();
            lines.add(Component.translatable("dvlens.note", NOTE_NAMES[note % 12] + " (" + note + ")",
                    Component.translatable("dvlens.instrument." + instrument)).withStyle(ChatFormatting.GRAY));
        } else if (state.getBlock() instanceof TntBlock && state.getValue(TntBlock.UNSTABLE)) {
            lines.add(Component.translatable("dvlens.tnt.unstable").withStyle(ChatFormatting.RED));
        } else if (state.getBlock() instanceof BeehiveBlock) {
            lines.add(Component.translatable("dvlens.honey", state.getValue(BeehiveBlock.HONEY_LEVEL), BeehiveBlock.MAX_HONEY_LEVELS)
                    .withStyle(ChatFormatting.GOLD));
            if (server != null && server.bees() >= 0) {
                lines.add(Component.translatable("dvlens.bees", server.bees(), BeehiveBlockEntity.MAX_OCCUPANTS).withStyle(ChatFormatting.YELLOW));
            }
        }
        if (server != null && !server.command().isEmpty()) {
            String command = server.command();
            if (command.length() > MAX_COMMAND) command = command.substring(0, MAX_COMMAND) + "…";
            lines.add(Component.literal(command).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Furnace cooking / brewing progress and the fuel left. */
    private static void progressLines(BlockInfoReply server, List<Component> lines) {
        if (server.progressMax() > 0 && server.progress() > 0) {
            lines.add(Component.translatable("dvlens.progress", server.progress() * 100 / server.progressMax()).withStyle(ChatFormatting.GRAY));
        }
        if (server.fuel() > 0) {
            Object fuel = server.fuelMax() > 0 ? server.fuel() + "/" + server.fuelMax() : StringUtil.formatTickDuration(server.fuel());
            lines.add(Component.translatable("dvlens.fuel", fuel).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Forge Energy, fluid tanks and Create rotation / stress, for any mod's machines. */
    private static void machineLines(MachineInfo machine, List<Component> lines) {
        NumberFormat numbers = NumberFormat.getIntegerInstance();
        if (machine.energyMax() > 0) {
            lines.add(Component.translatable("dvlens.energy", numbers.format(machine.energy()), numbers.format(machine.energyMax()))
                    .withStyle(ChatFormatting.RED));
        }
        for (MachineInfo.Tank tank : machine.tanks()) {
            Component fluid = tank.fluid().isEmpty() ? Component.translatable("dvlens.fluid.empty") : tank.fluid().getDisplayName();
            lines.add(Component.translatable("dvlens.fluid", fluid, numbers.format(tank.fluid().getAmount()), numbers.format(tank.capacity()))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (!Float.isNaN(machine.rpm())) {
            lines.add(Component.translatable("dvlens.create.speed", format(Math.abs(machine.rpm()))).withStyle(ChatFormatting.GOLD));
            if (machine.capacity() > 0) {
                boolean overstressed = machine.stress() > machine.capacity();
                lines.add(Component.translatable("dvlens.create.stress", numbers.format(machine.stress()), numbers.format(machine.capacity()))
                        .withStyle(overstressed ? ChatFormatting.RED : ChatFormatting.GRAY));
            }
        }
    }

    /** Item frame item, painting title and artist, potion effects from the server. */
    private static void entityDetails(Entity entity, List<Component> lines) {
        if (entity instanceof ItemFrame frame && !frame.getItem().isEmpty()) {
            lines.add(frame.getItem().getHoverName().copy().withStyle(ChatFormatting.GRAY));
        } else if (entity instanceof Painting painting) {
            painting.getVariant().unwrapKey().ifPresent(key -> {
                String base = "painting." + key.location().getNamespace() + "." + key.location().getPath();
                lines.add(Component.translatable(base + ".title").withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable(base + ".author").withStyle(ChatFormatting.GRAY));
            });
        }
    }

    /** "Farmer · Expert", named with vanilla's own villager title and trade-screen level keys. */
    private static Component villagerLine(VillagerData data) {
        VillagerProfession profession = data.getProfession();
        if (profession == VillagerProfession.NONE) {
            return Component.translatable("dvlens.villager.unemployed").withStyle(ChatFormatting.GOLD);
        }
        ResourceLocation id = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(profession);
        MutableComponent line = id == null ? Component.literal(profession.name())
                : Component.translatable("entity." + id.getNamespace() + ".villager." + id.getPath());
        if (profession != VillagerProfession.NITWIT) {
            line = line.append(" · ").append(Component.translatable("merchant.level." + data.getLevel()));
        }
        return line.withStyle(ChatFormatting.GOLD);
    }

    /** Babies: time until grown up; adults: breeding cooldown. Exact times only come from the server. */
    private static Component ageLine(AgeableMob mob, EntityInfoReply server) {
        if (server != null && server.age() < 0) {
            return Component.translatable("dvlens.baby.grows", StringUtil.formatTickDuration(-server.age())).withStyle(ChatFormatting.GREEN);
        }
        if (mob.isBaby()) return Component.translatable("dvlens.baby").withStyle(ChatFormatting.GREEN);
        if (server != null && server.age() > 0) {
            return Component.translatable("dvlens.breed.cooldown", StringUtil.formatTickDuration(server.age())).withStyle(ChatFormatting.GRAY);
        }
        return null;
    }

    /** Online owners are in the tab list; for offline ones only the server knows the name. */
    private static String ownerName(Minecraft mc, UUID id, EntityInfoReply server) {
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(id) : null;
        if (info != null) return info.getProfile().getName();
        return server != null ? server.owner() : "";
    }

    private static void redstone(ClientLevel level, BlockPos pos, BlockState state, List<Component> lines) {
        Integer power = null;
        if (state.hasProperty(BlockStateProperties.POWER)) {
            power = state.getValue(BlockStateProperties.POWER);
        } else if (state.isSignalSource() && !(state.getBlock() instanceof ComparatorBlock)) {
            // The comparator's output lives in its block entity, which the client never receives.
            power = 0;
            for (Direction dir : Direction.values()) power = Math.max(power, state.getSignal(level, pos, dir));
        }
        if (power != null) lines.add(Component.translatable("dvlens.redstone.power", power).withStyle(ChatFormatting.RED));

        if (state.getBlock() instanceof RepeaterBlock) {
            MutableComponent delay = Component.translatable("dvlens.redstone.delay", state.getValue(RepeaterBlock.DELAY));
            if (state.getValue(RepeaterBlock.LOCKED)) delay.append(" · ").append(Component.translatable("dvlens.redstone.locked"));
            lines.add(delay.withStyle(ChatFormatting.GRAY));
        } else if (state.getBlock() instanceof ComparatorBlock) {
            boolean subtract = state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT;
            lines.add(Component.translatable(subtract ? "dvlens.redstone.subtract" : "dvlens.redstone.compare").withStyle(ChatFormatting.GRAY));
        } else if (state.getBlock() instanceof ObserverBlock) {
            BlockState watched = level.getBlockState(pos.relative(state.getValue(ObserverBlock.FACING)));
            lines.add(Component.translatable("dvlens.redstone.watches", watched.getBlock().getName()).withStyle(ChatFormatting.GRAY));
        }
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

    /** The weakest vanilla tool that harvests the block, or null when it isn't mined with a tool type we know. */
    private static Item minimumTool(BlockState state) {
        Item[] tiers;
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            tiers = new Item[] {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE};
        } else if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            tiers = new Item[] {Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE};
        } else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            tiers = new Item[] {Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL};
        } else if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            tiers = new Item[] {Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE};
        } else {
            return null;
        }
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return tiers[3];
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return tiers[2];
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return tiers[1];
        return tiers[0];
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
