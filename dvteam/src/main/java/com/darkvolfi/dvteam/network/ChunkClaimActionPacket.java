package com.darkvolfi.dvteam.network;

import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanTypes.SettlementType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Claim, unclaim or (un)force-load one chunk. When the chunk founds a new settlement,
 * {@code name} and {@code type} (village or trade center) describe it.
 */
public record ChunkClaimActionPacket(Action action, String dimension, int chunkX, int chunkZ, String name, SettlementType type) {
    public enum Action { CLAIM, UNCLAIM, FORCE_LOAD, UNFORCE_LOAD }

    public static ChunkClaimActionPacket of(Action action, String dimension, int chunkX, int chunkZ) {
        return new ChunkClaimActionPacket(action, dimension, chunkX, chunkZ, "", SettlementType.VILLAGE);
    }

    public static void encode(ChunkClaimActionPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeUtf(p.dimension, 128);
        buf.writeVarInt(p.chunkX);
        buf.writeVarInt(p.chunkZ);
        buf.writeUtf(p.name, 64);
        buf.writeEnum(p.type);
    }

    public static ChunkClaimActionPacket decode(FriendlyByteBuf buf) {
        return new ChunkClaimActionPacket(buf.readEnum(Action.class), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(64), buf.readEnum(SettlementType.class));
    }

    public static void handle(ChunkClaimActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        MinecraftServer server = player.getServer();
        ClanData data = ClanData.get(server);
        String error = switch (p.action) {
            case CLAIM -> data.claim(server, player, p.dimension, p.chunkX, p.chunkZ, p.name, p.type);
            case UNCLAIM -> data.unclaim(server, player, p.dimension, p.chunkX, p.chunkZ);
            case FORCE_LOAD -> data.setForceLoaded(server, player, p.dimension, p.chunkX, p.chunkZ, true);
            case UNFORCE_LOAD -> data.setForceLoaded(server, player, p.dimension, p.chunkX, p.chunkZ, false);
        };
        TeamNetwork.tell(player, error);
        if (error == null) TeamNetwork.syncToAll(server);
    }
}
