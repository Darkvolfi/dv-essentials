package com.darkvolfi.dvgrave.compat;

import com.darkvolfi.dvteam.data.ClanData;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** DV Team integration; only touched after checking that DV Team is loaded. */
public final class TeamCompat {
    private TeamCompat() {}

    public static boolean sameClan(MinecraftServer server, UUID a, UUID b) {
        ClanData data = ClanData.get(server);
        ClanData.Clan clan = data.clanOf(a);
        return clan != null && clan == data.clanOf(b);
    }
}
