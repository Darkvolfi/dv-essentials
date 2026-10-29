package com.darkvolfi.dvsouls;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Brings a dead pet back: lay the shard of its collar on the altar, then offer a soul at least as strong (as many hearts or
 * more). The offered soul is spent and the pet stands up on the altar, whole, with its name and owner.
 */
public class SoulAltarBlock extends BaseEntityBlock {
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    private static final VoxelShape SHAPE = Shapes.or(
            box(1, 0, 1, 15, 4, 15),
            box(4, 4, 4, 12, 10, 12),
            box(2, 10, 2, 14, 13, 14));

    public SoulAltarBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.POLISHED_BLACKSTONE)
                .lightLevel(state -> state.getValue(FILLED) ? 10 : 3).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoulAltarBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity altar)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        ItemStack copy = altar.copy();
        if (copy.isEmpty()) {
            Component refusal = refuse(held, player);
            if (refusal != null) {
                tell(player, level, refusal);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                altar.setCopy(held.split(1));
                level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1, 0.6f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.setItemInHand(hand, copy);
                altar.setCopy(ItemStack.EMPTY);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        float needed = needed(copy);
        if (!held.is(DVSouls.FILLED_SOUL_JAR.get())) {
            tell(player, level, Component.translatable(offerKey(), Soul.name(Soul.get(copy)), hearts(needed)));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        float offered = Soul.hearts(Soul.get(held));
        if (offered < needed) {
            tell(player, level, Component.translatable("dvsouls.altar.too_weak", hearts(offered), hearts(needed)));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level instanceof ServerLevel server) {
            Vec3 top = Vec3.atBottomCenterOf(pos.above());
            if (!complete(server, top, player, copy)) return InteractionResult.FAIL;
            altar.setCopy(ItemStack.EMPTY);
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(DVSouls.SOUL_JAR.get()));
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, top.x, top.y + 0.5, top.z, 40, 0.4, 0.6, 0.4, 0.03);
            server.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Why {@code held} can't be laid on the altar, or null if it can: only the owner's collar shard. */
    @Nullable
    protected Component refuse(ItemStack held, Player player) {
        if (!held.is(DVSouls.COLLAR_SHARD.get())) return Component.translatable("dvsouls.altar.empty");
        UUID owner = Pets.owner(Soul.get(held));
        return owner == null || owner.equals(player.getUUID()) ? null : Component.translatable("dvsouls.capture.not_yours");
    }

    /** How many hearts the offered soul needs: as many as the dead pet had. */
    protected float needed(ItemStack laid) {
        return Soul.hearts(Soul.get(laid));
    }

    /** The message asking for an offering: name, hearts. */
    protected String offerKey() {
        return "dvsouls.altar.offer";
    }

    /** The offering is accepted: the pet stands up on the altar. False if its creature no longer exists. */
    protected boolean complete(ServerLevel level, Vec3 top, Player player, ItemStack laid) {
        return Soul.spawn(Soul.get(laid), level, top, player.getYRot() + 180) != null;
    }

    private static String hearts(float hearts) {
        return String.format(Locale.ROOT, "%.1f", hearts);
    }

    private static void tell(Player player, Level level, Component message) {
        if (!level.isClientSide) player.displayClientMessage(message, true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SoulAltarBlockEntity altar) {
            Block.popResource(level, pos, altar.copy());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    static void setFilled(Level level, BlockPos pos, boolean filled) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(FILLED) && state.getValue(FILLED) != filled) level.setBlock(pos, state.setValue(FILLED, filled), 3);
    }

    /** Holds the collar shard laid on the altar. */
    public static class SoulAltarBlockEntity extends BlockEntity {
        private ItemStack copy = ItemStack.EMPTY;

        public SoulAltarBlockEntity(BlockPos pos, BlockState state) {
            super(DVSouls.SOUL_ALTAR_ENTITY.get(), pos, state);
        }

        ItemStack copy() {
            return copy;
        }

        void setCopy(ItemStack copy) {
            this.copy = copy;
            setChanged();
            if (level != null) setFilled(level, worldPosition, !copy.isEmpty());
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            if (!copy.isEmpty()) tag.put("Copy", copy.save(new CompoundTag()));
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            copy = tag.contains("Copy") ? ItemStack.of(tag.getCompound("Copy")) : ItemStack.EMPTY;
        }
    }
}
