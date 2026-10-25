package com.darkvolfi.dvharvest;

import com.darkvolfi.dvvein.VeinConfig;
import com.darkvolfi.dvvein.VeinFinder;
import com.darkvolfi.dvvein.VeinMiner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Optional DV Vein integration: holding the vein key while harvesting takes the whole field.
 * Only touched after checking that DV Vein is loaded, so DV Harvest also runs without it.
 */
final class VeinHarvest {
    private VeinHarvest() {}

    static boolean isVeinKeyHeld(Player player) {
        return VeinMiner.isKeyHeld(player);
    }

    /** Connected blocks of the same crop, the clicked one first; ripeness is checked by the caller. */
    static List<BlockPos> connectedCrops(Level level, BlockPos start, BlockState crop) {
        return VeinFinder.find(level, start, crop, VeinConfig.MAX_BLOCKS.get(), false);
    }

    static float extraExhaustion() {
        return VeinConfig.EXHAUSTION_PER_BLOCK.get().floatValue();
    }
}
