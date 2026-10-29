package com.darkvolfi.dvsouls;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * Creatures bound to a player with a soul collar. Any creature can wear one, so the pet's behaviour is added to its
 * own goals: it follows its owner, sits, or wanders about on its own, and one that can fight takes on whoever hurts its owner or whom its owner
 * hits. It never turns on its owner or the owner's other pets (see {@link SoulEvents}).
 */
public final class Pets {
    private static final String OWNER = "dvsouls:owner";
    private static final String MODE = "dvsouls:mode";

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

    /** Both are this player's: the player, or a pet of theirs. */
    public static boolean sameSide(Entity a, Entity b) {
        UUID ownerA = a instanceof Player ? a.getUUID() : owner(a);
        UUID ownerB = b instanceof Player ? b.getUUID() : owner(b);
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
