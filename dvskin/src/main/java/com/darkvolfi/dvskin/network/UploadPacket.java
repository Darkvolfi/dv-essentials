package com.darkvolfi.dvskin.network;

import com.darkvolfi.dvskin.SkinData;
import com.darkvolfi.dvskin.SkinServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** One chunk of the player's {@link SkinData}; {@code count} chunks make the whole. */
public record UploadPacket(int index, int count, byte[] data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(index);
        buf.writeVarInt(count);
        buf.writeByteArray(data);
    }

    public static UploadPacket decode(FriendlyByteBuf buf) {
        return new UploadPacket(buf.readVarInt(), buf.readVarInt(), buf.readByteArray(SkinData.CHUNK));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) SkinServer.receive(player, this);
    }
}
