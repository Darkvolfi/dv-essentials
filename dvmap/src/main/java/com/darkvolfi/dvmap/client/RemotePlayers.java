package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvmap.MapConfig;
import com.darkvolfi.dvmap.network.MapNetwork;
import com.darkvolfi.dvmap.network.PlayerPositionsPacket;
import com.darkvolfi.dvmap.network.VisibilityPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Client copy of what the server shares about other players; empty when the server has no DV Map. */
public final class RemotePlayers {
    private static final long STALE_MS = 3000;

    private static List<PlayerPositionsPacket.Entry> players = List.of();
    private static Set<UUID> hidden = Set.of();
    private static Set<UUID> nameHidden = Set.of();
    private static long lastUpdate;
    private static VisibilityPacket lastSent;

    private RemotePlayers() {}

    public static void accept(PlayerPositionsPacket packet) {
        players = packet.players();
        hidden = new HashSet<>(packet.hidden());
        Set<UUID> names = new HashSet<>();
        for (PlayerPositionsPacket.Entry e : players) if (e.nameHidden()) names.add(e.id());
        nameHidden = names;
        lastUpdate = System.currentTimeMillis();
    }

    private static boolean fresh() {
        return System.currentTimeMillis() - lastUpdate < STALE_MS;
    }

    public static List<PlayerPositionsPacket.Entry> players() {
        return fresh() ? players : List.of();
    }

    /** The player chose not to appear on maps; respected even when they are close by. */
    public static boolean isHidden(UUID id) {
        return fresh() && hidden.contains(id);
    }

    public static boolean isNameHidden(UUID id) {
        return fresh() && nameHidden.contains(id);
    }

    public static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && MapNetwork.CHANNEL.isRemotePresent(connection.getConnection());
    }

    /** Sends this player's hide choices when they change or after joining a server. */
    public static void tick() {
        if (!serverHasMod()) return;
        boolean hide = MapConfig.HIDE_ME.get();
        VisibilityPacket now = new VisibilityPacket(hide, hide || MapConfig.HIDE_MY_NAME.get());
        if (now.equals(lastSent)) return;
        MapNetwork.CHANNEL.sendToServer(now);
        lastSent = now;
    }

    public static void reset() {
        players = List.of();
        hidden = Set.of();
        nameHidden = Set.of();
        lastUpdate = 0;
        lastSent = null;
    }
}
