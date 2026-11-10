package com.darkvolfi.dvsouls;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

/**
 * Creatures bound to a player at the Binding Altar. Any creature can be bound, so the pet's behaviour is added to its
 * own goals: it follows its owner, sits, or wanders about on its own, and one that can fight takes on whoever hurts its
 * owner or whom its owner hits. It never turns on its owner or the owner's other pets (see {@link SoulEvents}).
 * A pet (or a creature tamed the usual way) that also wears a soul collar leaves a shard of it when it dies.
 */
public final class Pets {
    private static final String OWNER = "dvsouls:owner";
    private static final String MODE = "dvsouls:mode";
    private static final String COLLARED = "dvsouls:collared";
    private static final String COLLAR_OWNER = "dvsouls:collar_owner";

    private Pets() {}

    public static UUID owner(Entity entity) {
        var data = entity.getPersistentData();
        return data.hasUUID(OWNER) ? data.getUUID(OWNER) : null;
    }

    public static boolean isPet(Entity entity) {
        return owner(entity) != null;
    }

    public static boolean ownedBy(Entity entity, Entity player) {
        return player.getUUID().equals(owner(entity));
    }

    /** The owner of a bound pet or of a creature tamed the usual way (a wolf, a cat, a horse...). */
    public static UUID anyOwner(Entity entity) {
        UUID owner = owner(entity);
        if (owner != null) return owner;
        return entity instanceof OwnableEntity ownable ? ownable.getOwnerUUID() : null;
    }

    /** The owner kept in a soul: its creature's bound owner, or the one it was tamed by. */
    public static UUID owner(CompoundTag soul) {
        if (soul == null) return null;
        CompoundTag data = soul.getCompound("Data");
        CompoundTag forge = data.getCompound("ForgeData");
        if (forge.hasUUID(OWNER)) return forge.getUUID(OWNER);
        return data.hasUUID("Owner") ? data.getUUID("Owner") : null;
    }

    public static boolean collared(Entity entity) {
        return entity.getPersistentData().getBoolean(COLLARED);
    }

    /** Puts the collar on, remembering who put it on: only they can lay the shard on a Soul Altar. */
    public static void putCollar(Entity entity, Player wearer) {
        entity.getPersistentData().putBoolean(COLLARED, true);
        entity.getPersistentData().putUUID(COLLAR_OWNER, wearer.getUUID());
    }

    /** Whose collar it wears: whoever put it on, or for an older collar the creature's own owner. */
    public static UUID collarOwner(Entity entity) {
        var data = entity.getPersistentData();
        return data.hasUUID(COLLAR_OWNER) ? data.getUUID(COLLAR_OWNER) : anyOwner(entity);
    }

    /**
     * A collar is spent when its creature dies: the soul kept for the shard no longer wears it, so a creature brought
     * back comes without one. The one it belonged to is kept beside the soul, for the altar.
     */
    public static void spendCollar(CompoundTag soul, UUID collarOwner) {
        CompoundTag forge = soul.getCompound("Data").getCompound("ForgeData");
        forge.remove(COLLARED);
        forge.remove(COLLAR_OWNER);
        if (collarOwner != null) soul.putUUID(COLLAR_OWNER, collarOwner);
    }

    /** Who may lay a shard's soul on the altar: the one whose collar it wore, else (older shards) its owner. */
    public static UUID shardOwner(CompoundTag soul) {
        if (soul == null) return null;
        return soul.hasUUID(COLLAR_OWNER) ? soul.getUUID(COLLAR_OWNER) : owner(soul);
    }

    /** Both are this player's: the player, or a pet or tamed creature of theirs. */
    public static boolean sameSide(Entity a, Entity b) {
        UUID ownerA = a instanceof Player ? a.getUUID() : anyOwner(a);
        UUID ownerB = b instanceof Player ? b.getUUID() : anyOwner(b);
        return ownerA != null && ownerA.equals(ownerB);
    }

    public static void bind(Mob mob, Player player) {
        mob.getPersistentData().putUUID(OWNER, player.getUUID());
        mob.setPersistenceRequired();
        mob.setTarget(null);
        addGoals(mob);
    }

