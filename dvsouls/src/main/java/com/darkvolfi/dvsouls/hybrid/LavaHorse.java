package com.darkvolfi.dvsouls.hybrid;

import com.darkvolfi.dvsouls.DVSouls;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Horse and strider: a horse of basalt and embers that walks on lava as if it were ground and never burns. Its rider
 * is kept from burning too, so the Nether's lava seas can be crossed on it.
 */
public class LavaHorse extends Horse {
    public LavaHorse(EntityType<? extends Horse> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean canStandOnFluid(FluidState fluid) {
        return fluid.is(FluidTags.LAVA);
    }

    @Override
    public void tick() {
        super.tick();
        if (isInLava()) {
            // A lava block carries it on a half-block-high surface, so standing on lava still counts as in it:
            // then it is on the ground. Only sunk deeper (jumped or fell in) does it float back up, as a strider does.
            if (CollisionContext.of(this).isAbove(LiquidBlock.STABLE_SHAPE, blockPosition(), true)
                    && !level().getFluidState(blockPosition().above()).is(FluidTags.LAVA)) {
                setOnGround(true);
            } else {
                setDeltaMovement(getDeltaMovement().scale(0.5).add(0, 0.05, 0));
            }
        }
        LivingEntity rider = getControllingPassenger();
        if (!level().isClientSide && tickCount % 20 == 0 && rider != null) {
            rider.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
        }
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        if (!(partner instanceof LavaHorse)) return null;
        LavaHorse foal = DVSouls.LAVA_HORSE.get().create(level);
        if (foal != null) setOffspringAttributes(partner, foal);
        return foal;
    }

    /** Only with another lava horse; a common horse would give a common foal. */
    @Override
    public boolean canMate(Animal other) {
        return other instanceof LavaHorse && super.canMate(other);
    }
}
