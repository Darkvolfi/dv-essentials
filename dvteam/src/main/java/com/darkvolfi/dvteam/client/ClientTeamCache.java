package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvteam.data.TeamSavedData;
import com.darkvolfi.dvteam.network.SyncTeamDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClientTeamCache {
    private static int maxClaimsPerPlayer = 64;
    private static int maxForceLoadedPerPlayer = 8;
    private static final Map<UUID, String> PLAYER_NAMES = new LinkedHashMap<>();
    private static final Set<UUID> ONLINE_PLAYERS = new LinkedHashSet<>();
    private static final Map<UUID, SyncTeamDataPacket.TeamEntry> TEAMS = new LinkedHashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_TEAM = new HashMap<>();
    private static final Map<TeamSavedData.ChunkKey, SyncTeamDataPacket.ClaimEntry> CLAIMS = new HashMap<>();

    private ClientTeamCache() {}

    public static void applySync(SyncTeamDataPacket pkt) {
        maxClaimsPerPlayer = pkt.maxClaimsPerPlayer;
        maxForceLoadedPerPlayer = pkt.maxForceLoadedPerPlayer;

        PLAYER_NAMES.clear();
        PLAYER_NAMES.putAll(pkt.playerNames);

        ONLINE_PLAYERS.clear();
        ONLINE_PLAYERS.addAll(pkt.onlinePlayers);

        TEAMS.clear();
        PLAYER_TO_TEAM.clear();
        for (SyncTeamDataPacket.TeamEntry t : pkt.teams) {
            TEAMS.put(t.id(), t);
            for (UUID m : t.members()) {
                PLAYER_TO_TEAM.put(m, t.id());
            }
        }

        CLAIMS.clear();
        for (SyncTeamDataPacket.ClaimEntry c : pkt.claims) {
            CLAIMS.put(new TeamSavedData.ChunkKey(c.dimension(), c.chunkX(), c.chunkZ()), c);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TeamScreen teamScreen) {
            teamScreen.refreshFromSync();
        }
    }

    public static void clear() {
        PLAYER_NAMES.clear();
        ONLINE_PLAYERS.clear();
        TEAMS.clear();
        PLAYER_TO_TEAM.clear();
        CLAIMS.clear();
    }

    public static SyncTeamDataPacket.TeamEntry getMyTeam() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        UUID tid = PLAYER_TO_TEAM.get(mc.player.getUUID());
        return tid == null ? null : TEAMS.get(tid);
    }

    public static SyncTeamDataPacket.TeamEntry getTeam(UUID teamId) {
        return TEAMS.get(teamId);
    }

    public static Collection<SyncTeamDataPacket.TeamEntry> getTeams() {
        return Collections.unmodifiableCollection(TEAMS.values());
    }

    public static Set<UUID> getOnlinePlayers() {
        return Collections.unmodifiableSet(ONLINE_PLAYERS);
    }

    public static String getPlayerName(UUID id) {
        return PLAYER_NAMES.getOrDefault(id, id.toString().substring(0, 8));
    }

    public static SyncTeamDataPacket.ClaimEntry getClaim(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (dimension == null) return null;
        return CLAIMS.get(new TeamSavedData.ChunkKey(dimension.location().toString(), chunkX, chunkZ));
    }

    public static int countClaims(UUID teamId) {
        int count = 0;
        for (SyncTeamDataPacket.ClaimEntry c : CLAIMS.values()) {
            if (c.teamId().equals(teamId)) count++;
        }
        return count;
    }

    public static int countForceLoaded(UUID teamId) {
        int count = 0;
        for (SyncTeamDataPacket.ClaimEntry c : CLAIMS.values()) {
            if (c.teamId().equals(teamId) && c.forceLoaded()) count++;
        }
        return count;
    }

    public static int maxClaimsFor(SyncTeamDataPacket.TeamEntry team) {
        return Math.max(1, team.members().size()) * maxClaimsPerPlayer;
    }

    public static int maxForceLoadedFor(SyncTeamDataPacket.TeamEntry team) {
        return Math.max(1, team.members().size()) * maxForceLoadedPerPlayer;
    }
}
