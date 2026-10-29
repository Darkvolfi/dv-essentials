package com.darkvolfi.dvsouls.charm;

import com.darkvolfi.dvsouls.DVSouls;
import com.darkvolfi.dvsouls.compat.BackpackCharms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gives players the abilities of the essences on their soul charms. The server counts the charms every few ticks and
 * keeps the status effects up; the rest happens here when it comes up. Climbing and lava walking move the player, which
 * the client does, so they are status effects the client sees and both sides act on.
 */
@Mod.EventBusSubscriber(modid = DVSouls.MOD_ID)
public final class Charms {
    private static final boolean BACKPACK = ModList.get().isLoaded("dvbackpack");
    private static final int UPDATE_TICKS = 5;
    /** Long enough to last until the next update; night vision flickers when it has less than 10 seconds left. */
    private static final int DURATION = 30, NIGHT_VISION_DURATION = 260;
    private static final UUID KNOCKBACK = UUID.fromString("5b0e2b9e-6c51-4d3a-9a4e-2f7b1c9d0a11");

    /** Each online player's ability levels, from their last count. Server side. */
    private static final Map<UUID, Map<Ability, Integer>> LEVELS = new HashMap<>();
    /** Each player's hunger exhaustion at the last update, to slow its growth (the woolly cow). */
    private static final Map<UUID, Float> EXHAUSTION = new HashMap<>();

    private Charms() {}

    /** The player's level of this ability: how many of its essences they carry, up to {@link Ability#MAX_LEVEL}. */
    public static int level(Player player, Ability ability) {
        return LEVELS.getOrDefault(player.getUUID(), Map.of()).getOrDefault(ability, 0);
    }

