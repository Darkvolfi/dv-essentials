package com.darkvolfi.dvvein.network;

import com.darkvolfi.dvvein.VeinMiner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Client tells the server which blocks its player never wants vein-mined (sent on join and on every change). */
public record BlacklistPacket(List<ResourceLocation> blocks) {
    private static final int MAX = 256;

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(blocks.subList(0, Math.min(MAX, blocks.size())), FriendlyByteBuf::writeResourceLocation);
    }

    public static BlacklistPacket decode(FriendlyByteBuf buf) {
        return new BlacklistPacket(buf.readList(FriendlyByteBuf::readResourceLocation));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) VeinMiner.setBlacklist(player, blocks);
    }
}
