package com.darkvolfi.dvteam.network;

import com.darkvolfi.dvteam.data.TeamSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ChunkClaimActionPacket {
    public enum Action {
        CLAIM,
        UNCLAIM,
        FORCE_LOAD,
        UNFORCE_LOAD
    }

    private final Action action;
    private final String dimension;
    private final int chunkX;
    private final int chunkZ;

    public ChunkClaimActionPacket(Action action, String dimension, int chunkX, int chunkZ) {
        this.action = action;
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(dimension, 128);
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
    }

    public static ChunkClaimActionPacket decode(FriendlyByteBuf buf) {
        return new ChunkClaimActionPacket(
                buf.readEnum(Action.class),
                buf.readUtf(128),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    public static void handle(ChunkClaimActionPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            TeamSavedData data = TeamSavedData.get(server);
            boolean changed = switch (pkt.action) {
                case CLAIM -> data.claimChunk(server, player, pkt.dimension, pkt.chunkX, pkt.chunkZ, false);
                case FORCE_LOAD -> data.claimChunk(server, player, pkt.dimension, pkt.chunkX, pkt.chunkZ, true);
                case UNCLAIM -> data.unclaimChunk(server, player, pkt.dimension, pkt.chunkX, pkt.chunkZ, false);
                case UNFORCE_LOAD -> data.unclaimChunk(server, player, pkt.dimension, pkt.chunkX, pkt.chunkZ, true);
            };
            if (changed) {
                TeamNetwork.syncToAll(server);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
