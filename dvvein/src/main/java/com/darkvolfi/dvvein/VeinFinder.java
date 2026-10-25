package com.darkvolfi.dvvein;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds blocks connected to a start block (including diagonals) that count as the same kind. Shared by client preview and server. */
public final class VeinFinder {
    private VeinFinder() {}

    /** The start block first, then the rest in breadth-first order, at most {@code max} blocks. */
    public static List<BlockPos> find(BlockGetter level, BlockPos start, BlockState origin, int max, boolean groupOres) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<TagKey<Block>> oreTags = groupOres ? oreTags(origin) : Set.of();

        queue.add(start.immutable());
        seen.add(start.immutable());
        while (!queue.isEmpty() && result.size() < max) {
            BlockPos pos = queue.poll();
            result.add(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (!seen.add(next)) continue;
                        if (matches(origin, oreTags, level.getBlockState(next))) queue.add(next);
                    }
                }
            }
        }
        return result;
    }

    private static boolean matches(BlockState origin, Set<TagKey<Block>> oreTags, BlockState other) {
        if (other.is(origin.getBlock())) return true;
        for (TagKey<Block> tag : oreTags) {
            if (other.is(tag)) return true;
        }
        return false;
    }

    private static Set<TagKey<Block>> oreTags(BlockState state) {
        Set<TagKey<Block>> tags = new HashSet<>();
        state.getTags().forEach(tag -> {
            if (tag.location().getNamespace().equals("forge") && tag.location().getPath().startsWith("ores/")) {
                tags.add(tag);
            }
        });
        return tags;
    }
}
