package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanTypes.Rank;
import com.darkvolfi.dvteam.data.ClanTypes.SettlementType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The client's copy of the clan state, replaced on every sync from the server. */
public final class ClientClanCache {
    private static ClanData.Snapshot snapshot;
    private static final Map<UUID, ClanData.Clan> CLANS = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_CLAN = new HashMap<>();
    private static final Map<UUID, ClanData.Settlement> SETTLEMENTS = new HashMap<>();
    /** Settlement id → {centre x, centre z (blocks), chunk count}. */
    private static final Map<UUID, double[]> CENTRES = new HashMap<>();

    private ClientClanCache() {}

    public static void apply(ClanData.Snapshot s) {
        snapshot = s;
        CLANS.clear();
        PLAYER_CLAN.clear();
        for (ClanData.Clan c : s.clans()) {
            CLANS.put(c.id, c);
            for (UUID m : c.members.keySet()) PLAYER_CLAN.put(m, c.id);
        }
        SETTLEMENTS.clear();
        for (ClanData.Settlement st : s.settlements()) SETTLEMENTS.put(st.id, st);
        CENTRES.clear();
        s.claims().forEach((k, c) -> {
            double[] acc = CENTRES.computeIfAbsent(c.settlementId, id -> new double[3]);
            acc[0] += k.x() * 16 + 8;
            acc[1] += k.z() * 16 + 8;
            acc[2]++;
        });
        for (double[] acc : CENTRES.values()) {
            acc[0] /= acc[2];
            acc[1] /= acc[2];
        }
        if (Minecraft.getInstance().screen instanceof ClanScreen screen) screen.refresh();
    }

    public static void clear() {
        snapshot = null;
        CLANS.clear();
        PLAYER_CLAN.clear();
        SETTLEMENTS.clear();
        CENTRES.clear();
    }

    public static boolean ready() {
        return snapshot != null;
    }

    public static ClanData.Snapshot limits() {
        return snapshot;
    }

    private static UUID me() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getUUID();
    }

    public static ClanData.Clan myClan() {
        UUID me = me();
        UUID id = me == null ? null : PLAYER_CLAN.get(me);
        return id == null ? null : CLANS.get(id);
    }

    public static Rank myRank() {
        ClanData.Clan c = myClan();
        return c == null ? null : c.rank(me());
    }

    public static boolean iAm(Rank rank) {
        Rank r = myRank();
        return r != null && r.atLeast(rank);
    }

    public static ClanData.Clan clan(UUID id) {
        return CLANS.get(id);
    }

    public static ClanData.Clan clanOf(UUID player) {
        UUID id = PLAYER_CLAN.get(player);
        return id == null ? null : CLANS.get(id);
    }

    public static Collection<ClanData.Clan> clans() {
        return snapshot == null ? List.of() : snapshot.clans();
    }

    /** Clans that invited this player. */
    public static List<ClanData.Clan> invitesForMe() {
        List<ClanData.Clan> list = new ArrayList<>();
        UUID me = me();
        for (ClanData.Clan c : clans()) if (me != null && c.invited.contains(me)) list.add(c);
        return list;
    }

    public static Collection<ClanData.Settlement> settlements() {
        return SETTLEMENTS.values();
    }

    public static List<ClanData.Settlement> settlementsOf(UUID clanId) {
        List<ClanData.Settlement> list = new ArrayList<>();
        if (snapshot != null) for (ClanData.Settlement s : snapshot.settlements()) if (s.clanId.equals(clanId)) list.add(s);
        list.sort((a, b) -> a.type.ordinal() - b.type.ordinal());
        return list;
    }

    public static ClanData.Settlement settlement(UUID id) {
        return SETTLEMENTS.get(id);
    }

    public static ClanData.Claim claimAt(ResourceKey<Level> dim, int cx, int cz) {
        if (snapshot == null || dim == null) return null;
        return snapshot.claims().get(new ClanData.ChunkKey(dim.location().toString(), cx, cz));
    }

    public static ClanData.Settlement settlementAt(ResourceKey<Level> dim, int cx, int cz) {
        ClanData.Claim c = claimAt(dim, cx, cz);
        return c == null ? null : SETTLEMENTS.get(c.settlementId);
    }

    /** {centre x, centre z, chunks} of a settlement, or null. */
    public static double[] centre(UUID settlementId) {
        return CENTRES.get(settlementId);
    }

    public static int chunkCount(UUID clanId) {
        int n = 0;
        for (Map.Entry<UUID, double[]> e : CENTRES.entrySet()) {
            ClanData.Settlement s = SETTLEMENTS.get(e.getKey());
            if (s != null && s.clanId.equals(clanId)) n += (int) e.getValue()[2];
        }
        return n;
    }

    public static int settlementChunks(UUID settlementId) {
        double[] c = CENTRES.get(settlementId);
        return c == null ? 0 : (int) c[2];
    }

    public static int forcedCount(UUID clanId) {
        if (snapshot == null) return 0;
        int n = 0;
        for (ClanData.Claim c : snapshot.claims().values()) {
            ClanData.Settlement s = SETTLEMENTS.get(c.settlementId);
            if (c.forced && s != null && s.clanId.equals(clanId)) n++;
        }
        return n;
    }

    public static int settlementCount(UUID clanId, SettlementType type) {
        int n = 0;
        for (ClanData.Settlement s : SETTLEMENTS.values()) if (s.clanId.equals(clanId) && s.type == type) n++;
        return n;
    }

    public static String playerName(UUID id) {
        return snapshot == null ? id.toString().substring(0, 8) : snapshot.names().getOrDefault(id, id.toString().substring(0, 8));
    }

    public static Map<UUID, String> playerNames() {
        return snapshot == null ? Map.of() : snapshot.names();
    }

    public static boolean online(UUID id) {
        return snapshot != null && snapshot.online().contains(id);
    }

    public static List<ClanData.War> wars() {
        return snapshot == null ? List.of() : snapshot.wars();
    }

    public static ClanData.War warBetween(UUID a, UUID b) {
        for (ClanData.War w : wars()) if (w.involves(a) && w.involves(b)) return w;
        return null;
    }

    /** Whether my clan is fighting the given clan right now (not just declared). */
    public static boolean atWarWithMe(UUID clanId) {
        ClanData.Clan mine = myClan();
        ClanData.War w = mine == null ? null : warBetween(mine.id, clanId);
        return w != null && w.active(System.currentTimeMillis());
    }
}
