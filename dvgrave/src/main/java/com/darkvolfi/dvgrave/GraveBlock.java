package com.darkvolfi.dvgrave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * The gravestone. It can't be broken or pushed, survives explosions and drops nothing: it goes away
 * by itself once it is empty. {@link #FACING} is the side its name faces.
 */
public class GraveBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape BASE = Block.box(2, 0, 4, 14, 2, 12);
    private static final VoxelShape STONE_NS = Shapes.or(BASE, Block.box(3, 2, 6, 13, 15, 10));
    private static final VoxelShape STONE_EW = Shapes.or(Block.box(4, 0, 2, 12, 2, 14), Block.box(6, 2, 3, 10, 15, 13));

    public GraveBlock() {
        super(Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).sound(SoundType.STONE)
                .noLootTable().pushReaction(PushReaction.BLOCK).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? STONE_NS : STONE_EW;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GraveBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof GraveBlockEntity grave) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!grave.mayOpen(sp)) {
            int minutes = grave.minutesLeft();
            sp.displayClientMessage(minutes < 0 ? Component.translatable("dvgrave.locked_forever", grave.ownerName())
                    : Component.translatable("dvgrave.locked", grave.ownerName(), minutes), true);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(sp, grave, buf -> {
            buf.writeBlockPos(pos);
            buf.writeVarInt(grave.container().getContainerSize());
            buf.writeVarInt(grave.xp());
            buf.writeUtf(grave.ownerName());
        });
        return InteractionResult.CONSUME;
    }

    /** A creative player breaking it: spill the contents rather than lose them. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
            grave.spill();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
