package com.darkvolfi.dvsouls;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = DVSouls.MOD_ID)
public final class SoulEvents {
    /** What each of a soul's hearts adds to a horse; how far it can go is a server setting. */
    private static final double SPEED_PER_HEART = 0.003, JUMP_PER_HEART = 0.01, HEALTH_PER_HEART = 0.5;

    private SoulEvents() {}

    /** Runs before the creature's own right-click (a villager's trades, milking…), so jars and collars come first. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!(event.getTarget() instanceof LivingEntity target) || target instanceof Player || !target.isAlive()) return;
        ItemStack held = event.getItemStack();
        InteractionResult result = InteractionResult.PASS;
        if (held.is(DVSouls.SOUL_JAR.get())) {
            result = capture(player, event.getHand(), held, target);
        } else if (held.is(DVSouls.SOUL_COLLAR.get())) {
            result = collar(player, held, target);
        } else if (held.is(DVSouls.FILLED_SOUL_JAR.get()) && target instanceof AbstractHorse horse) {
            result = strengthen(player, event.getHand(), held, horse);
        } else if (held.isEmpty() && event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
                && target instanceof Mob mob && Pets.ownedBy(mob, player)) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable(Pets.nextMode(mob).key(), mob.getName()), true);
            }
            result = InteractionResult.SUCCESS;
        }
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private static boolean uncapturable(Entity target) {
        return target.getType().is(DVSouls.UNCAPTURABLE)
                || SoulsConfig.UNCAPTURABLE.get().contains(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(target.getType())));
    }

    private static InteractionResult capture(Player player, InteractionHand hand, ItemStack jar, LivingEntity target) {
        if (uncapturable(target)) return deny(player, "dvsouls.capture.too_strong");
        UUID owner = Pets.anyOwner(target);
        if (owner != null && !owner.equals(player.getUUID())) return deny(player, "dvsouls.capture.not_yours");
        if (player.level() instanceof ServerLevel level) {
            target.ejectPassengers();
            target.stopRiding();
            ItemStack filled = Soul.put(new ItemStack(DVSouls.FILLED_SOUL_JAR.get()), Soul.of(target));
            level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(0.5), target.getZ(), 20,
                    target.getBbWidth() / 2, target.getBbHeight() / 3, target.getBbWidth() / 2, 0.02);
            level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1, 0.8f);
            target.discard();
            if (!player.getAbilities().instabuild) jar.shrink(1);
            if (jar.isEmpty()) player.setItemInHand(hand, filled);
            else if (!player.getInventory().add(filled)) player.drop(filled, false);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A collar goes on any creature that has no owner (an enemy too) or is the player's own, except the ones no jar can
     * hold. It lets the creature come back once after death, and only for the one who put it on.
     */
    private static InteractionResult collar(Player player, ItemStack collar, LivingEntity target) {
        if (!(target instanceof Mob mob) || uncapturable(mob)) return deny(player, "dvsouls.collar.cannot");
        UUID owner = Pets.anyOwner(mob);
        if (owner != null && !owner.equals(player.getUUID())) return deny(player, "dvsouls.capture.not_yours");
        if (Pets.collared(mob)) return deny(player, "dvsouls.collar.already");
        if (player.level() instanceof ServerLevel level) {
            Pets.putCollar(mob, player);
            if (!player.getAbilities().instabuild) collar.shrink(1);
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY(1), mob.getZ(), 6, 0.3, 0.3, 0.3, 0);
            player.displayClientMessage(Component.translatable("dvsouls.collar.bound", mob.getName()), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** A soul given to a horse makes it faster, jump higher and tougher, by the soul's hearts. */
    private static InteractionResult strengthen(Player player, InteractionHand hand, ItemStack jar, AbstractHorse horse) {
        if (!horse.isTamed()) return deny(player, "dvsouls.horse.untamed");
        CompoundTag soul = Soul.get(jar);
        if (soul == null) return InteractionResult.PASS;
        if (player.level() instanceof ServerLevel level) {
            float hearts = Soul.hearts(soul);
            boolean changed = raise(horse, Attributes.MOVEMENT_SPEED, hearts * SPEED_PER_HEART, SoulsConfig.HORSE_SPEED_MAX.get())
                    | raise(horse, Attributes.JUMP_STRENGTH, hearts * JUMP_PER_HEART, SoulsConfig.HORSE_JUMP_MAX.get())
                    | raise(horse, Attributes.MAX_HEALTH, hearts * HEALTH_PER_HEART, SoulsConfig.HORSE_HEALTH_MAX.get());
            if (!changed) return deny(player, "dvsouls.horse.maxed");
            horse.heal(horse.getMaxHealth());
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, horse.getX(), horse.getY(0.6), horse.getZ(), 25, 0.5, 0.5, 0.5, 0.02);
            level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_ARMOR, SoundSource.PLAYERS, 1, 1.2f);
            player.displayClientMessage(Component.translatable("dvsouls.horse.stronger",
                    String.format(Locale.ROOT, "%.1f", horse.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * 43.17),
                    String.format(Locale.ROOT, "%.2f", horse.getAttributeBaseValue(Attributes.JUMP_STRENGTH)),
                    String.format(Locale.ROOT, "%.0f", horse.getMaxHealth() / 2)), true);
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(DVSouls.SOUL_JAR.get()));
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean raise(LivingEntity entity, Attribute attribute, double amount, double max) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null || instance.getBaseValue() >= max) return false;
        instance.setBaseValue(Math.min(max, instance.getBaseValue() + amount));
        return true;
    }

    private static InteractionResult deny(Player player, String key) {
        if (!player.level().isClientSide) player.displayClientMessage(Component.translatable(key), true);
        return InteractionResult.FAIL;
    }

    /** A bound undead pet doesn't burn in the sun: the fire the sun sets it on is put out before it can hurt. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !entity.isOnFire() || entity.getMobType() != MobType.UNDEAD || !Pets.isPet(entity)) return;
        if (!entity.isInLava() && entity.level().isDay() && entity.level().canSeeSky(entity.blockPosition())) entity.clearFire();
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Mob mob && Pets.isPet(mob)) Pets.addGoals(mob);
    }

    /** Whose side a creature is on decides whom it may target. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        if (target == null) return;
        LivingEntity attacker = event.getEntity();
        // No pet or tamed creature turns on its own side, and iron and snow golems leave bound pets alone, monsters or not.
        if (Pets.anyOwner(attacker) != null && Pets.sameSide(attacker, target)) event.setCanceled(true);
        if (attacker instanceof AbstractGolem && Pets.isPet(target)) event.setCanceled(true);
    }

    /** A collared pet that dies leaves a shard of its collar, which keeps its soul (healed) and never despawns; the Soul Altar brings it back. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!Pets.collared(entity) || !(entity.level() instanceof ServerLevel level)) return;
        UUID owner = Pets.collarOwner(entity);
        CompoundTag soul = Soul.of(entity);
        Pets.spendCollar(soul, owner); // one use: whatever is brought back from this shard has no collar
        ItemStack shard = Soul.put(new ItemStack(DVSouls.COLLAR_SHARD.get()), Soul.healed(soul));
        ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY(0.5), entity.getZ(), shard);
        drop.setUnlimitedLifetime();
        drop.setInvulnerable(true);
        level.addFreshEntity(drop);
        Player player = owner == null ? null : level.getPlayerByUUID(owner);
        if (player != null) player.sendSystemMessage(Component.translatable("dvsouls.pet.died", entity.getName()));
    }
}
