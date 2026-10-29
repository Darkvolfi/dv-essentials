package com.darkvolfi.dvbackpack.network;

import com.darkvolfi.dvbackpack.BackpackMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the sleeping bag colour picked for the backpack that is open. */
public record SetBagColorPacket(int rgb) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(rgb);
    }

    public static SetBagColorPacket decode(FriendlyByteBuf buf) {
        return new SetBagColorPacket(buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof BackpackMenu menu) menu.setBagColor(rgb);
    }
}
