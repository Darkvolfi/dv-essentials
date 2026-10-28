package com.darkvolfi.dvlens.network;

import com.darkvolfi.dvteam.protection.ClaimProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** DV Team integration; only touched after checking that DV Team is loaded. */
final class TeamContents {
    private TeamContents() {}

    /** Containers in a settlement where the player may not open them stay hidden. */
    static boolean mayPeek(ServerPlayer player, BlockPos pos) {
        return ClaimProtection.mayOpenContainers((ServerLevel) player.level(), player, pos);
    }
}
