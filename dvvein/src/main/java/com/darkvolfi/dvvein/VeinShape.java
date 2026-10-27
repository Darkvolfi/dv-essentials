package com.darkvolfi.dvvein;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        // Locale.ROOT: in Turkish, "MINING".toLowerCase() is "mınıng" (dotless ı) and the key is not found
        return "dvvein.shape." + name().toLowerCase(Locale.ROOT);
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

        if (this == ESCAPE_TUNNEL || this == MINING_TUNNEL) {
            // Stairs from the clicked block: column k is k forward and k up (escape) or down (mining),
            // three blocks tall so you can walk/jump through; the block under each column is the step.
            int rise = this == ESCAPE_TUNNEL ? 1 : -1;
            for (int k = 0; k <= max && result.size() < max; k++) {
                BlockPos base = origin.relative(facing, k).above(rise * k);
                int cut = 0;
                for (int h = 0; h < 3; h++) cut += add(level, player, base.above(h), result, max) ? 1 : 0;
                if (k > 0 && cut == 0) break; // reached open air (or only uncuttable blocks): stop here
            }
            return result;
        }

        List<BlockPos> cross = crossSection(look);
        int depthLimit = this == SQUARE ? 1 : max;
        for (int depth = 0; depth < depthLimit && result.size() < max; depth++) {
            int cut = 0;
            for (BlockPos offset : cross) {
                cut += add(level, player, origin.relative(look, depth).offset(offset), result, max) ? 1 : 0;
            }
            // A slice with nothing to cut is a gap (cave, air): don't jump over it to unconnected blocks.
            if (depth > 0 && cut == 0) break;
        }
        return result;
    }

    /** Offsets across the tunnel, perpendicular to {@code look}. */
    private List<BlockPos> crossSection(Direction look) {
        List<BlockPos> offsets = new ArrayList<>();
        switch (this) {
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

    /** Whether {@code pos} is part of the shape: newly added, or already in it (the clicked block). */
    private static boolean add(Level level, Player player, BlockPos pos, List<BlockPos> result, int max) {
        if (result.contains(pos)) return true;
        if (result.size() >= max || !canCut(level, player, pos)) return false;
        result.add(pos.immutable());
        return true;
    }

    /** Shaped modes cut through mixed blocks, but never containers, fluids or unbreakable blocks. */
    private static boolean canCut(Level level, Player player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return player.isCreative() || player.hasCorrectToolForDrops(state);
    }
}
