package com.darkvolfi.dvbackpack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sleeping with the backpack still on: the sleeping bag is laid out in front of the player, they lie down in
 * it, and once they are up again it is rolled back up.
 */
@Mod.EventBusSubscriber(modid = DVBackpack.MOD_ID)
public final class SleepOnTheGround {
    /** The head of the bag each sleeper laid out, until they get up. */
    private static final Map<UUID, BlockPos> BAGS = new HashMap<>();

    private SleepOnTheGround() {}

    public static void sleep(ServerPlayer player, int color) {
        if (BAGS.containsKey(player.getUUID())) return;
        ServerLevel level = player.serverLevel();
        Direction ahead = player.getDirection();
        BlockPos head = player.blockPosition().relative(ahead);
        BlockPos foot = head.relative(ahead);
        for (BlockPos pos : new BlockPos[]{head, foot}) {
            if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                player.displayClientMessage(Component.translatable("dvbackpack.sleeping_bag.no_room_ahead"), true);
                return;
            }
        }
        // A bed's facing points from its foot to its head: here, back towards the player.
        BlockState bag = DVBackpack.SLEEPING_BAG.get().defaultBlockState().setValue(BedBlock.FACING, ahead.getOpposite());
        level.setBlock(foot, bag.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(head, bag.setValue(BedBlock.PART, BedPart.HEAD), 3);
        for (BlockPos pos : new BlockPos[]{head, foot}) {
            if (level.getBlockEntity(pos) instanceof SleepingBagBlockEntity entity) entity.setColor(color);
        }
        level.playSound(null, head, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 1f);
        player.closeContainer();
        player.startSleepInBed(head).ifLeft(problem -> {
            if (problem.getMessage() != null) player.displayClientMessage(problem.getMessage(), true);
            rollUp(level, head);
        }).ifRight(unit -> BAGS.put(player.getUUID(), head));
    }

    private static void rollUp(ServerLevel level, BlockPos head) {
        if (!level.getBlockState(head).is(DVBackpack.SLEEPING_BAG.get())) return;
        BlockPos foot = head.relative(level.getBlockState(head).getValue(BedBlock.FACING).getOpposite());
        level.removeBlock(head, false);
        if (level.getBlockState(foot).is(DVBackpack.SLEEPING_BAG.get())) level.removeBlock(foot, false);
        level.playSound(null, head, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1f, 1f);
    }

    /** Rolls the bag up the tick after its sleeper got up (getting up itself still needs the bag, to stand beside it). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.isSleeping()) return;
        BlockPos head = BAGS.remove(player.getUUID());
        if (head != null) rollUp(player.serverLevel(), head);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BlockPos head = BAGS.remove(event.getEntity().getUUID());
        if (head != null && event.getEntity().level() instanceof ServerLevel level) rollUp(level, head);
    }
}
