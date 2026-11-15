package com.darkvolfi.dvskin.network;

import com.darkvolfi.dvskin.SkinData;
import com.darkvolfi.dvskin.client.SkinTextures;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** A player's checked look, from the server to everyone with DV Skin. Empty data: back to their own skin. */
public record SkinPacket(UUID player, byte[] data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeByteArray(data);
    }

    public static SkinPacket decode(FriendlyByteBuf buf) {
        return new SkinPacket(buf.readUUID(), buf.readByteArray(SkinData.MAX_BYTES));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinTextures.receive(player, data));
    }
}
