package com.darkvolfi.dvsouls;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A creature corrupted by a foreign soul: it keeps the creature's soul and looks like it, darkened, with red eyes and
 * smoke about it, but it is a monster and attacks players. On the client it carries a copy of the creature (its
 * {@link #puppet()}), kept in step with it, which its renderer draws.
 */
public class Corrupted extends Monster {
    private static final EntityDataAccessor<CompoundTag> SOURCE = SynchedEntityData.defineId(Corrupted.class, EntityDataSerializers.COMPOUND_TAG);
    private static final DustParticleOptions EYE = new DustParticleOptions(new Vector3f(1, 0.05f, 0.05f), 0.6f);

    private @Nullable LivingEntity puppet;
    private boolean noPuppet;

    public Corrupted(EntityType<? extends Corrupted> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    /** Turns the creature into a corrupted one where it stands. */
    public static Corrupted corrupt(ServerLevel level, LivingEntity creature) {
        Corrupted corrupted = DVSouls.CORRUPTED.get().create(level);
        if (corrupted == null) throw new IllegalStateException("No corrupted creature");
        corrupted.become(Soul.of(creature), creature.getMaxHealth());
        corrupted.moveTo(creature.getX(), creature.getY(), creature.getZ(), creature.getYRot(), creature.getXRot());
        corrupted.setYHeadRot(creature.getYHeadRot());
        creature.discard();
        level.addFreshEntity(corrupted);
        return corrupted;
    }

    /** Takes on the creature: its soul, its health (at least 10), and a bite to match. Stays in the world once there. */
    private void become(CompoundTag soul, float health) {
        setSource(soul);
        float max = Math.max(10, health);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3 + max / 10);
        setHealth(max);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SOURCE, new CompoundTag());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** The soul of the creature it was. */
    public CompoundTag source() {
        return entityData.get(SOURCE);
    }

    private void setSource(CompoundTag soul) {
        entityData.set(SOURCE, soul);
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SOURCE.equals(key)) {
            refreshDimensions();
            puppet = null;
            noPuppet = false;
        }
    }

    /** As big as the creature it was. */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return Soul.type(source()).map(EntityType::getDimensions).orElseGet(() -> super.getDimensions(pose));
    }

    /** "Corrupted Wolf", or "Corrupted Rex" for a named one. */
    @Override
    protected Component getTypeName() {
        return source().isEmpty() ? super.getTypeName() : Component.translatable("entity.dvsouls.corrupted.of", Soul.name(source()));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Source", source());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSource(tag.getCompound("Source"));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) return;
        followPuppet();
        if (random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.SMOKE, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.02, 0);
        }
        // Two red eyes, either side of the head, a little in front of it.
        if (tickCount % 2 == 0) {
            float yaw = getYHeadRot() * Mth.DEG_TO_RAD;
            double forward = getBbWidth() * 0.5 + 0.05, apart = Math.min(0.15, getBbWidth() * 0.2);
            double fx = -Mth.sin(yaw) * forward, fz = Mth.cos(yaw) * forward;
            double sx = Mth.cos(yaw) * apart, sz = Mth.sin(yaw) * apart;
            double y = getEyeY();
            level().addParticle(EYE, getX() + fx + sx, y, getZ() + fz + sz, 0, 0, 0);
            level().addParticle(EYE, getX() + fx - sx, y, getZ() + fz - sz, 0, 0, 0);
        }
    }

    /** The creature it was, as its renderer draws it; null if there is none (or it can't be made). Client only. */
    @Nullable
    public LivingEntity puppet() {
        if (puppet == null && !noPuppet) {
            CompoundTag soul = source();
            if (Soul.type(soul).map(type -> type.create(level())).orElse(null) instanceof LivingEntity body) {
                try {
                    body.load(soul.getCompound("Data"));
                } catch (RuntimeException ignored) {
                    // its saved data didn't fit: it still looks like its kind
                }
                body.setCustomName(null);
                puppet = body;
            }
            noPuppet = puppet == null;
        }
        return puppet;
    }

    /** Puts the puppet where this is, posed the same, and walks it the way this walks. */
    private void followPuppet() {
        LivingEntity body = puppet();
        if (body == null) return;
        body.setPos(getX(), getY(), getZ());
        body.xo = xo;
        body.yo = yo;
        body.zo = zo;
        body.xOld = xOld;
        body.yOld = yOld;
        body.zOld = zOld;
        body.setYRot(getYRot());
        body.yRotO = yRotO;
        body.setXRot(getXRot());
        body.xRotO = xRotO;
        body.yBodyRot = yBodyRot;
        body.yBodyRotO = yBodyRotO;
        body.yHeadRot = yHeadRot;
        body.yHeadRotO = yHeadRotO;
        body.attackAnim = attackAnim;
        body.oAttackAnim = oAttackAnim;
        body.swinging = swinging;
        body.swingTime = swingTime;
        body.hurtTime = hurtTime;
        body.deathTime = deathTime;
        body.tickCount = tickCount;
        body.setOnGround(onGround());
        // the same as LivingEntity.calculateEntityAnimation does for this one, so both walk in step
        body.walkAnimation.update(Math.min((float) Mth.length(getX() - xo, getZ() - zo) * 4, 1), 0.4f);
    }
}
