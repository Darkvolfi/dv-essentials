package com.darkvolfi.dvslots.network;

import com.darkvolfi.dvslots.menu.SlotsMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: open the DV slots screen. */
public record OpenSlotsPacket() {

    public void encode(FriendlyByteBuf buf) {}

    public static OpenSlotsPacket decode(FriendlyByteBuf buf) {
        return new OpenSlotsPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) SlotsMenu.open(player);
    }
}
