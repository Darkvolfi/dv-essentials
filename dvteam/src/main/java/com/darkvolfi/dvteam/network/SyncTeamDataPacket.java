package com.darkvolfi.dvteam.network;

import com.darkvolfi.dvteam.TeamConfig;
import com.darkvolfi.dvteam.client.ClientTeamCache;
import com.darkvolfi.dvteam.data.TeamSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class SyncTeamDataPacket {
    public final int maxClaimsPerPlayer;
    public final int maxForceLoadedPerPlayer;
    public final Map<UUID, String> playerNames;
    public final Set<UUID> onlinePlayers;
    public final List<TeamEntry> teams;
    public final List<ClaimEntry> claims;

    public record TeamEntry(
            UUID id,
            String name,
            int color,
            UUID owner,
            Set<UUID> members,
            Set<UUID> invited,
            Set<UUID> allies,
            boolean friendlyFire,
            boolean allowAllyInteract,
            boolean allowAllyEdit,
            boolean publicInteract
    ) {}

    public record ClaimEntry(String dimension, int chunkX, int chunkZ, UUID teamId, boolean forceLoaded) {}

    public SyncTeamDataPacket(int maxClaimsPerPlayer, int maxForceLoadedPerPlayer,
                              Map<UUID, String> playerNames, Set<UUID> onlinePlayers,
                              List<TeamEntry> teams, List<ClaimEntry> claims) {
        this.maxClaimsPerPlayer = maxClaimsPerPlayer;
        this.maxForceLoadedPerPlayer = maxForceLoadedPerPlayer;
        this.playerNames = playerNames;
        this.onlinePlayers = onlinePlayers;
        this.teams = teams;
        this.claims = claims;
    }

    public static SyncTeamDataPacket fromServer(MinecraftServer server, TeamSavedData data) {
        Map<UUID, String> names = new LinkedHashMap<>(data.getPlayerNames());
        Set<UUID> online = new LinkedHashSet<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            online.add(sp.getUUID());
            names.put(sp.getUUID(), sp.getGameProfile().getName());
        }

        List<TeamEntry> teamList = new ArrayList<>();
        for (TeamSavedData.TeamRecord tr : data.getTeams()) {
            teamList.add(new TeamEntry(
                    tr.id, tr.name, tr.color, tr.owner,
                    new LinkedHashSet<>(tr.members),
                    new LinkedHashSet<>(tr.invited),
                    new LinkedHashSet<>(tr.allies),
                    tr.friendlyFire, tr.allowAllyInteract, tr.allowAllyEdit, tr.publicInteract
            ));
        }

        List<ClaimEntry> claimList = new ArrayList<>();
        for (Map.Entry<TeamSavedData.ChunkKey, TeamSavedData.ClaimRecord> e : data.getClaims().entrySet()) {
            claimList.add(new ClaimEntry(
                    e.getKey().dimension(), e.getKey().x(), e.getKey().z(),
                    e.getValue().teamId, e.getValue().forceLoaded
            ));
        }

        return new SyncTeamDataPacket(
                TeamConfig.MAX_CLAIMS_PER_PLAYER.get(),
                TeamConfig.MAX_FORCE_LOADED_PER_PLAYER.get(),
                names, online, teamList, claimList
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(maxClaimsPerPlayer);
        buf.writeVarInt(maxForceLoadedPerPlayer);

        buf.writeVarInt(playerNames.size());
        for (Map.Entry<UUID, String> e : playerNames.entrySet()) {
            buf.writeUUID(e.getKey());
            buf.writeUtf(e.getValue(), 64);
        }

        buf.writeVarInt(onlinePlayers.size());
        for (UUID id : onlinePlayers) {
            buf.writeUUID(id);
        }

        buf.writeVarInt(teams.size());
        for (TeamEntry t : teams) {
            buf.writeUUID(t.id());
            buf.writeUtf(t.name(), 48);
            buf.writeInt(t.color());
            buf.writeUUID(t.owner());
            writeUuidSet(buf, t.members());
            writeUuidSet(buf, t.invited());
            writeUuidSet(buf, t.allies());
            buf.writeBoolean(t.friendlyFire());
            buf.writeBoolean(t.allowAllyInteract());
            buf.writeBoolean(t.allowAllyEdit());
            buf.writeBoolean(t.publicInteract());
        }

        buf.writeVarInt(claims.size());
        for (ClaimEntry c : claims) {
            buf.writeUtf(c.dimension(), 128);
            buf.writeVarInt(c.chunkX());
            buf.writeVarInt(c.chunkZ());
            buf.writeUUID(c.teamId());
            buf.writeBoolean(c.forceLoaded());
        }
    }

    public static SyncTeamDataPacket decode(FriendlyByteBuf buf) {
        int maxClaims = buf.readVarInt();
        int maxForce = buf.readVarInt();

        int nameCount = buf.readVarInt();
        Map<UUID, String> names = new LinkedHashMap<>(nameCount);
        for (int i = 0; i < nameCount; i++) {
            names.put(buf.readUUID(), buf.readUtf(64));
        }

        int onlineCount = buf.readVarInt();
        Set<UUID> online = new LinkedHashSet<>(onlineCount);
        for (int i = 0; i < onlineCount; i++) {
            online.add(buf.readUUID());
        }

        int teamCount = buf.readVarInt();
        List<TeamEntry> teams = new ArrayList<>(teamCount);
        for (int i = 0; i < teamCount; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(48);
            int color = buf.readInt();
            UUID owner = buf.readUUID();
            Set<UUID> members = readUuidSet(buf);
            Set<UUID> invited = readUuidSet(buf);
            Set<UUID> allies = readUuidSet(buf);
            boolean ff = buf.readBoolean();
            boolean allyInt = buf.readBoolean();
            boolean allyEdit = buf.readBoolean();
            boolean pubInt = buf.readBoolean();
            teams.add(new TeamEntry(id, name, color, owner, members, invited, allies, ff, allyInt, allyEdit, pubInt));
        }

        int claimCount = buf.readVarInt();
        List<ClaimEntry> claims = new ArrayList<>(claimCount);
        for (int i = 0; i < claimCount; i++) {
            claims.add(new ClaimEntry(buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readUUID(), buf.readBoolean()));
        }

        return new SyncTeamDataPacket(maxClaims, maxForce, names, online, teams, claims);
    }

    private static void writeUuidSet(FriendlyByteBuf buf, Set<UUID> set) {
        buf.writeVarInt(set.size());
        for (UUID id : set) buf.writeUUID(id);
    }

    private static Set<UUID> readUuidSet(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Set<UUID> set = new LinkedHashSet<>(n);
        for (int i = 0; i < n; i++) set.add(buf.readUUID());
        return set;
    }

    public static void handle(SyncTeamDataPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTeamCache.applySync(pkt)));
        ctx.get().setPacketHandled(true);
    }
}
