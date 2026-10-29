package com.darkvolfi.dvbackpack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A backpack set down on the ground. Right-click opens it; sneak + right-click with an empty hand picks it up
 * (onto your back if there is room). Breaking it drops the backpack with everything still inside.
 */
public class BackpackBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** {@link BackpackTier} ordinal, for the colour. */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, BackpackTier.values().length - 1);
    /** Whether the sleeping bag is rolled up in front of the backpack (false while it is laid out). */
    public static final BooleanProperty ROLLED = BooleanProperty.create("rolled");

    private static final VoxelShape NORTH = box(1, 0, 0, 15, 13, 12);
    private static final VoxelShape EAST = box(4, 0, 1, 16, 13, 15);
    private static final VoxelShape SOUTH = box(1, 0, 4, 15, 13, 16);
    private static final VoxelShape WEST = box(0, 0, 1, 12, 13, 15);

    public BackpackBlock() {
        super(Properties.of().strength(0.5f, 1200f).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TIER, 0).setValue(ROLLED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER, ROLLED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        int tier = context.getItemInHand().getItem() instanceof BackpackItem item ? item.tier().ordinal() : 0;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(TIER, tier);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BackpackBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BackpackBlockEntity entity) || entity.backpack().isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isSecondaryUseActive()) {
            if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
            ItemStack backpack = entity.takeBackpack();
            if (!WornBackpack.equip(player, backpack) && !player.getInventory().add(backpack)) player.drop(backpack, false);
            level.removeBlock(pos, false);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.BLOCKS, 1f, 1f);
        } else if (player instanceof ServerPlayer sp) {
            BackpackMenu.open(sp, entity.backpack(), BackpackMenu.Source.block(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BackpackBlockEntity entity) {
            entity.removeSleepingBag();
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), entity.takeBackpack());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(DVBackpack.BACKPACKS.get(BackpackTier.values()[state.getValue(TIER)]).get());
    }
}
