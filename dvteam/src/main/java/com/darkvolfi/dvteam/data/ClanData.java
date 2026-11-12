package com.darkvolfi.dvteam.data;

import com.darkvolfi.dvteam.TeamConfig;
import com.darkvolfi.dvteam.data.ClanTypes.Perm;
import com.darkvolfi.dvteam.data.ClanTypes.Manage;
import com.darkvolfi.dvteam.data.ClanTypes.SettlementType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side state of clans, their settlements (capital, villages, trade centers), claimed chunks and wars.
 * All rules live here; packets and commands only call these methods. Methods that can fail return a
 * translation key describing why (shown to the player), or null on success.
 */
public class ClanData extends SavedData {
    private static final String DATA_NAME = "dvteam_clans";
    private static final long MINUTE = 60_000L;

    public record ChunkKey(String dimension, int x, int z) {}

    public static final class Clan {
        public final UUID id;
        public String name;
        public String tag;
        public int color;
        public BannerData banner;
        /** Player → rank id ({@link ClanRank#LEADER_ID} for the leader). */
        public final Map<UUID, Integer> members = new LinkedHashMap<>();
        /** The clan's ranks, highest first. Never empty: new members join the last one. */
        public final List<ClanRank> ranks = new ArrayList<>();
        private int nextRankId = ClanRank.LEADER_ID + 1;
        public final Set<UUID> invited = new LinkedHashSet<>();
        public final Set<UUID> allies = new LinkedHashSet<>();
        /** Clans this clan offered an alliance to. */
        public final Set<UUID> allyRequests = new LinkedHashSet<>();
        public boolean friendlyFire;
        /** Server side only; never synced. */
        public final ClanVault vault;

        public Clan(UUID id, String name, String tag, int color, BannerData banner) {
            this.id = id;
            this.vault = new ClanVault(id);
            this.name = name;
            this.tag = tag;
            this.color = color & 0xFFFFFF;
            this.banner = banner;
        }

        /** The clan colour for text: very dark colours are lifted toward white so they stay readable on dark panels and chat. */
        public int textColor() {
            return ClanTypes.readable(color);
        }

        // ---- ranks. "Order" is the position in the hierarchy: the leader is -1, then the ranks top down.

        public boolean isLeader(UUID player) {
            Integer r = members.get(player);
            return r != null && r == ClanRank.LEADER_ID;
        }

        public ClanRank rankById(int rankId) {
            for (ClanRank r : ranks) if (r.id == rankId) return r;
            return null;
        }

        /** The player's rank; null for the leader and for non-members. */
        public ClanRank rankOf(UUID player) {
            Integer r = members.get(player);
            return r == null ? null : rankById(r);
        }

        public ClanRank lowest() {
            return ranks.get(ranks.size() - 1);
        }

        public int orderOf(int rankId) {
            if (rankId == ClanRank.LEADER_ID) return -1;
            for (int i = 0; i < ranks.size(); i++) if (ranks.get(i).id == rankId) return i;
            return Integer.MAX_VALUE;
        }

        public int order(UUID player) {
            Integer r = members.get(player);
            return r == null ? Integer.MAX_VALUE : orderOf(r);
        }

        /** Whether {@code actor} is ranked strictly above {@code target}. */
        public boolean above(UUID actor, UUID target) {
            return members.containsKey(actor) && order(actor) < order(target);
        }

        /** A management right: the leader has all of them, everyone else what their rank grants. */
        public boolean can(UUID player, Manage right) {
            if (isLeader(player)) return true;
            ClanRank r = rankOf(player);
            return r != null && right.in(r.manage);
        }

        /** What a member may do on the clan's own land. */
        public boolean mayLand(UUID player, Perm perm) {
            if (isLeader(player)) return true;
            ClanRank r = rankOf(player);
            return r != null && perm.in(r.land);
        }

        ClanRank newRank(String name, int color, int manage, int land) {
            ClanRank r = new ClanRank(nextRankId++, name, color, manage, land);
            ranks.add(r);
            return r;
        }

        /**
         * The two ranks every clan starts with; they carry the rights of the old fixed Officer and Member ranks.
         * Both may do everything on the clan's land, as members always could.
         */
        void addDefaultRanks(boolean officerVault, boolean memberVault) {
            int officer = Manage.INVITE.bit() | Manage.KICK.bit() | Manage.CLAIM.bit() | (officerVault ? Manage.VAULT.bit() : 0);
            newRank(ClanRank.DEFAULT_OFFICER, 0x55AAFF, officer, Perm.all());
            newRank(ClanRank.DEFAULT_MEMBER, 0xAAAAAA, memberVault ? Manage.VAULT.bit() : 0, Perm.all());
        }

        public UUID leader() {
            for (Map.Entry<UUID, Integer> e : members.entrySet()) if (e.getValue() == ClanRank.LEADER_ID) return e.getKey();
            return members.keySet().iterator().next();
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 64);
            buf.writeUtf(tag, 16);
            buf.writeInt(color);
            banner.write(buf);
            buf.writeVarInt(ranks.size());
            for (ClanRank r : ranks) r.write(buf);
            buf.writeVarInt(members.size());
            members.forEach((p, r) -> {
                buf.writeUUID(p);
                buf.writeVarInt(r);
            });
            writeIds(buf, invited);
            writeIds(buf, allies);
            writeIds(buf, allyRequests);
            buf.writeBoolean(friendlyFire);
        }

