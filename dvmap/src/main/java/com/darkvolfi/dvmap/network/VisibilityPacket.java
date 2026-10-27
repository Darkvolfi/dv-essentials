package com.darkvolfi.dvmap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: whether this player appears on other players' maps, and with their name. */
public record VisibilityPacket(boolean hidden, boolean nameHidden) {
    public static void encode(VisibilityPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.hidden);
        buf.writeBoolean(p.nameHidden);
    }

    public static VisibilityPacket decode(FriendlyByteBuf buf) {
        return new VisibilityPacket(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(VisibilityPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        // Hidden on the map always hides the name too.
        if (sender != null) MapNetwork.setVisibility(sender, new VisibilityPacket(p.hidden, p.hidden || p.nameHidden));
        ctx.get().setPacketHandled(true);
    }
}
