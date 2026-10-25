package com.darkvolfi.dvvein.network;

import com.darkvolfi.dvvein.VeinMiner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client tells the server when the vein key is pressed or released. */
public record VeinKeyPacket(boolean held) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(held);
    }

    public static VeinKeyPacket decode(FriendlyByteBuf buf) {
        return new VeinKeyPacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) VeinMiner.setKeyHeld(player, held);
    }
}
