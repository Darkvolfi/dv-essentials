package com.darkvolfi.dvsouls.hybrid;

import com.darkvolfi.dvsouls.DVSouls;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.IForgeShearable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sheep and cow: a cow that grows wool. Shear it like a sheep, milk it like a cow; when it dies it drops what a cow
 * does (leather and beef). Shorn, it is bare until it grazes on grass and its wool grows back, like a sheep's.
 */
public class WoollyCow extends Cow implements IForgeShearable {
    private static final EntityDataAccessor<Boolean> SHEARED = SynchedEntityData.defineId(WoollyCow.class, EntityDataSerializers.BOOLEAN);

    public WoollyCow(EntityType<? extends Cow> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SHEARED, false);
    }

    public boolean isSheared() {
        return entityData.get(SHEARED);
    }

    @Override
    public boolean isShearable(ItemStack item, Level level, BlockPos pos) {
        return isAlive() && !isSheared() && !isBaby();
    }

    /** Forge's shears call this: 1-3 white wool, like a sheep. */
    @Override
    public List<ItemStack> onSheared(@Nullable Player player, ItemStack item, Level level, BlockPos pos, int fortune) {
        level.playSound(null, this, SoundEvents.SHEEP_SHEAR, player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 1, 1);
        entityData.set(SHEARED, true);
        List<ItemStack> wool = new ArrayList<>();
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) wool.add(new ItemStack(Items.WHITE_WOOL));
        return wool;
    }

    private EatBlockGoal grazing;
    /** Counts down while it grazes: 40 ticks with its head down, as a sheep's. */
    private int grazeTick;

    @Override
    protected void registerGoals() {
        super.registerGoals();
        grazing = new EatBlockGoal(this);
        goalSelector.addGoal(5, grazing);
    }

    @Override
    protected void customServerAiStep() {
        grazeTick = grazing.getEatAnimationTick();
        super.customServerAiStep();
    }

    @Override
    public void aiStep() {
        if (level().isClientSide) grazeTick = Math.max(0, grazeTick - 1);
        super.aiStep();
    }

    /** The grazing goal tells the client with event 10, which starts the head-down animation. */
    @Override
    public void handleEntityEvent(byte event) {
        if (event == 10) grazeTick = 40;
        else super.handleEntityEvent(event);
    }

    /** How far its head is lowered while grazing, 0-1: down over 4 ticks, held, back up over the last 4. */
    public float getHeadEatPositionScale(float partialTick) {
        if (grazeTick <= 0) return 0;
        if (grazeTick >= 4 && grazeTick <= 36) return 1;
        return grazeTick < 4 ? (grazeTick - partialTick) / 4 : -(grazeTick - 40 - partialTick) / 4;
    }

    /** Its head's tilt while grazing, nodding a little as it chews; otherwise where it looks. */
    public float getHeadEatAngleScale(float partialTick) {
        if (grazeTick > 4 && grazeTick <= 36) {
            float chew = (grazeTick - 4 - partialTick) / 32;
            return Mth.PI / 5 + 0.21991149f * Mth.sin(chew * 28.7f);
        }
        return grazeTick > 0 ? Mth.PI / 5 : getXRot() * Mth.DEG_TO_RAD;
    }

    /** Grazing grows its wool back, as with a sheep. */
    @Override
    public void ate() {
        super.ate();
        entityData.set(SHEARED, false);
    }

    /** Like a sheep, it drops a wool when it dies unshorn: its own table, the cow's drops and one white wool. */
    @Override
    protected ResourceLocation getDefaultLootTable() {
        return isSheared() ? super.getDefaultLootTable() : new ResourceLocation(DVSouls.MOD_ID, "entities/woolly_cow_woolly");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Sheared", isSheared());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SHEARED, tag.getBoolean("Sheared"));
    }

    @Override
    public WoollyCow getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return DVSouls.WOOLLY_COW.get().create(level);
    }
}
