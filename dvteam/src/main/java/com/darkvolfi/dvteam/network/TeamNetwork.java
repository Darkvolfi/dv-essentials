package com.darkvolfi.dvteam.network;

import com.darkvolfi.dvteam.DVTeam;
import com.darkvolfi.dvteam.data.ClanData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class TeamNetwork {
    private static final String PROTOCOL = "2"; // 2: clans and settlements
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DVTeam.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private TeamNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(SyncClanPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncClanPacket::encode)
                .decoder(SyncClanPacket::decode)
                .consumerMainThread(SyncClanPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChunkClaimActionPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChunkClaimActionPacket::encode)
                .decoder(ChunkClaimActionPacket::decode)
                .consumerMainThread(ChunkClaimActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(ClanActionPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClanActionPacket::encode)
                .decoder(ClanActionPacket::decode)
                .consumerMainThread(ClanActionPacket::handle)
                .add();
    }

    public static void syncToPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncClanPacket(ClanData.get(server), server));
    }

    public static void syncToAll(MinecraftServer server) {
        if (server == null) return;
        CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncClanPacket(ClanData.get(server), server));
    }

    /** Shows a failure reason (a translation key from {@link ClanData}) above the hotbar. */
    public static void tell(ServerPlayer player, String errorKey) {
        if (errorKey != null) player.displayClientMessage(Component.translatable(errorKey).withStyle(ChatFormatting.RED), true);
    }

    public static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
