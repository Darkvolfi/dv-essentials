package com.darkvolfi.dvteam.network;

import com.darkvolfi.dvteam.DVTeam;
import com.darkvolfi.dvteam.data.TeamSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class TeamNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DVTeam.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private TeamNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, SyncTeamDataPacket.class,
                SyncTeamDataPacket::encode,
                SyncTeamDataPacket::decode,
                SyncTeamDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(1, ChunkClaimActionPacket.class,
                ChunkClaimActionPacket::encode,
                ChunkClaimActionPacket::decode,
                ChunkClaimActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(2, TeamManagePacket.class,
                TeamManagePacket::encode,
                TeamManagePacket::decode,
                TeamManagePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void syncToPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        TeamSavedData data = TeamSavedData.get(server);
        data.ensurePlayerTeam(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), SyncTeamDataPacket.fromServer(server, data));
    }

    public static void syncToAll(MinecraftServer server) {
        if (server == null) return;
        TeamSavedData data = TeamSavedData.get(server);
        SyncTeamDataPacket pkt = SyncTeamDataPacket.fromServer(server, data);
        CHANNEL.send(PacketDistributor.ALL.noArg(), pkt);
    }
}