    /** The charms that count: in the worn backpack's upgrade slots with DV Backpack, else anywhere in the inventory. */
    private static List<ItemStack> charms(ServerPlayer player) {
        if (BACKPACK) return BackpackCharms.charms(player);
        List<ItemStack> charms = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof SoulCharm) charms.add(inventory.getItem(i));
        }
        return charms;
    }

    private static Map<Ability, Integer> count(List<ItemStack> charms) {
        Map<Ability, Integer> levels = new EnumMap<>(Ability.class);
        for (ItemStack charm : charms) {
            for (var type : SoulCharm.essences(charm)) {
                Ability ability = Ability.of(type);
                if (ability != null) levels.merge(ability, 1, (a, b) -> Math.min(Ability.MAX_LEVEL, a + b));
            }
        }
        return levels;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase == TickEvent.Phase.START) {
            move(player);
            return;
        }
        if (!(player instanceof ServerPlayer server)) return;
        Map<Ability, Integer> levels = LEVELS.get(player.getUUID());
        if (player.tickCount % UPDATE_TICKS == 0) {
            levels = count(charms(server));
            LEVELS.put(player.getUUID(), levels);
            update(server, levels);
        }
        if (levels != null && levels.containsKey(Ability.WOLF_FOX)) pullItems(server, levels.get(Ability.WOLF_FOX));
    }

    /** The status effects, knockback and hunger, after a count. */
    private static void update(ServerPlayer player, Map<Ability, Integer> levels) {
        Map<MobEffect, Integer> effects = new HashMap<>();
        levels.forEach((ability, level) -> {
            if (ability.effect() != null) effects.merge(ability.effect(), level, Math::max);
        });
        if (levels.containsKey(Ability.LAVA_HORSE)) effects.put(DVSouls.LAVA_WALKING.get(), 1);
        // the squid also sees in the dark, under water
        if (levels.containsKey(Ability.SQUID) && player.isEyeInFluid(FluidTags.WATER)) effects.merge(MobEffects.NIGHT_VISION, 1, Math::max);
        boolean feathers = levels.containsKey(Ability.CHICKEN) || levels.containsKey(Ability.FEATHERED_PIG);
        if (feathers && !player.isShiftKeyDown()) {
            effects.put(MobEffects.SLOW_FALLING, 1);
        } else {
            // sneaking drops like a stone: take off the slow falling a charm gave (not a potion's)
            MobEffectInstance slow = player.getEffect(MobEffects.SLOW_FALLING);
            if (slow != null && slow.isAmbient() && slow.getDuration() <= DURATION) player.removeEffect(MobEffects.SLOW_FALLING);
        }
        effects.forEach((effect, level) -> player.addEffect(new MobEffectInstance(effect,
                effect == MobEffects.NIGHT_VISION ? NIGHT_VISION_DURATION : DURATION, level - 1, true, false, true)));

        AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        double resist = Math.min(1, 0.35 * levels.getOrDefault(Ability.IRON_GOLEM, 0));
        AttributeModifier current = knockback.getModifier(KNOCKBACK);
        if (current == null ? resist > 0 : current.getAmount() != resist) {
            knockback.removeModifier(KNOCKBACK);
            if (resist > 0) knockback.addTransientModifier(new AttributeModifier(KNOCKBACK, "dvsouls charm", resist, AttributeModifier.Operation.ADDITION));
        }

        // The woolly cow: of what was added to exhaustion since the last update, take back a share.
        float exhaustion = player.getFoodData().getExhaustionLevel();
        Float last = EXHAUSTION.get(player.getUUID());
        int cow = levels.getOrDefault(Ability.WOOLLY_COW, 0);
        if (cow > 0 && last != null && exhaustion > last) {
            exhaustion -= (exhaustion - last) * cow / (cow + 1f);
            player.getFoodData().setExhaustion(exhaustion);
        }
        EXHAUSTION.put(player.getUUID(), exhaustion);
    }

    /** The wolf fox: items lying near drift to the player. Ones still too fresh to pick up (just dropped) stay. */
    private static void pullItems(ServerPlayer player, int level) {
        double range = 4 + 2 * level;
        Vec3 to = player.position().add(0, 0.5, 0);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(range),
                item -> !item.hasPickUpDelay())) {
            item.setDeltaMovement(to.subtract(item.position()).normalize().scale(0.35));
        }
    }

    /** Climbing walls and walking on lava; both sides, before the player moves. */
    private static void move(Player player) {
        Vec3 motion = player.getDeltaMovement();
        if (player.hasEffect(DVSouls.CLIMBING.get()) && player.horizontalCollision) {
            player.setDeltaMovement(motion.x, player.isShiftKeyDown() ? 0 : 0.2, motion.z);
            player.resetFallDistance();
        }
        if (player.hasEffect(DVSouls.LAVA_WALKING.get()) && !player.isShiftKeyDown()) {
            if (lava(player, 0.1)) {
                player.setDeltaMovement(motion.x, 0.25, motion.z); // sunk in: rise to the top
            } else if (lava(player, -0.05) && motion.y <= 0) {
                player.setDeltaMovement(motion.x, 0, motion.z); // on the top: stand on it
                player.setOnGround(true);
                player.resetFallDistance();
            }
        }
    }

    private static boolean lava(Player player, double dy) {
        return player.level().getFluidState(BlockPos.containing(player.getX(), player.getY() + dy, player.getZ())).is(FluidTags.LAVA);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && level(player, Ability.FEATHERED_PIG) > 0) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int puffer = level(player, Ability.PUFFERFISH);
        if (puffer > 0 && event.getSource().getDirectEntity() instanceof LivingEntity attacker && attacker != player) {
            attacker.addEffect(new MobEffectInstance(MobEffects.POISON, 60 * puffer), player);
        }
        int ender = level(player, Ability.ENDERMAN);
        if (ender > 0 && event.getSource().getEntity() != null && player.getRandom().nextFloat() < 0.15f * ender) teleport(player);
    }

    /** Like eating a chorus fruit: somewhere safe within 8 blocks. */
    private static void teleport(ServerPlayer player) {
        double x = player.getX(), y = player.getY(), z = player.getZ();
        for (int i = 0; i < 16; i++) {
            double tx = x + (player.getRandom().nextDouble() - 0.5) * 16;
            double ty = Mth.clamp(y + player.getRandom().nextInt(16) - 8, player.level().getMinBuildHeight(),
                    player.level().getMinBuildHeight() + ((net.minecraft.server.level.ServerLevel) player.level()).getLogicalHeight() - 1);
            double tz = z + (player.getRandom().nextDouble() - 0.5) * 16;
            if (player.isPassenger()) player.stopRiding();
            if (player.randomTeleport(tx, ty, tz, true)) {
                player.level().playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
                player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1, 1);
                player.resetFallDistance();
                return;
            }
        }
    }

    /** The cat: every creeper keeps away from a player carrying its essence. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Creeper creeper) {
            creeper.goalSelector.addGoal(3, new AvoidEntityGoal<>(creeper, Player.class, 8, 1, 1.2,
                    entity -> entity instanceof Player player && level(player, Ability.CAT) > 0));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LEVELS.remove(event.getEntity().getUUID());
        EXHAUSTION.remove(event.getEntity().getUUID());
    }
}
