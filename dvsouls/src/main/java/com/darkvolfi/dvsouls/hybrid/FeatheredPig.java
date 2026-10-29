package com.darkvolfi.dvsouls.hybrid;

import com.darkvolfi.dvsouls.DVSouls;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Chicken and pig: a pig with feathers and little wings. It lays eggs like a hen, floats down instead of falling,
 * and drops pork and feathers. It can still be saddled and ridden.
 */
public class FeatheredPig extends Pig {
    private int eggTime;

    public FeatheredPig(EntityType<? extends Pig> type, Level level) {
        super(type, level);
        eggTime = nextEggTime();
    }

    private int nextEggTime() {
        return 6000 + random.nextInt(6000);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        Vec3 motion = getDeltaMovement();
        // Its wings slow every fall, like a hen's.
        if (!onGround() && motion.y < 0) setDeltaMovement(motion.multiply(1, 0.6, 1));
        if (!level().isClientSide && isAlive() && !isBaby() && --eggTime <= 0) {
            playSound(SoundEvents.CHICKEN_EGG, 1, (random.nextFloat() - random.nextFloat()) * 0.2f + 1);
            spawnAtLocation(Items.EGG);
            eggTime = nextEggTime();
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("EggLayTime", eggTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("EggLayTime")) eggTime = tag.getInt("EggLayTime");
    }

    @Override
    public FeatheredPig getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return DVSouls.FEATHERED_PIG.get().create(level);
    }
}
