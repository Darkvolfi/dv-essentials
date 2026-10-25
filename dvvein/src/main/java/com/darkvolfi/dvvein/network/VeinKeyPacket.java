package com.darkvolfi.dvvein.network;

import com.darkvolfi.dvvein.VeinMiner;
import com.darkvolfi.dvvein.VeinShape;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client tells the server whether the vein key is held and which shape is selected. */
public record VeinKeyPacket(boolean held, VeinShape shape) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(held);
        buf.writeVarInt(shape.ordinal());
    }

    public static VeinKeyPacket decode(FriendlyByteBuf buf) {
        return new VeinKeyPacket(buf.readBoolean(), VeinShape.byId(buf.readVarInt()));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) VeinMiner.setKeyState(player, held, shape);
    }
}