    /** What a pet does when it isn't fighting; its owner switches between them (see {@link SoulEvents}). */
    public enum Mode {
        FOLLOW, SIT, WANDER;

        public String key() {
            return "dvsouls.pet." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static Mode mode(Entity entity) {
        int index = entity.getPersistentData().getInt(MODE);
        return Mode.values()[Math.floorMod(index, Mode.values().length)];
    }

    /** Switches to the next mode: follow, sit, wander, and back. */
    public static Mode nextMode(Mob mob) {
        Mode next = Mode.values()[(mode(mob).ordinal() + 1) % Mode.values().length];
        mob.getPersistentData().putInt(MODE, next.ordinal());
        mob.getNavigation().stop();
        return next;
    }

    static boolean sitting(Entity entity) {
        return mode(entity) == Mode.SIT;
    }

    private static Player ownerPlayer(Mob mob) {
        UUID owner = owner(mob);
        return owner == null ? null : mob.level().getPlayerByUUID(owner);
    }

    /** Adds the pet's goals, once; called when a pet joins the world and when it is bound. */
    public static void addGoals(Mob mob) {
        if (!(mob instanceof PathfinderMob creature)) return;
        if (mob.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof Sit)) return;
        mob.goalSelector.addGoal(0, new Sit(mob));
        mob.goalSelector.addGoal(2, new Follow(creature));
        if (mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
            mob.targetSelector.addGoal(0, new Defend(mob, true));
            mob.targetSelector.addGoal(1, new Defend(mob, false));
        }
    }

    /** Stays put while told to sit. */
    private static final class Sit extends Goal {
        private final Mob mob;

        Sit(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return sitting(mob);
        }

        @Override
        public void start() {
            mob.getNavigation().stop();
        }
    }

    /** Walks after the owner when they get far, and catches up by teleporting when they get very far. */
    private static final class Follow extends Goal {
        private static final double START = 7;
        private static final double STOP = 3;
        private static final double TELEPORT = 20;
        private final PathfinderMob mob;
        private Player owner;
        private int repath;

        Follow(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mode(mob) != Mode.FOLLOW || mob.isLeashed() || mob.isPassenger() || mob.getTarget() != null) return false;
            owner = ownerPlayer(mob);
            return owner != null && !owner.isSpectator() && mob.distanceToSqr(owner) > START * START;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && mode(mob) == Mode.FOLLOW && mob.getTarget() == null
                    && mob.distanceToSqr(owner) > STOP * STOP;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            owner = null;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(owner, 10, mob.getMaxHeadXRot());
            if (--repath > 0) return;
            repath = 10;
            if (mob.distanceToSqr(owner) > TELEPORT * TELEPORT && owner.onGround()) {
                mob.moveTo(owner.getX(), owner.getY(), owner.getZ(), mob.getYRot(), mob.getXRot());
                mob.getNavigation().stop();
            } else {
                mob.getNavigation().moveTo(owner, 1.2);
            }
        }
    }

    /** Targets whoever hurt the owner ({@code byOwner} false) or whomever the owner hit ({@code byOwner} true). */
    private static final class Defend extends TargetGoal {
        private final boolean byOwner;
        private LivingEntity enemy;
        private int lastStamp;

        Defend(Mob mob, boolean byOwner) {
            super(mob, false);
            this.byOwner = byOwner;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (sitting(mob)) return false;
            Player owner = ownerPlayer(mob);
            if (owner == null) return false;
            enemy = byOwner ? owner.getLastHurtMob() : owner.getLastHurtByMob();
            int stamp = byOwner ? owner.getLastHurtMobTimestamp() : owner.getLastHurtByMobTimestamp();
            return stamp != lastStamp && enemy != null && !sameSide(enemy, owner)
                    && canAttack(enemy, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(enemy);
            Player owner = ownerPlayer(mob);
            if (owner != null) lastStamp = byOwner ? owner.getLastHurtMobTimestamp() : owner.getLastHurtByMobTimestamp();
            super.start();
        }
    }
}
