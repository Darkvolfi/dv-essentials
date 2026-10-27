package com.darkvolfi.dvmap.network;

import com.darkvolfi.dvmap.DVMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * With DV Map on the server, players see each other on the map at any distance in the same
 * dimension, unless they chose to hide. Optional on both sides.
 */
@Mod.EventBusSubscriber(modid = DVMap.MOD_ID)
public final class MapNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DVMap.MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private static final int INTERVAL_TICKS = 10;
    /** Server side: each player's choice, sent by their client. */
    private static final Map<UUID, VisibilityPacket> VISIBILITY = new HashMap<>();
    private static int ticks;

    private MapNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(VisibilityPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VisibilityPacket::encode)
                .decoder(VisibilityPacket::decode)
                .consumerMainThread(VisibilityPacket::handle)
                .add();
        CHANNEL.messageBuilder(PlayerPositionsPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerPositionsPacket::encode)
                .decoder(PlayerPositionsPacket::decode)
                .consumerMainThread(PlayerPositionsPacket::handle)
                .add();
    }

    static void setVisibility(ServerPlayer player, VisibilityPacket choice) {
        VISIBILITY.put(player.getUUID(), choice);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < INTERVAL_TICKS) return;
        ticks = 0;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> players = level.players();
            if (players.size() < 2) continue;

            List<PlayerPositionsPacket.Entry> visible = new ArrayList<>();
            List<UUID> hidden = new ArrayList<>();
            for (ServerPlayer p : players) {
                VisibilityPacket choice = VISIBILITY.get(p.getUUID());
                if (choice != null && choice.hidden()) {
                    hidden.add(p.getUUID());
                    continue;
                }
                boolean nameHidden = choice != null && choice.nameHidden();
                visible.add(new PlayerPositionsPacket.Entry(p.getUUID(),
                        nameHidden ? "" : p.getGameProfile().getName(), nameHidden,
                        p.getBlockX(), p.getBlockY(), p.getBlockZ(), p.getYRot()));
            }

            for (ServerPlayer receiver : players) {
                if (!CHANNEL.isRemotePresent(receiver.connection.connection)) continue;
                List<PlayerPositionsPacket.Entry> others = new ArrayList<>(visible.size());
                for (PlayerPositionsPacket.Entry e : visible) {
                    if (!e.id().equals(receiver.getUUID())) others.add(e);
                }
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver), new PlayerPositionsPacket(others, hidden));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VISIBILITY.remove(event.getEntity().getUUID());
    }
}
