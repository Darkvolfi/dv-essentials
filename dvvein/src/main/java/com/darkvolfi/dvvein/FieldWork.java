package com.darkvolfi.dvvein;

import com.darkvolfi.dvvein.client.VeinClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * With the vein key held, right-clicking the ground works on the whole connected patch of the same block at the
 * same height: seeds and saplings are planted everywhere they can grow, a hoe tills and a shovel makes paths.
 * Every change goes through Forge's place event, so claim mods (DV Team) can refuse single blocks.
 */
@Mod.EventBusSubscriber(modid = DVVein.MOD_ID)
public final class FieldWork {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private FieldWork() {}

    // Runs on both sides: the client must also cancel, or it would use the item on the clicked block itself.
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || player.isSpectator()) return;
        Level level = event.getLevel();
        if (!(level.isClientSide ? VeinClient.isHeld() : VeinMiner.isKeyHeld(player))) return;

        ItemStack stack = player.getMainHandItem();
        BlockPos pos = event.getPos();
        BlockState ground = level.getBlockState(pos);
        if (!level.isClientSide && VeinMiner.isBlacklisted(player, ground)) return;

        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof IPlantable plant) {
            if (event.getFace() != Direction.UP || !canPlant(level, pos, plant)) return;
            if (level instanceof ServerLevel server) plantAll(server, player, pos, ground, stack, plant);
        } else {
            ToolAction action = stack.canPerformAction(ToolActions.HOE_TILL) ? ToolActions.HOE_TILL
                    : stack.canPerformAction(ToolActions.SHOVEL_FLATTEN) ? ToolActions.SHOVEL_FLATTEN : null;
            if (action == null || modified(level, player, pos, action, true) == null) return;
            if (level instanceof ServerLevel server) workAll(server, player, pos, stack, action);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    private static boolean canPlant(Level level, BlockPos ground, IPlantable plant) {
        BlockPos above = ground.above();
        return level.isEmptyBlock(above) && plant.getPlant(level, above).canSurvive(level, above);
    }

    private static void plantAll(ServerLevel level, Player player, BlockPos origin, BlockState ground, ItemStack held, IPlantable plant) {
        int planted = 0;
        for (BlockPos pos : patch(level, origin, ground.getBlock(), p -> canPlant(level, p, plant))) {
            ItemStack seeds = held.isEmpty() ? findMore(player, held) : held;
            if (seeds.isEmpty() || (!player.isCreative() && player.getFoodData().getFoodLevel() <= 0)) break;
            BlockPos above = pos.above();
            if (!change(level, player, above, plant.getPlant(level, above))) continue;
            if (!player.isCreative()) seeds.shrink(1);
            player.causeFoodExhaustion(VeinConfig.EXHAUSTION_PER_BLOCK.get().floatValue());
            planted++;
        }
        if (planted > 0) {
            SoundEvent sound = plant.getPlant(level, origin.above()).getSoundType().getPlaceSound();
            level.playSound(null, origin, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    /** The held stack ran out: keep planting from another stack of the same seeds in the inventory. */
    private static ItemStack findMore(Player player, ItemStack used) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, used.copyWithCount(1))) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void workAll(ServerLevel level, Player player, BlockPos origin, ItemStack tool, ToolAction action) {
        int done = 0;
        // Any block the tool can work, so mixed grass and dirt is tilled in one go.
        for (BlockPos pos : patch(level, origin, null, p -> modified(level, player, p, action, true) != null)) {
            if (!VeinMiner.canContinue(player, tool)) break;
            BlockState result = modified(level, player, pos, action, false);
            if (result == null || !change(level, player, pos, result)) continue;
            if (!player.isCreative()) tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            player.causeFoodExhaustion(VeinConfig.EXHAUSTION_PER_BLOCK.get().floatValue());
            done++;
        }
        if (done > 0) {
            SoundEvent sound = action == ToolActions.HOE_TILL ? SoundEvents.HOE_TILL : SoundEvents.SHOVEL_FLATTEN;
            level.playSound(null, origin, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    /** What the tool would turn the block into (null if nothing); like vanilla, only with air above. */
    private static BlockState modified(Level level, Player player, BlockPos pos, ToolAction action, boolean simulate) {
        if (!level.getBlockState(pos.above()).isAir()) return null;
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
        return level.getBlockState(pos).getToolModifiedState(context, action, simulate);
    }

    /** Sets the block like vanilla placement does: place, fire Forge's place event, undo if a claim refuses it. */
    private static boolean change(ServerLevel level, Player player, BlockPos pos, BlockState state) {
        if (!level.mayInteract(player, pos)) return false;
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore(true, false);
            return false;
        }
        return true;
    }

    /** The clicked block first, then blocks connected sideways at the same height that pass the test (and are {@code kind}, unless null). */
    private static List<BlockPos> patch(Level level, BlockPos origin, Block kind, Predicate<BlockPos> usable) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(origin);
        int max = VeinConfig.MAX_BLOCKS.get();
        while (!queue.isEmpty() && result.size() < max) {
            BlockPos pos = queue.poll();
            result.add(pos);
            for (Direction side : SIDES) {
                BlockPos next = pos.relative(side);
                if (seen.add(next) && (kind == null || level.getBlockState(next).is(kind)) && usable.test(next)) queue.add(next);
            }
        }
        return result;
    }
}
