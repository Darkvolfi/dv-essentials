package com.darkvolfi.dvvein;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * What the vein key breaks. SHAPELESS follows connected blocks of the same kind; the others
 * cut a fixed shape through any block the player could mine normally. Shared by the client
 * preview and the server, so both see the same blocks.
 */
public enum VeinShape {
    SHAPELESS,
    SMALL_TUNNEL,
    MINING_TUNNEL,
    LARGE_TUNNEL,
    SQUARE,
    ESCAPE_TUNNEL;

    public String translationKey() {
        return "dvvein.shape." + name().toLowerCase();
    }

    public VeinShape cycle(int step) {
        VeinShape[] all = values();
        return all[Math.floorMod(ordinal() + step, all.length)];
    }

    public static VeinShape byId(int id) {
        VeinShape[] all = values();
        return id >= 0 && id < all.length ? all[id] : SHAPELESS;
    }

    /** Blocks to break, {@code origin} first, at most {@code max}. */
    public List<BlockPos> blocks(Level level, Player player, BlockPos origin, BlockState originState, int max, boolean groupOres) {
        if (this == SHAPELESS) return VeinFinder.find(level, origin, originState, max, groupOres);

        List<BlockPos> result = new ArrayList<>();
        result.add(origin.immutable());
        Direction look = Direction.orderedByNearest(player)[0];
        Direction facing = player.getDirection();

        if (this == ESCAPE_TUNNEL) {
            // Stairs up and away: step k is k blocks forward and k up, three blocks tall for headroom.
            BlockPos feet = player.blockPosition();
            for (int k = 1; result.size() < max && k <= max; k++) {
                BlockPos floor = feet.relative(facing, k).above(k);
                for (int h = 0; h < 3; h++) add(level, player, floor.above(h), result, max);
            }
            return result;
        }

        List<BlockPos> cross = crossSection(player, origin, look);
        int depthLimit = this == SQUARE ? 1 : max;
        for (int depth = 0; depth < depthLimit && result.size() < max; depth++) {
            for (BlockPos offset : cross) {
                add(level, player, origin.relative(look, depth).offset(offset), result, max);
            }
        }
        return result;
    }

    /** Offsets across the tunnel, perpendicular to {@code look}. */
    private List<BlockPos> crossSection(Player player, BlockPos origin, Direction look) {
        List<BlockPos> offsets = new ArrayList<>();
        switch (this) {
            case SMALL_TUNNEL -> offsets.add(BlockPos.ZERO);
            case MINING_TUNNEL -> {
                offsets.add(BlockPos.ZERO);
                if (look.getAxis().isHorizontal()) {
                    // walkable: the block at the other height of the player's two blocks
                    boolean eyeLevel = origin.getY() > player.getBlockY();
                    offsets.add(eyeLevel ? BlockPos.ZERO.below() : BlockPos.ZERO.above());
                }
            }
            case LARGE_TUNNEL, SQUARE -> {
                Direction u = look.getAxis().isHorizontal() ? Direction.UP : Direction.NORTH;
                Direction v = look.getAxis().isHorizontal() ? look.getClockWise() : Direction.EAST;
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) offsets.add(BlockPos.ZERO.relative(u, a).relative(v, b));
                }
            }
            default -> offsets.add(BlockPos.ZERO);
        }
        return offsets;
    }

    private static void add(Level level, Player player, BlockPos pos, List<BlockPos> result, int max) {
        if (result.size() >= max || result.contains(pos)) return;
        if (canCut(level, player, pos)) result.add(pos.immutable());
    }

    /** Shaped modes cut through mixed blocks, but never containers, fluids or unbreakable blocks. */
    private static boolean canCut(Level level, Player player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return player.isCreative() || player.hasCorrectToolForDrops(state);
    }
}
