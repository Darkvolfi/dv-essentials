package com.darkvolfi.dvlens.client;

import com.darkvolfi.dvteam.client.ClientClanCache;
import com.darkvolfi.dvteam.data.ClanData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** DV Team integration; only touched after checking that DV Team is loaded. */
final class TeamLand {
    private TeamLand() {}

    /** "[TAG] Settlement" in the clan colour, or null when the chunk is wild land. */
    static Component owner(ClientLevel level, BlockPos pos) {
        ClanData.Settlement s = ClientClanCache.settlementAt(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        if (s == null) return null;
        ClanData.Clan clan = ClientClanCache.clan(s.clanId);
        if (clan == null) return null;
        return Component.translatable("dvlens.land", "[" + clan.tag + "] " + s.name)
                .withStyle(st -> st.withColor(clan.textColor()));
    }
}
