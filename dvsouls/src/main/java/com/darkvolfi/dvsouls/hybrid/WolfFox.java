package com.darkvolfi.dvsouls.hybrid;

import com.darkvolfi.dvsouls.DVSouls;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Wolf and fox: looks like a fox, behaves like a wolf, tamed with bones and as good in a fight. It carries things in its
 * mouth like a fox: a tame one that isn't sitting fetches items lying near and brings them to its owner, and eats food.
 */
public class WolfFox extends Wolf {
    public WolfFox(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
        // Whatever it carries is never lost with it.
        setDropChance(EquipmentSlot.MAINHAND, 2);
    }

    /** Ticks spent chewing what it holds; food is swallowed after {@link #EAT_TIME}. */
    private int chewing;
    private static final int EAT_TIME = 60;

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(5, new Fetch(this));
    }

    /**
     * Food it holds in its mouth it eats, as a fox does: it chews for a few seconds, with the sound and crumbs of
     * eating, then swallows and heals by what the food is worth.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        ItemStack held = getMainHandItem();
        if (level().isClientSide || !held.isEdible()) {
            chewing = 0;
            return;
        }
        chewing++;
        if (chewing % 5 == 0 && level() instanceof ServerLevel server) {
            Vec3 mouth = position().add(getLookAngle().scale(0.5)).add(0, getEyeHeight() - 0.1, 0);
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, held), mouth.x, mouth.y, mouth.z, 4, 0.05, 0.05, 0.05, 0.05);
            playSound(getEatingSound(held), 0.6f, 0.9f + random.nextFloat() * 0.2f);
        }
        if (chewing >= EAT_TIME) {
            var food = held.getFoodProperties(this);
            heal(food == null ? 1 : food.getNutrition());
            held.shrink(1);
            setItemSlot(EquipmentSlot.MAINHAND, held.isEmpty() ? ItemStack.EMPTY : held);
            chewing = 0;
        }
    }

    @Override
    public WolfFox getBreedOffspring(ServerLevel level, AgeableMob partner) {
        WolfFox pup = DVSouls.WOLF_FOX.get().create(level);
        if (pup != null && isTame()) {
            pup.setOwnerUUID(getOwnerUUID());
            pup.setTame(true);
        }
        return pup;
    }

    /** Picks up the nearest item lying around, then takes it to the owner and hands it over. */
    private static final class Fetch extends Goal {
        private static final double RANGE = 10;
        private final WolfFox fox;
        private ItemEntity item;
        private int repath;

        Fetch(WolfFox fox) {
            this.fox = fox;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private LivingEntity owner() {
            return fox.getOwner();
        }

        @Override
        public boolean canUse() {
            if (!fox.isTame() || fox.isOrderedToSit() || fox.getTarget() != null || owner() == null) return false;
            // Food it is carrying it eats rather than brings back.
            if (!fox.getMainHandItem().isEmpty()) return !fox.getMainHandItem().isEdible();
            List<ItemEntity> items = fox.level().getEntitiesOfClass(ItemEntity.class, fox.getBoundingBox().inflate(RANGE),
                    e -> e.isAlive() && !e.hasPickUpDelay() && !e.getItem().isEmpty());
            item = items.stream().min(Comparator.comparingDouble(fox::distanceToSqr)).orElse(null);
            return item != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (!fox.isTame() || fox.isOrderedToSit() || fox.getTarget() != null || owner() == null) return false;
            ItemStack held = fox.getMainHandItem();
            return held.isEmpty() ? item != null && item.isAlive() : !held.isEdible();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            item = null;
            fox.getNavigation().stop();
        }

        @Override
        public void tick() {
            ItemStack carried = fox.getMainHandItem();
            if (carried.isEmpty()) {
                fox.getLookControl().setLookAt(item, 30, 30);
                if (fox.distanceToSqr(item) < 2.25) {
                    fox.setItemSlot(EquipmentSlot.MAINHAND, item.getItem().copy());
                    item.discard();
                    item = null;
                } else if (--repath <= 0) {
                    repath = 10;
                    fox.getNavigation().moveTo(item, 1.2);
                }
                return;
            }
            LivingEntity owner = owner();
            fox.getLookControl().setLookAt(owner, 30, 30);
            if (fox.distanceToSqr(owner) < 4) {
                if (!(owner instanceof Player player) || !player.getInventory().add(carried)) fox.spawnAtLocation(carried);
                fox.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            } else if (--repath <= 0) {
                repath = 10;
                fox.getNavigation().moveTo(owner, 1.2);
            }
        }
    }
}