        static Clan read(FriendlyByteBuf buf) {
            Clan c = new Clan(buf.readUUID(), buf.readUtf(64), buf.readUtf(16), buf.readInt(), BannerData.read(buf));
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) c.ranks.add(ClanRank.read(buf));
            n = buf.readVarInt();
            for (int i = 0; i < n; i++) c.members.put(buf.readUUID(), buf.readVarInt());
            c.invited.addAll(readIds(buf));
            c.allies.addAll(readIds(buf));
            c.allyRequests.addAll(readIds(buf));
            c.friendlyFire = buf.readBoolean();
            return c;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putString("Name", name);
            t.putString("Tag", tag);
            t.putInt("Color", color);
            t.put("Banner", banner.save());
            ListTag rl = new ListTag();
            for (ClanRank r : ranks) rl.add(r.save());
            t.put("Ranks", rl);
            t.putInt("NextRankId", nextRankId);
            ListTag m = new ListTag();
            members.forEach((p, r) -> {
                CompoundTag e = new CompoundTag();
                e.putUUID("Id", p);
                e.putInt("RankId", r);
                m.add(e);
            });
            t.put("Members", m);
            t.put("Invited", saveIds(invited));
            t.put("Allies", saveIds(allies));
            t.put("AllyRequests", saveIds(allyRequests));
            t.putBoolean("FriendlyFire", friendlyFire);
            t.put("Vault", vault.createTag());
            return t;
        }

        static Clan load(CompoundTag t) {
            Clan c = new Clan(t.getUUID("Id"), t.getString("Name"), t.getString("Tag"), t.getInt("Color"),
                    BannerData.load(t.getCompound("Banner")));
            ListTag m = t.getList("Members", Tag.TAG_COMPOUND);
            if (t.contains("Ranks", Tag.TAG_LIST)) {
                ListTag rl = t.getList("Ranks", Tag.TAG_COMPOUND);
                for (int i = 0; i < rl.size() && c.ranks.size() < ClanRank.MAX_RANKS; i++) {
                    ClanRank r = ClanRank.load(rl.getCompound(i));
                    if (r.id != ClanRank.LEADER_ID && c.rankById(r.id) == null) c.ranks.add(r);
                }
                int next = t.getInt("NextRankId");
                for (ClanRank r : c.ranks) next = Math.max(next, r.id + 1);
                c.nextRankId = Math.max(next, ClanRank.LEADER_ID + 1);
                if (c.ranks.isEmpty()) c.addDefaultRanks(true, false); // damaged data: never leave a clan without ranks
                for (int i = 0; i < m.size(); i++) {
                    CompoundTag e = m.getCompound(i);
                    int r = e.getInt("RankId");
                    // A rank that no longer exists (damaged data) falls back to the lowest one.
                    if (r != ClanRank.LEADER_ID && c.rankById(r) == null) r = c.lowest().id;
                    c.members.put(e.getUUID("Id"), r);
                }
            } else {
                // Saves before 1.0.1.0 had three fixed ranks (LEADER, OFFICER, MEMBER) and a "VaultAccess" field
                // naming the lowest rank that could open the vault (default OFFICER). They become the two default
                // ranks with exactly the old rights: the officer rank may invite, kick and claim; both may do
                // everything on the clan's land; the vault right goes to the ranks VaultAccess allowed.
                String vaultAccess = t.contains("VaultAccess") ? t.getString("VaultAccess") : "OFFICER";
                c.addDefaultRanks(!vaultAccess.equals("LEADER"), vaultAccess.equals("MEMBER"));
                int officer = c.ranks.get(0).id;
                int member = c.ranks.get(1).id;
                for (int i = 0; i < m.size(); i++) {
                    CompoundTag e = m.getCompound(i);
                    String old = e.getString("Rank");
                    int r = old.equals("LEADER") ? ClanRank.LEADER_ID : old.equals("OFFICER") ? officer : member;
                    c.members.put(e.getUUID("Id"), r);
                }
            }
            c.invited.addAll(loadIds(t.getList("Invited", Tag.TAG_STRING)));
            c.allies.addAll(loadIds(t.getList("Allies", Tag.TAG_STRING)));
            c.allyRequests.addAll(loadIds(t.getList("AllyRequests", Tag.TAG_STRING)));
            c.friendlyFire = t.getBoolean("FriendlyFire");
            c.vault.fromTag(t.getList("Vault", Tag.TAG_COMPOUND));
            return c;
        }
    }

    public static final class Settlement {
        public final UUID id;
        public final UUID clanId;
        public String name;
        public final SettlementType type;
        public final String dimension;
        /** The first chunk; for the capital, the 3x3 around it can't be raided. */
        public final int coreX;
        public final int coreZ;
        public int allyPerms;
        public int publicPerms;

        public Settlement(UUID id, UUID clanId, String name, SettlementType type, String dimension, int coreX, int coreZ) {
            this.id = id;
            this.clanId = clanId;
            this.name = name;
            this.type = type;
            this.dimension = dimension;
            this.coreX = coreX;
            this.coreZ = coreZ;
            this.allyPerms = ClanTypes.defaultAllyPerms(type);
            this.publicPerms = ClanTypes.defaultPublicPerms(type);
        }

        public boolean isCore(int cx, int cz) {
            return type == SettlementType.CAPITAL && Math.abs(cx - coreX) <= 1 && Math.abs(cz - coreZ) <= 1;
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUUID(clanId);
            buf.writeUtf(name, 64);
            buf.writeEnum(type);
            buf.writeUtf(dimension, 128);
            buf.writeVarInt(coreX);
            buf.writeVarInt(coreZ);
            buf.writeVarInt(allyPerms);
            buf.writeVarInt(publicPerms);
        }

        static Settlement read(FriendlyByteBuf buf) {
            Settlement s = new Settlement(buf.readUUID(), buf.readUUID(), buf.readUtf(64), buf.readEnum(SettlementType.class),
                    buf.readUtf(128), buf.readVarInt(), buf.readVarInt());
            s.allyPerms = buf.readVarInt();
            s.publicPerms = buf.readVarInt();
            return s;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putUUID("Clan", clanId);
            t.putString("Name", name);
            t.putString("Type", type.name());
            t.putString("Dim", dimension);
            t.putInt("CoreX", coreX);
            t.putInt("CoreZ", coreZ);
            t.putInt("AllyPerms", allyPerms);
            t.putInt("PublicPerms", publicPerms);
            return t;
        }

        static Settlement load(CompoundTag t) {
            Settlement s = new Settlement(t.getUUID("Id"), t.getUUID("Clan"), t.getString("Name"),
                    SettlementType.valueOf(t.getString("Type")), t.getString("Dim"), t.getInt("CoreX"), t.getInt("CoreZ"));
            s.allyPerms = t.getInt("AllyPerms");
            s.publicPerms = t.getInt("PublicPerms");
            return s;
        }
    }

    public static final class Claim {
        public final UUID settlementId;
        public boolean forced;

        public Claim(UUID settlementId, boolean forced) {
            this.settlementId = settlementId;
            this.forced = forced;
        }
    }

    /** Times are wall-clock milliseconds, so a war keeps running across server restarts. */
    public static final class War {
        public final UUID attacker;
        public final UUID defender;
        public final long startsAt;
        public final long endsAt;
        public boolean attackerPeace;
        public boolean defenderPeace;
        public boolean announcedStart;

        public War(UUID attacker, UUID defender, long startsAt, long endsAt) {
            this.attacker = attacker;
            this.defender = defender;
            this.startsAt = startsAt;
            this.endsAt = endsAt;
        }

        public boolean involves(UUID clan) {
            return attacker.equals(clan) || defender.equals(clan);
        }

        public UUID other(UUID clan) {
            return attacker.equals(clan) ? defender : attacker;
        }

        public boolean active(long now) {
            return now >= startsAt && now < endsAt;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Attacker", attacker);
            t.putUUID("Defender", defender);
            t.putLong("Starts", startsAt);
            t.putLong("Ends", endsAt);
            t.putBoolean("AttackerPeace", attackerPeace);
            t.putBoolean("DefenderPeace", defenderPeace);
            t.putBoolean("Announced", announcedStart);
            return t;
        }

        static War load(CompoundTag t) {
            War w = new War(t.getUUID("Attacker"), t.getUUID("Defender"), t.getLong("Starts"), t.getLong("Ends"));
            w.attackerPeace = t.getBoolean("AttackerPeace");
            w.defenderPeace = t.getBoolean("DefenderPeace");
            w.announcedStart = t.getBoolean("Announced");
            return w;
        }
    }

    private final Map<UUID, Clan> clans = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerClan = new HashMap<>();
    private final Map<UUID, String> playerNames = new LinkedHashMap<>();
    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
    private final Map<ChunkKey, Claim> claims = new LinkedHashMap<>();
    private final List<War> wars = new ArrayList<>();
    /** "clanA|clanB" (sorted) → wall-clock time until which they can't fight again. */
    private final Map<String, Long> cooldowns = new HashMap<>();
    /** Counts changes, so packet handlers can tell whether clients need a new sync. */
    private long revision;

    public static ClanData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ClanData::load, ClanData::new, DATA_NAME);
    }

    @Override
    public void setDirty() {
        super.setDirty();
        revision++;
    }

    public long revision() {
        return revision;
    }

    // ---------------------------------------------------------------- lookups

    public Clan clanOf(UUID player) {
        UUID id = playerClan.get(player);
        return id == null ? null : clans.get(id);
    }

    public Clan clan(UUID id) {
        return clans.get(id);
    }

    public Collection<Clan> clans() {
        return Collections.unmodifiableCollection(clans.values());
    }

    public Collection<Settlement> settlements() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    public Settlement settlement(UUID id) {
        return settlements.get(id);
    }

    public Map<ChunkKey, Claim> claims() {
        return Collections.unmodifiableMap(claims);
    }

    public List<War> wars() {
        return Collections.unmodifiableList(wars);
    }

    public Map<UUID, String> playerNames() {
        return Collections.unmodifiableMap(playerNames);
    }

    public String playerName(UUID id) {
        return playerNames.getOrDefault(id, id.toString().substring(0, 8));
    }

    public void rememberName(ServerPlayer player) {
        if (!player.getGameProfile().getName().equals(playerNames.put(player.getUUID(), player.getGameProfile().getName()))) {
            setDirty();
        }
    }

    public Settlement settlementAt(String dim, int cx, int cz) {
        Claim c = claims.get(new ChunkKey(dim, cx, cz));
        return c == null ? null : settlements.get(c.settlementId);
    }

    public Claim claimAt(String dim, int cx, int cz) {
        return claims.get(new ChunkKey(dim, cx, cz));
    }

    public Clan clanAt(String dim, int cx, int cz) {
        Settlement s = settlementAt(dim, cx, cz);
        return s == null ? null : clans.get(s.clanId);
    }

    public int chunkCount(UUID clanId) {
        int n = 0;
        for (Claim c : claims.values()) {
            Settlement s = settlements.get(c.settlementId);
            if (s != null && s.clanId.equals(clanId)) n++;
        }
        return n;
    }

    public int level(Clan clan) {
        return ClanLevel.level(ClanLevel.points(clan.members.size(), chunkCount(clan.id)));
    }

    public int forcedCount(UUID clanId) {
        int n = 0;
        for (Claim c : claims.values()) {
            Settlement s = settlements.get(c.settlementId);
            if (c.forced && s != null && s.clanId.equals(clanId)) n++;
        }
        return n;
    }

    public int settlementChunks(UUID settlementId) {
        int n = 0;
        for (Claim c : claims.values()) if (c.settlementId.equals(settlementId)) n++;
        return n;
    }

    public int settlementCount(UUID clanId, SettlementType type) {
        int n = 0;
        for (Settlement s : settlements.values()) if (s.clanId.equals(clanId) && s.type == type) n++;
        return n;
    }

    public boolean mutualAllies(Clan a, Clan b) {
        return a.allies.contains(b.id) && b.allies.contains(a.id);
    }

    public War warBetween(UUID a, UUID b) {
        for (War w : wars) if (w.involves(a) && w.involves(b)) return w;
        return null;
    }

    public boolean atWar(UUID a, UUID b) {
        War w = warBetween(a, b);
        return w != null && w.active(System.currentTimeMillis());
    }

    // ---------------------------------------------------------------- clan management

    private static final int NAME_MIN = 3;
    private static final int NAME_MAX = 24;

    public String create(ServerPlayer player, String name, String tag, int color, BannerData banner) {
        if (clanOf(player.getUUID()) != null) return "dvteam.err.already_in_clan";
        String n = name.trim();
        String t = tag.trim().toUpperCase(Locale.ROOT);
        if (n.length() < NAME_MIN || n.length() > NAME_MAX) return "dvteam.err.name_length";
        if (!t.matches("[\\p{L}\\p{N}]{2,4}")) return "dvteam.err.tag_format";
        for (Clan c : clans.values()) {
            if (c.name.equalsIgnoreCase(n)) return "dvteam.err.name_taken";
            if (c.tag.equalsIgnoreCase(t)) return "dvteam.err.tag_taken";
        }
        Clan clan = new Clan(UUID.randomUUID(), n, t, color, banner.validated());
        clan.vault.addListener(v -> setDirty());
        clan.addDefaultRanks(true, false); // as before: officers may open the vault, members not
        clan.members.put(player.getUUID(), ClanRank.LEADER_ID);
        clans.put(clan.id, clan);
        playerClan.put(player.getUUID(), clan.id);
        for (Clan c : clans.values()) c.invited.remove(player.getUUID());
        setDirty();
        return null;
    }

    public void disband(MinecraftServer server, Clan clan) {
        // Disbanding is always done by the leader, who is online: the vault goes to them.
        ServerPlayer leader = server.getPlayerList().getPlayer(clan.leader());
        if (leader != null) {
            for (ItemStack stack : clan.vault.removeAllItems()) ItemHandlerHelper.giveItemToPlayer(leader, stack);
        }
        Iterator<Map.Entry<ChunkKey, Claim>> it = claims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ChunkKey, Claim> e = it.next();
            Settlement s = settlements.get(e.getValue().settlementId);
            if (s != null && s.clanId.equals(clan.id)) {
                if (e.getValue().forced) setForced(server, e.getKey(), false);
                it.remove();
            }
        }
        settlements.values().removeIf(s -> s.clanId.equals(clan.id));
        wars.removeIf(w -> w.involves(clan.id));
        for (Clan c : clans.values()) {
            c.allies.remove(clan.id);
            c.allyRequests.remove(clan.id);
        }
        for (UUID m : clan.members.keySet()) playerClan.remove(m);
        clans.remove(clan.id);
        setDirty();
    }

    public String leave(MinecraftServer server, ServerPlayer player) {
        Clan clan = clanOf(player.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        if (clan.isLeader(player.getUUID())) {
            if (clan.members.size() > 1) return "dvteam.err.leader_leave";
            disband(server, clan);
            return null;
        }
        clan.members.remove(player.getUUID());
        playerClan.remove(player.getUUID());
        setDirty();
        return null;
    }

    public String invite(ServerPlayer actor, UUID target) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        if (!clan.can(actor.getUUID(), Manage.INVITE)) return "dvteam.err.rank";
        if (clanOf(target) != null) return "dvteam.err.target_in_clan";
        clan.invited.add(target);
        setDirty();
        return null;
    }

    public String join(ServerPlayer player, UUID clanId) {
        Clan clan = clans.get(clanId);
        if (clan == null || !clan.invited.contains(player.getUUID())) return "dvteam.err.no_invite";
        if (clanOf(player.getUUID()) != null) return "dvteam.err.already_in_clan";
        for (Clan c : clans.values()) c.invited.remove(player.getUUID());
        clan.members.put(player.getUUID(), clan.lowest().id);
        playerClan.put(player.getUUID(), clan.id);
        setDirty();
        return null;
    }

    public void decline(ServerPlayer player, UUID clanId) {
        Clan clan = clans.get(clanId);
        if (clan != null && clan.invited.remove(player.getUUID())) setDirty();
    }

    /** Members with the kick right can remove members ranked strictly below them; the leader anyone but themself. */
    public String kick(ServerPlayer actor, UUID target) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null || !clan.members.containsKey(target) || target.equals(actor.getUUID())) return "dvteam.err.invalid";
        if (!clan.can(actor.getUUID(), Manage.KICK) || !clan.above(actor.getUUID(), target)) return "dvteam.err.rank";
        clan.members.remove(target);
        playerClan.remove(target);
        setDirty();
        return null;
    }

    /**
     * Gives {@code target} the rank {@code rankId}. With the ranks right, only to members strictly below the actor
     * and only ranks strictly below the actor's own. {@link ClanRank#LEADER_ID} hands over leadership (leader
     * only); the old leader takes the highest rank.
     */
    public String setRank(ServerPlayer actor, UUID target, int rankId) {
        Clan clan = clanOf(actor.getUUID());
        UUID me = actor.getUUID();
        if (clan == null || !clan.members.containsKey(target) || target.equals(me)) return "dvteam.err.invalid";
        if (rankId == ClanRank.LEADER_ID) {
            if (!clan.isLeader(me)) return "dvteam.err.rank";
            clan.members.put(me, clan.ranks.get(0).id);
            clan.members.put(target, ClanRank.LEADER_ID);
        } else {
            if (clan.rankById(rankId) == null) return "dvteam.err.invalid";
            if (!clan.can(me, Manage.RANKS) || !clan.above(me, target) || clan.order(me) >= clan.orderOf(rankId)) {
                return "dvteam.err.rank";
            }
            clan.members.put(target, rankId);
        }
        setDirty();
        return null;
    }

    // ---------------------------------------------------------------- rank definitions (leader only)

    private static String rankFieldsError(String name, int color, int manage, int land) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > ClanRank.NAME_MAX || n.chars().anyMatch(Character::isISOControl)) return "dvteam.err.rank_name";
        if ((color & ~0xFFFFFF) != 0 || (manage & ~Manage.all()) != 0 || (land & ~Perm.all()) != 0) return "dvteam.err.invalid";
        return null;
    }

    /** Adds a rank at the bottom, so it becomes the rank new members join. */
    public String addRank(ServerPlayer actor, String name, int color, int manage, int land) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        if (!clan.isLeader(actor.getUUID())) return "dvteam.err.rank";
        if (clan.ranks.size() >= ClanRank.MAX_RANKS) return "dvteam.err.rank_limit";
        String error = rankFieldsError(name, color, manage, land);
        if (error != null) return error;
        clan.newRank(name.trim(), color, manage, land);
        setDirty();
        return null;
    }

    public String editRank(ServerPlayer actor, int rankId, String name, int color, int manage, int land) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        if (!clan.isLeader(actor.getUUID())) return "dvteam.err.rank";
        ClanRank r = clan.rankById(rankId);
        if (r == null) return "dvteam.err.invalid";
        String error = rankFieldsError(name, color, manage, land);
        if (error != null) return error;
        // The screen sends a default rank's translation key back unchanged when its name was not edited.
        r.name = name.trim();
        r.color = color;
        r.manage = manage;
        r.land = land;
        setDirty();
        return null;
    }

    /** Moves a rank one step up ({@code up}) or down the order. */
    public String moveRank(ServerPlayer actor, int rankId, boolean up) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        if (!clan.isLeader(actor.getUUID())) return "dvteam.err.rank";
        int i = clan.orderOf(rankId);
        int j = up ? i - 1 : i + 1;
        if (i < 0 || i >= clan.ranks.size() || j < 0 || j >= clan.ranks.size()) return "dvteam.err.invalid";
        Collections.swap(clan.ranks, i, j);
        setDirty();
        return null;
    }

    /** Deletes a rank; its members move to the rank below it, or (for the lowest rank) to the new lowest. */
    public String deleteRank(ServerPlayer actor, int rankId) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        if (!clan.isLeader(actor.getUUID())) return "dvteam.err.rank";
        int i = clan.orderOf(rankId);
        if (i < 0 || i >= clan.ranks.size()) return "dvteam.err.invalid";
        if (clan.ranks.size() <= 1) return "dvteam.err.last_rank";
        int to = clan.ranks.get(i + 1 < clan.ranks.size() ? i + 1 : i - 1).id;
        clan.members.replaceAll((p, r) -> r == rankId ? to : r);
        clan.ranks.remove(i);
        setDirty();
        return null;
    }

    /** Name, tag, colour, flag, friendly fire: leader only. Null fields stay unchanged. */
    public String edit(ServerPlayer actor, String name, String tag, Integer color, BannerData banner, Boolean friendlyFire) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        if (!clan.isLeader(actor.getUUID())) return "dvteam.err.rank";
        if (name != null) {
            String n = name.trim();
            if (n.length() < NAME_MIN || n.length() > NAME_MAX) return "dvteam.err.name_length";
            for (Clan c : clans.values()) if (c != clan && c.name.equalsIgnoreCase(n)) return "dvteam.err.name_taken";
            clan.name = n;
        }
        if (tag != null) {
            String t = tag.trim().toUpperCase(Locale.ROOT);
            if (!t.matches("[\\p{L}\\p{N}]{2,4}")) return "dvteam.err.tag_format";
            for (Clan c : clans.values()) if (c != clan && c.tag.equalsIgnoreCase(t)) return "dvteam.err.tag_taken";
            clan.tag = t;
        }
        if (color != null) clan.color = color & 0xFFFFFF;
        if (banner != null) clan.banner = banner.validated();
        if (friendlyFire != null) clan.friendlyFire = friendlyFire;
        setDirty();
        return null;
    }

    // ---------------------------------------------------------------- diplomacy

    /** Offers an alliance, or accepts the other clan's offer. */
    public String requestAlliance(ServerPlayer actor, UUID otherId) {
        Clan clan = clanOf(actor.getUUID());
        Clan other = clans.get(otherId);
        if (clan == null || other == null || other == clan) return "dvteam.err.invalid";
        if (!clan.can(actor.getUUID(), Manage.DIPLOMACY)) return "dvteam.err.rank";
        if (warBetween(clan.id, other.id) != null) return "dvteam.err.at_war";
        if (other.allyRequests.contains(clan.id)) {
            other.allyRequests.remove(clan.id);
            clan.allyRequests.remove(other.id);
            clan.allies.add(other.id);
            other.allies.add(clan.id);
        } else {
            clan.allyRequests.add(other.id);
        }
        setDirty();
        return null;
    }

    public String breakAlliance(ServerPlayer actor, UUID otherId) {
        Clan clan = clanOf(actor.getUUID());
        Clan other = clans.get(otherId);
        if (clan == null || other == null) return "dvteam.err.invalid";
        if (!clan.can(actor.getUUID(), Manage.DIPLOMACY)) return "dvteam.err.rank";
        clan.allies.remove(other.id);
        other.allies.remove(clan.id);
        clan.allyRequests.remove(other.id);
        setDirty();
        return null;
    }

    public String declareWar(ServerPlayer actor, UUID targetId) {
        if (!TeamConfig.WARS_ENABLED.get()) return "dvteam.err.wars_disabled";
        Clan clan = clanOf(actor.getUUID());
        Clan target = clans.get(targetId);
        if (clan == null || target == null || target == clan) return "dvteam.err.invalid";
        if (!clan.can(actor.getUUID(), Manage.DIPLOMACY)) return "dvteam.err.rank";
        if (warBetween(clan.id, target.id) != null) return "dvteam.err.at_war";
        Long until = cooldowns.get(pairKey(clan.id, target.id));
        long now = System.currentTimeMillis();
        if (until != null && until > now) return "dvteam.err.war_cooldown";
        // War breaks any alliance between the two.
        clan.allies.remove(target.id);
        target.allies.remove(clan.id);
        clan.allyRequests.remove(target.id);
        target.allyRequests.remove(clan.id);
        long start = now + TeamConfig.WAR_WARMUP_MINUTES.get() * MINUTE;
        wars.add(new War(clan.id, target.id, start, start + TeamConfig.WAR_MAX_MINUTES.get() * MINUTE));
        setDirty();
        return null;
    }

    /** Peace needs both leaders; returns true when the war ended. */
    public boolean offerPeace(ServerPlayer actor, UUID otherId) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null || !clan.can(actor.getUUID(), Manage.DIPLOMACY)) return false;
        War w = warBetween(clan.id, otherId);
        if (w == null) return false;
        if (w.attacker.equals(clan.id)) w.attackerPeace = true;
        else w.defenderPeace = true;
        setDirty();
        if (w.attackerPeace && w.defenderPeace) {
            endWar(w);
            return true;
        }
        return false;
    }

    public War surrender(ServerPlayer actor, UUID otherId) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null || !clan.can(actor.getUUID(), Manage.DIPLOMACY)) return null;
        War w = warBetween(clan.id, otherId);
        if (w != null) endWar(w);
        return w;
    }

    public void endWar(War w) {
        wars.remove(w);
        cooldowns.put(pairKey(w.attacker, w.defender), System.currentTimeMillis() + TeamConfig.WAR_COOLDOWN_HOURS.get() * 60 * MINUTE);
        setDirty();
    }

    private static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    // ---------------------------------------------------------------- settlements and claims

    public String renameSettlement(ServerPlayer actor, UUID settlementId, String name) {
        Settlement s = settlements.get(settlementId);
        Clan clan = clanOf(actor.getUUID());
        if (s == null || clan == null || !s.clanId.equals(clan.id)) return "dvteam.err.invalid";
        if (!clan.can(actor.getUUID(), Manage.CLAIM)) return "dvteam.err.rank";
        String n = name.trim();
        if (n.length() < NAME_MIN || n.length() > NAME_MAX) return "dvteam.err.name_length";
        s.name = n;
        setDirty();
        return null;
    }

    public String setPerms(ServerPlayer actor, UUID settlementId, int allyPerms, int publicPerms) {
        Settlement s = settlements.get(settlementId);
        Clan clan = clanOf(actor.getUUID());
        if (s == null || clan == null || !s.clanId.equals(clan.id)) return "dvteam.err.invalid";
        if (!clan.can(actor.getUUID(), Manage.SETTLEMENTS)) return "dvteam.err.rank";
        s.allyPerms = allyPerms & Perm.all();
        s.publicPerms = publicPerms & Perm.all();
        setDirty();
        return null;
    }

    /**
     * Claims a chunk for the actor's clan. A chunk next to one of the clan's settlements joins it; a chunk
     * elsewhere founds a new one (the capital if the clan has none yet), which needs {@code newName}.
     */
    public String claim(MinecraftServer server, ServerPlayer actor, String dim, int cx, int cz,
                        String newName, SettlementType newType) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        if (!clan.can(actor.getUUID(), Manage.CLAIM)) return "dvteam.err.rank";
        ChunkKey key = new ChunkKey(dim, cx, cz);
        Claim existing = claims.get(key);
        if (existing != null) {
            Settlement s = settlements.get(existing.settlementId);
            return s != null && s.clanId.equals(clan.id) ? "dvteam.err.already_yours" : "dvteam.err.taken";
        }
        if (chunkCount(clan.id) >= ClanLevel.chunkLimit(TeamConfig.MAX_CHUNKS_PER_CLAN.get(), level(clan))) return "dvteam.err.limit_clan";
        if (tooCloseToOthers(clan.id, dim, cx, cz)) return "dvteam.err.too_close";

        Set<UUID> neighbours = neighbourSettlements(clan.id, dim, cx, cz);
        Settlement target;
        if (neighbours.size() > 1) return "dvteam.err.would_merge";
        if (neighbours.size() == 1) {
            target = settlements.get(neighbours.iterator().next());
            if (target.type != SettlementType.CAPITAL && settlementChunks(target.id) >= TeamConfig.MAX_VILLAGE_CHUNKS.get()) {
                return "dvteam.err.limit_settlement";
            }
        } else {
            SettlementType type = settlementCount(clan.id, SettlementType.CAPITAL) == 0 ? SettlementType.CAPITAL
                    : (newType == SettlementType.TRADE ? SettlementType.TRADE : SettlementType.VILLAGE);
            if (type == SettlementType.VILLAGE && settlementCount(clan.id, type) >= TeamConfig.MAX_VILLAGES.get()) {
                return "dvteam.err.limit_villages";
            }
            if (type == SettlementType.TRADE && settlementCount(clan.id, type) >= TeamConfig.MAX_TRADE_CENTERS.get()) {
                return "dvteam.err.limit_trade";
            }
            String n = newName == null ? "" : newName.trim();
            if (n.isEmpty()) return "dvteam.err.need_name";
            if (n.length() < NAME_MIN || n.length() > NAME_MAX) return "dvteam.err.name_length";
            target = new Settlement(UUID.randomUUID(), clan.id, n, type, dim, cx, cz);
            settlements.put(target.id, target);
        }
        claims.put(key, new Claim(target.id, false));
        setDirty();
        return null;
    }

    public String unclaim(MinecraftServer server, ServerPlayer actor, String dim, int cx, int cz) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        if (!clan.can(actor.getUUID(), Manage.CLAIM)) return "dvteam.err.rank";
        ChunkKey key = new ChunkKey(dim, cx, cz);
        Claim claim = claims.get(key);
        Settlement s = claim == null ? null : settlements.get(claim.settlementId);
        if (s == null || !s.clanId.equals(clan.id)) return "dvteam.err.not_yours";
        if (wouldSplit(s.id, key)) return "dvteam.err.would_split";
        if (claim.forced) setForced(server, key, false);
        claims.remove(key);
        if (settlementChunks(s.id) == 0) settlements.remove(s.id);
        setDirty();
        return null;
    }

    public String setForceLoaded(MinecraftServer server, ServerPlayer actor, String dim, int cx, int cz, boolean forced) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        if (!clan.can(actor.getUUID(), Manage.CLAIM)) return "dvteam.err.rank";
        ChunkKey key = new ChunkKey(dim, cx, cz);
        Claim claim = claims.get(key);
        Settlement s = claim == null ? null : settlements.get(claim.settlementId);
        if (s == null || !s.clanId.equals(clan.id)) return "dvteam.err.not_yours";
        if (claim.forced == forced) return null;
        if (forced && forcedCount(clan.id) >= ClanLevel.forcedLimit(TeamConfig.MAX_FORCE_LOADED.get(), level(clan))) return "dvteam.err.limit_forced";
        claim.forced = forced;
        setForced(server, key, forced);
        setDirty();
        return null;
    }

    public void unclaimAll(MinecraftServer server, ServerPlayer actor) {
        Clan clan = clanOf(actor.getUUID());
        if (clan == null || !clan.isLeader(actor.getUUID())) return;
        Iterator<Map.Entry<ChunkKey, Claim>> it = claims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ChunkKey, Claim> e = it.next();
            Settlement s = settlements.get(e.getValue().settlementId);
            if (s != null && s.clanId.equals(clan.id)) {
                if (e.getValue().forced) setForced(server, e.getKey(), false);
                it.remove();
            }
        }
        settlements.values().removeIf(s -> s.clanId.equals(clan.id));
        setDirty();
    }

    private Set<UUID> neighbourSettlements(UUID clanId, String dim, int cx, int cz) {
        Set<UUID> result = new HashSet<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            Settlement s = settlementAt(dim, cx + d[0], cz + d[1]);
            if (s != null && s.clanId.equals(clanId)) result.add(s.id);
        }
        return result;
    }

    private boolean tooCloseToOthers(UUID clanId, String dim, int cx, int cz) {
        int gap = TeamConfig.MIN_GAP.get();
        for (int dz = -gap; dz <= gap; dz++) {
            for (int dx = -gap; dx <= gap; dx++) {
                Settlement s = settlementAt(dim, cx + dx, cz + dz);
                if (s != null && !s.clanId.equals(clanId)) return true;
            }
        }
        return false;
    }

    /** Whether removing {@code removed} would leave the settlement in more than one piece. */
    private boolean wouldSplit(UUID settlementId, ChunkKey removed) {
        Set<ChunkKey> rest = new HashSet<>();
        for (Map.Entry<ChunkKey, Claim> e : claims.entrySet()) {
            if (e.getValue().settlementId.equals(settlementId) && !e.getKey().equals(removed)) rest.add(e.getKey());
        }
        if (rest.isEmpty()) return false;
        Set<ChunkKey> seen = new HashSet<>();
        Deque<ChunkKey> queue = new ArrayDeque<>();
        ChunkKey first = rest.iterator().next();
        queue.add(first);
        seen.add(first);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            ChunkKey k = queue.poll();
            for (int[] d : dirs) {
                ChunkKey n = new ChunkKey(k.dimension(), k.x() + d[0], k.z() + d[1]);
                if (rest.contains(n) && seen.add(n)) queue.add(n);
            }
        }
        return seen.size() != rest.size();
    }

    public void applyAllForceLoads(MinecraftServer server) {
        claims.forEach((k, c) -> {
            if (c.forced) setForced(server, k, true);
        });
    }

    private static void setForced(MinecraftServer server, ChunkKey key, boolean forced) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(key.dimension())));
        if (level != null) level.setChunkForced(key.x(), key.z(), forced);
    }

    // ---------------------------------------------------------------- protection

    /** Whether {@code player} may do {@code perm} in the chunk. Unclaimed land allows everything. */
    public boolean allows(MinecraftServer server, Player player, String dim, int cx, int cz, Perm perm) {
        Settlement s = settlementAt(dim, cx, cz);
        if (s == null) return true;
        if (player instanceof FakePlayer) return TeamConfig.ALLOW_FAKE_PLAYERS.get();
        Clan owner = clans.get(s.clanId);
        if (owner == null) return true;
        Clan mine = clanOf(player.getUUID());
        // Members may do what their rank allows on their own land, and never less than what everyone may.
        if (mine == owner) return owner.mayLand(player.getUUID(), perm) || perm.in(s.publicPerms);
        if (mine != null && raidable(server, s, cx, cz, mine.id)) return true;
        if (s.publicPerms != 0 && perm.in(s.publicPerms)) return true;
        return mine != null && mutualAllies(owner, mine) && perm.in(s.allyPerms);
    }

    /** Both sides of an active war can raid each other, except trade centers and the capital core. */
    public boolean raidable(MinecraftServer server, Settlement s, int cx, int cz, UUID raiderClan) {
        if (s.type == SettlementType.TRADE || s.isCore(cx, cz)) return false;
        if (!atWar(s.clanId, raiderClan)) return false;
        return TeamConfig.OFFLINE_RAIDS.get() || anyOnline(server, clans.get(s.clanId));
    }

    /** Explosions only damage claimed land while its clan is in an active war (and could be raided). */
    public boolean explosionAllowed(MinecraftServer server, String dim, int cx, int cz) {
        Settlement s = settlementAt(dim, cx, cz);
        if (s == null || !TeamConfig.PROTECT_EXPLOSIONS.get()) return true;
        if (s.type == SettlementType.TRADE || s.isCore(cx, cz)) return false;
        long now = System.currentTimeMillis();
        for (War w : wars) {
            if (w.involves(s.clanId) && w.active(now)
                    && (TeamConfig.OFFLINE_RAIDS.get() || anyOnline(server, clans.get(s.clanId)))) return true;
        }
        return false;
    }

    public boolean inTradeCenter(String dim, int cx, int cz) {
        Settlement s = settlementAt(dim, cx, cz);
        return s != null && s.type == SettlementType.TRADE;
    }

    private static boolean anyOnline(MinecraftServer server, Clan clan) {
        if (clan == null) return false;
        for (UUID m : clan.members.keySet()) if (server.getPlayerList().getPlayer(m) != null) return true;
        return false;
    }

    // ---------------------------------------------------------------- sync

    public void writeSync(FriendlyByteBuf buf, MinecraftServer server) {
        buf.writeVarInt(TeamConfig.MAX_CHUNKS_PER_CLAN.get());
        buf.writeVarInt(TeamConfig.MAX_VILLAGES.get());
        buf.writeVarInt(TeamConfig.MAX_TRADE_CENTERS.get());
        buf.writeVarInt(TeamConfig.MAX_VILLAGE_CHUNKS.get());
        buf.writeVarInt(TeamConfig.MAX_FORCE_LOADED.get());
        buf.writeBoolean(TeamConfig.WARS_ENABLED.get());
        buf.writeVarInt(TeamConfig.MIN_GAP.get());

        Map<UUID, String> names = new LinkedHashMap<>(playerNames);
        Set<UUID> online = new LinkedHashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            online.add(p.getUUID());
            names.put(p.getUUID(), p.getGameProfile().getName());
        }
        buf.writeVarInt(names.size());
        names.forEach((id, n) -> {
            buf.writeUUID(id);
            buf.writeUtf(n, 64);
        });
        writeIds(buf, online);

        buf.writeVarInt(clans.size());
        for (Clan c : clans.values()) c.write(buf);
        buf.writeVarInt(settlements.size());
        for (Settlement s : settlements.values()) s.write(buf);
        buf.writeVarInt(claims.size());
        claims.forEach((k, c) -> {
            buf.writeUtf(k.dimension(), 128);
            buf.writeVarInt(k.x());
            buf.writeVarInt(k.z());
            buf.writeUUID(c.settlementId);
            buf.writeBoolean(c.forced);
        });
        // Times relative to now, so the client's clock doesn't matter.
        long now = System.currentTimeMillis();
        buf.writeVarInt(wars.size());
        for (War w : wars) {
            buf.writeUUID(w.attacker);
            buf.writeUUID(w.defender);
            buf.writeLong(w.startsAt - now);
            buf.writeLong(w.endsAt - now);
            buf.writeBoolean(w.attackerPeace);
            buf.writeBoolean(w.defenderPeace);
        }
    }

    /** Client copy of the state, decoded from {@link #writeSync}. */
    public record Snapshot(int maxChunks, int maxVillages, int maxTrades, int maxVillageChunks, int maxForced,
                           boolean warsEnabled, int minGap, Map<UUID, String> names, Set<UUID> online,
                           List<Clan> clans, List<Settlement> settlements, Map<ChunkKey, Claim> claims, List<War> wars) {
        public static Snapshot read(FriendlyByteBuf buf) {
            int maxChunks = buf.readVarInt();
            int maxVillages = buf.readVarInt();
            int maxTrades = buf.readVarInt();
            int maxVillageChunks = buf.readVarInt();
            int maxForced = buf.readVarInt();
            boolean wars = buf.readBoolean();
            int minGap = buf.readVarInt();
            Map<UUID, String> names = new LinkedHashMap<>();
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) names.put(buf.readUUID(), buf.readUtf(64));
            Set<UUID> online = readIds(buf);
            List<Clan> clans = new ArrayList<>();
            n = buf.readVarInt();
            for (int i = 0; i < n; i++) clans.add(Clan.read(buf));
            List<Settlement> settlements = new ArrayList<>();
            n = buf.readVarInt();
            for (int i = 0; i < n; i++) settlements.add(Settlement.read(buf));
            Map<ChunkKey, Claim> claims = new HashMap<>();
            n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                ChunkKey k = new ChunkKey(buf.readUtf(128), buf.readVarInt(), buf.readVarInt());
                claims.put(k, new Claim(buf.readUUID(), buf.readBoolean()));
            }
            long now = System.currentTimeMillis();
            List<War> warList = new ArrayList<>();
            n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                War w = new War(buf.readUUID(), buf.readUUID(), now + buf.readLong(), now + buf.readLong());
                w.attackerPeace = buf.readBoolean();
                w.defenderPeace = buf.readBoolean();
                warList.add(w);
            }
            return new Snapshot(maxChunks, maxVillages, maxTrades, maxVillageChunks, maxForced, wars, minGap,
                    names, online, clans, settlements, claims, warList);
        }
    }

    // ---------------------------------------------------------------- persistence

    public static ClanData load(CompoundTag root) {
        ClanData data = new ClanData();
        ListTag names = root.getList("PlayerNames", Tag.TAG_COMPOUND);
        for (int i = 0; i < names.size(); i++) {
            CompoundTag n = names.getCompound(i);
            data.playerNames.put(n.getUUID("Id"), n.getString("Name"));
        }
        ListTag clanList = root.getList("Clans", Tag.TAG_COMPOUND);
        for (int i = 0; i < clanList.size(); i++) {
            Clan c = Clan.load(clanList.getCompound(i));
            c.vault.addListener(v -> data.setDirty());
            data.clans.put(c.id, c);
            for (UUID m : c.members.keySet()) data.playerClan.put(m, c.id);
        }
        ListTag setList = root.getList("Settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < setList.size(); i++) {
            Settlement s = Settlement.load(setList.getCompound(i));
            if (data.clans.containsKey(s.clanId)) data.settlements.put(s.id, s);
        }
        ListTag claimList = root.getList("Claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < claimList.size(); i++) {
            CompoundTag c = claimList.getCompound(i);
            UUID sid = c.getUUID("Settlement");
            if (data.settlements.containsKey(sid)) {
                data.claims.put(new ChunkKey(c.getString("Dim"), c.getInt("X"), c.getInt("Z")), new Claim(sid, c.getBoolean("Forced")));
            }
        }
        ListTag warList = root.getList("Wars", Tag.TAG_COMPOUND);
        for (int i = 0; i < warList.size(); i++) data.wars.add(War.load(warList.getCompound(i)));
        CompoundTag cd = root.getCompound("Cooldowns");
        for (String k : cd.getAllKeys()) data.cooldowns.put(k, cd.getLong(k));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag names = new ListTag();
        playerNames.forEach((id, n) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putString("Name", n);
            names.add(t);
        });
        root.put("PlayerNames", names);
        ListTag clanList = new ListTag();
        for (Clan c : clans.values()) clanList.add(c.save());
        root.put("Clans", clanList);
        ListTag setList = new ListTag();
        for (Settlement s : settlements.values()) setList.add(s.save());
        root.put("Settlements", setList);
        ListTag claimList = new ListTag();
        claims.forEach((k, c) -> {
            CompoundTag t = new CompoundTag();
            t.putString("Dim", k.dimension());
            t.putInt("X", k.x());
            t.putInt("Z", k.z());
            t.putUUID("Settlement", c.settlementId);
            t.putBoolean("Forced", c.forced);
            claimList.add(t);
        });
        root.put("Claims", claimList);
        ListTag warList = new ListTag();
        for (War w : wars) warList.add(w.save());
        root.put("Wars", warList);
        CompoundTag cd = new CompoundTag();
        long now = System.currentTimeMillis();
        cooldowns.forEach((k, v) -> {
            if (v > now) cd.putLong(k, v);
        });
        root.put("Cooldowns", cd);
        return root;
    }

    private static void writeIds(FriendlyByteBuf buf, Set<UUID> ids) {
        buf.writeVarInt(ids.size());
        for (UUID id : ids) buf.writeUUID(id);
    }

    private static Set<UUID> readIds(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Set<UUID> set = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) set.add(buf.readUUID());
        return set;
    }

    private static ListTag saveIds(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) list.add(StringTag.valueOf(id.toString()));
        return list;
    }

    private static Set<UUID> loadIds(ListTag list) {
        Set<UUID> set = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) set.add(UUID.fromString(list.getString(i)));
        return set;
    }

    /** Default flag for a new clan: plain, in the dye closest to the clan colour. */
    public static BannerData defaultBanner(int rgb) {
        DyeColor best = DyeColor.WHITE;
        long bestDist = Long.MAX_VALUE;
        for (DyeColor d : DyeColor.values()) {
            int c = d.getTextColor();
            long dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            long dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            long db = (c & 0xFF) - (rgb & 0xFF);
            long dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return BannerData.plain(best);
    }
}
