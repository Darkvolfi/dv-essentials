package com.darkvolfi.dvteam.data;

import com.darkvolfi.dvteam.TeamConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.util.FakePlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeamSavedData extends SavedData {
    private static final String DATA_NAME = "dvteam_data";

    public static final int[] TEAM_PALETTE = {
            0xE5534B, // Red
            0xFF9800, // Orange
            0xFFD54F, // Gold
            0x66BB6A, // Green
            0x26A69A, // Teal
            0x4FC3F7, // Sky Blue
            0x5C6BC0, // Indigo
            0x9B6BFF, // Purple
            0xEC407A, // Pink
            0x8D6E63, // Brown
            0x78909C, // Slate
            0xFFFFFF  // White
    };

    public record ChunkKey(String dimension, int x, int z) {}

    public static final class ClaimRecord {
        public final UUID teamId;
        public boolean forceLoaded;

        public ClaimRecord(UUID teamId, boolean forceLoaded) {
            this.teamId = teamId;
            this.forceLoaded = forceLoaded;
        }
    }

    public static final class TeamRecord {
        public final UUID id;
        public String name;
        public int color;
        public UUID owner;
        public final Set<UUID> members = new LinkedHashSet<>();
        public final Set<UUID> invited = new LinkedHashSet<>();
        public final Set<UUID> allies = new LinkedHashSet<>();
        public boolean friendlyFire = false;
        public boolean allowAllyInteract = true;
        public boolean allowAllyEdit = false;
        public boolean publicInteract = false;

        public TeamRecord(UUID id, String name, int color, UUID owner) {
            this.id = id;
            this.name = name;
            this.color = color & 0xFFFFFF;
            this.owner = owner;
        }
    }

    private final Map<UUID, TeamRecord> teams = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerToTeam = new HashMap<>();
    private final Map<UUID, String> playerNames = new LinkedHashMap<>();
    private final Map<ChunkKey, ClaimRecord> claims = new LinkedHashMap<>();

    public static TeamSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TeamSavedData::load, TeamSavedData::new, DATA_NAME);
    }

    public TeamRecord ensurePlayerTeam(ServerPlayer player) {
        UUID pid = player.getUUID();
        String name = player.getGameProfile().getName();
        playerNames.put(pid, name);
        UUID tid = playerToTeam.get(pid);
        if (tid != null && teams.containsKey(tid)) {
            setDirty();
            return teams.get(tid);
        }
        int color = TEAM_PALETTE[Math.abs(pid.hashCode()) % TEAM_PALETTE.length];
        TeamRecord team = new TeamRecord(UUID.randomUUID(), name, color, pid);
        team.members.add(pid);
        teams.put(team.id, team);
        playerToTeam.put(pid, team.id);
        setDirty();
        return team;
    }

    public TeamRecord getPlayerTeam(UUID playerId) {
        UUID tid = playerToTeam.get(playerId);
        return tid == null ? null : teams.get(tid);
    }

    public TeamRecord getTeam(UUID teamId) {
        return teams.get(teamId);
    }

    public Collection<TeamRecord> getTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public Map<ChunkKey, ClaimRecord> getClaims() {
        return Collections.unmodifiableMap(claims);
    }

    public Map<UUID, String> getPlayerNames() {
        return Collections.unmodifiableMap(playerNames);
    }

    public String getPlayerName(UUID playerId) {
        return playerNames.getOrDefault(playerId, playerId.toString().substring(0, 8));
    }

    public ClaimRecord getClaim(String dimension, int chunkX, int chunkZ) {
        return claims.get(new ChunkKey(dimension, chunkX, chunkZ));
    }

    public int countClaims(UUID teamId) {
        int count = 0;
        for (ClaimRecord c : claims.values()) {
            if (c.teamId.equals(teamId)) count++;
        }
        return count;
    }

    public int countForceLoaded(UUID teamId) {
        int count = 0;
        for (ClaimRecord c : claims.values()) {
            if (c.teamId.equals(teamId) && c.forceLoaded) count++;
        }
        return count;
    }

    public int maxClaimsFor(TeamRecord team) {
        return Math.max(1, team.members.size()) * TeamConfig.MAX_CLAIMS_PER_PLAYER.get();
    }

    public int maxForceLoadedFor(TeamRecord team) {
        return Math.max(1, team.members.size()) * TeamConfig.MAX_FORCE_LOADED_PER_PLAYER.get();
    }

    public boolean isAllied(TeamRecord ownerTeam, UUID actorPlayerId) {
        TeamRecord actorTeam = getPlayerTeam(actorPlayerId);
        if (actorTeam == null) return false;
        return ownerTeam.id.equals(actorTeam.id) || ownerTeam.allies.contains(actorTeam.id);
    }

    public boolean canEdit(String dimension, int chunkX, int chunkZ, Player player) {
        ClaimRecord claim = getClaim(dimension, chunkX, chunkZ);
        if (claim == null) return true;
        if (player instanceof FakePlayer) return TeamConfig.ALLOW_FAKE_PLAYERS.get();
        TeamRecord team = teams.get(claim.teamId);
        if (team == null) return true;
        UUID pid = player.getUUID();
        if (team.members.contains(pid)) return true;
        return team.allowAllyEdit && isAllied(team, pid);
    }

    public boolean canInteract(String dimension, int chunkX, int chunkZ, Player player) {
        ClaimRecord claim = getClaim(dimension, chunkX, chunkZ);
        if (claim == null) return true;
        if (player instanceof FakePlayer) return TeamConfig.ALLOW_FAKE_PLAYERS.get();
        TeamRecord team = teams.get(claim.teamId);
        if (team == null) return true;
        if (team.publicInteract || canEdit(dimension, chunkX, chunkZ, player)) return true;
        return team.allowAllyInteract && isAllied(team, player.getUUID());
    }

    public boolean claimChunk(MinecraftServer server, ServerPlayer player, String dimension, int chunkX, int chunkZ, boolean forceLoad) {
        TeamRecord team = ensurePlayerTeam(player);
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        ClaimRecord existing = claims.get(key);

        if (existing != null && !existing.teamId.equals(team.id)) {
            return false; // Already claimed by another team
        }

        if (existing == null) {
            if (countClaims(team.id) >= maxClaimsFor(team)) {
                return false;
            }
            boolean canForce = forceLoad && countForceLoaded(team.id) < maxForceLoadedFor(team);
            claims.put(key, new ClaimRecord(team.id, canForce));
            if (canForce) {
                setChunkForcedOnServer(server, dimension, chunkX, chunkZ, true);
            }
            setDirty();
            return true;
        } else if (forceLoad && !existing.forceLoaded) {
            if (countForceLoaded(team.id) >= maxForceLoadedFor(team)) {
                return false;
            }
            existing.forceLoaded = true;
            setChunkForcedOnServer(server, dimension, chunkX, chunkZ, true);
            setDirty();
            return true;
        }
        return false;
    }

    public boolean unclaimChunk(MinecraftServer server, ServerPlayer player, String dimension, int chunkX, int chunkZ, boolean onlyUnforce) {
        TeamRecord team = ensurePlayerTeam(player);
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        ClaimRecord existing = claims.get(key);
        if (existing == null || !existing.teamId.equals(team.id)) {
            return false;
        }
        if (onlyUnforce) {
            if (!existing.forceLoaded) return false;
            existing.forceLoaded = false;
            setChunkForcedOnServer(server, dimension, chunkX, chunkZ, false);
            setDirty();
            return true;
        }
        if (existing.forceLoaded) {
            setChunkForcedOnServer(server, dimension, chunkX, chunkZ, false);
        }
        claims.remove(key);
        setDirty();
        return true;
    }

    public void unclaimAllForTeam(MinecraftServer server, UUID teamId) {
        Iterator<Map.Entry<ChunkKey, ClaimRecord>> it = claims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ChunkKey, ClaimRecord> e = it.next();
            if (e.getValue().teamId.equals(teamId)) {
                if (e.getValue().forceLoaded) {
                    setChunkForcedOnServer(server, e.getKey().dimension(), e.getKey().x(), e.getKey().z(), false);
                }
                it.remove();
            }
        }
        setDirty();
    }

    public void joinTeam(MinecraftServer server, ServerPlayer player, UUID targetTeamId) {
        TeamRecord target = teams.get(targetTeamId);
        if (target == null) return;
        UUID pid = player.getUUID();
        if (!target.invited.contains(pid)) return;

        leaveCurrentTeamInternal(server, pid, player.getGameProfile().getName(), false);
        target.invited.remove(pid);
        target.members.add(pid);
        playerToTeam.put(pid, target.id);
        setDirty();
    }

    public void leaveTeam(MinecraftServer server, ServerPlayer player) {
        UUID pid = player.getUUID();
        TeamRecord current = getPlayerTeam(pid);
        if (current == null || current.members.size() <= 1) return; // Already in solo team
        leaveCurrentTeamInternal(server, pid, player.getGameProfile().getName(), true);
        setDirty();
    }

    public void kickMember(MinecraftServer server, ServerPlayer ownerPlayer, UUID targetPlayerId) {
        TeamRecord team = getPlayerTeam(ownerPlayer.getUUID());
        if (team == null || !team.owner.equals(ownerPlayer.getUUID())) return;
        if (targetPlayerId.equals(ownerPlayer.getUUID()) || !team.members.contains(targetPlayerId)) return;

        leaveCurrentTeamInternal(server, targetPlayerId, getPlayerName(targetPlayerId), true);
        setDirty();
    }

    private void leaveCurrentTeamInternal(MinecraftServer server, UUID pid, String playerName, boolean createSoloTeam) {
        TeamRecord oldTeam = getPlayerTeam(pid);
        if (oldTeam != null) {
            oldTeam.members.remove(pid);
            if (oldTeam.members.isEmpty()) {
                unclaimAllForTeam(server, oldTeam.id);
                teams.remove(oldTeam.id);
                for (TeamRecord t : teams.values()) {
                    t.allies.remove(oldTeam.id);
                }
            } else if (oldTeam.owner.equals(pid)) {
                oldTeam.owner = oldTeam.members.iterator().next();
            }
        }
        if (createSoloTeam) {
            int color = TEAM_PALETTE[Math.abs(pid.hashCode()) % TEAM_PALETTE.length];
            TeamRecord solo = new TeamRecord(UUID.randomUUID(), playerName, color, pid);
            solo.members.add(pid);
            teams.put(solo.id, solo);
            playerToTeam.put(pid, solo.id);
        }
    }

    public void applyAllForceLoads(MinecraftServer server) {
        for (Map.Entry<ChunkKey, ClaimRecord> e : claims.entrySet()) {
            if (e.getValue().forceLoaded) {
                setChunkForcedOnServer(server, e.getKey().dimension(), e.getKey().x(), e.getKey().z(), true);
            }
        }
    }

    private static void setChunkForcedOnServer(MinecraftServer server, String dimension, int cx, int cz, boolean forced) {
        ResourceLocation loc = new ResourceLocation(dimension);
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, loc);
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            level.setChunkForced(cx, cz, forced);
        }
    }

    public static TeamSavedData load(CompoundTag root) {
        TeamSavedData data = new TeamSavedData();

        ListTag namesTag = root.getList("PlayerNames", Tag.TAG_COMPOUND);
        for (int i = 0; i < namesTag.size(); i++) {
            CompoundTag n = namesTag.getCompound(i);
            data.playerNames.put(n.getUUID("Id"), n.getString("Name"));
        }

        ListTag teamsTag = root.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < teamsTag.size(); i++) {
            CompoundTag t = teamsTag.getCompound(i);
            UUID id = t.getUUID("Id");
            String name = t.getString("Name");
            int color = t.getInt("Color");
            UUID owner = t.getUUID("Owner");
            TeamRecord team = new TeamRecord(id, name, color, owner);
            team.friendlyFire = t.getBoolean("FriendlyFire");
            team.allowAllyInteract = !t.contains("AllowAllyInteract") || t.getBoolean("AllowAllyInteract");
            team.allowAllyEdit = t.getBoolean("AllowAllyEdit");
            team.publicInteract = t.getBoolean("PublicInteract");

            ListTag membersTag = t.getList("Members", Tag.TAG_STRING);
            for (int j = 0; j < membersTag.size(); j++) {
                UUID m = UUID.fromString(membersTag.getString(j));
                team.members.add(m);
                data.playerToTeam.put(m, id);
            }
            ListTag invitedTag = t.getList("Invited", Tag.TAG_STRING);
            for (int j = 0; j < invitedTag.size(); j++) {
                team.invited.add(UUID.fromString(invitedTag.getString(j)));
            }
            ListTag alliesTag = t.getList("Allies", Tag.TAG_STRING);
            for (int j = 0; j < alliesTag.size(); j++) {
                team.allies.add(UUID.fromString(alliesTag.getString(j)));
            }
            data.teams.put(id, team);
        }

        ListTag claimsTag = root.getList("Claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < claimsTag.size(); i++) {
            CompoundTag c = claimsTag.getCompound(i);
            ChunkKey key = new ChunkKey(c.getString("Dim"), c.getInt("X"), c.getInt("Z"));
            UUID teamId = c.getUUID("Team");
            boolean forced = c.getBoolean("Forced");
            if (data.teams.containsKey(teamId)) {
                data.claims.put(key, new ClaimRecord(teamId, forced));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag namesTag = new ListTag();
        for (Map.Entry<UUID, String> e : playerNames.entrySet()) {
            CompoundTag n = new CompoundTag();
            n.putUUID("Id", e.getKey());
            n.putString("Name", e.getValue());
            namesTag.add(n);
        }
        root.put("PlayerNames", namesTag);

        ListTag teamsTag = new ListTag();
        for (TeamRecord team : teams.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", team.id);
            t.putString("Name", team.name);
            t.putInt("Color", team.color);
            t.putUUID("Owner", team.owner);
            t.putBoolean("FriendlyFire", team.friendlyFire);
            t.putBoolean("AllowAllyInteract", team.allowAllyInteract);
            t.putBoolean("AllowAllyEdit", team.allowAllyEdit);
            t.putBoolean("PublicInteract", team.publicInteract);

            ListTag membersTag = new ListTag();
            for (UUID m : team.members) membersTag.add(StringTag.valueOf(m.toString()));
            t.put("Members", membersTag);

            ListTag invitedTag = new ListTag();
            for (UUID inv : team.invited) invitedTag.add(StringTag.valueOf(inv.toString()));
            t.put("Invited", invitedTag);

            ListTag alliesTag = new ListTag();
            for (UUID ally : team.allies) alliesTag.add(StringTag.valueOf(ally.toString()));
            t.put("Allies", alliesTag);

            teamsTag.add(t);
        }
        root.put("Teams", teamsTag);

        ListTag claimsTag = new ListTag();
        for (Map.Entry<ChunkKey, ClaimRecord> e : claims.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("Dim", e.getKey().dimension());
            c.putInt("X", e.getKey().x());
            c.putInt("Z", e.getKey().z());
            c.putUUID("Team", e.getValue().teamId);
            c.putBoolean("Forced", e.getValue().forceLoaded);
            claimsTag.add(c);
        }
        root.put("Claims", claimsTag);
        return root;
    }
}
