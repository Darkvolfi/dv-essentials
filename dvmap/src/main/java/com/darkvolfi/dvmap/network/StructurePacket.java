package com.darkvolfi.dvmap.network;

import com.darkvolfi.dvmap.client.StructureMarkers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the player walked into a structure; {@code x}/{@code z} is its centre. */
public record StructurePacket(ResourceLocation structure, ResourceLocation dimension, int x, int z) {
    public static void encode(StructurePacket p, FriendlyByteBuf buf) {
        buf.writeResourceLocation(p.structure);
        buf.writeResourceLocation(p.dimension);
        buf.writeVarInt(p.x);
        buf.writeVarInt(p.z);
    }

    public static StructurePacket decode(FriendlyByteBuf buf) {
        return new StructurePacket(buf.readResourceLocation(), buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(StructurePacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> StructureMarkers.discovered(p.structure, p.dimension, p.x, p.z));
        ctx.get().setPacketHandled(true);
    }
}
