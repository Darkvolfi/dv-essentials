package com.darkvolfi.dvmap.network;

import com.darkvolfi.dvmap.client.RemotePlayers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server → client: the other players in this dimension, and who chose to hide. */
/** {@code tagHidden}: players whose name tag over their head is hidden too (when the server allows it). */
public record PlayerPositionsPacket(List<Entry> players, List<UUID> hidden, List<UUID> tagHidden) {
    /** {@code name} is empty when the player hid it. */
    public record Entry(UUID id, String name, boolean nameHidden, int x, int y, int z, float yaw) {}

    public static void encode(PlayerPositionsPacket p, FriendlyByteBuf buf) {
        buf.writeCollection(p.players, (b, e) -> {
            b.writeUUID(e.id);
            b.writeUtf(e.name, 16);
            b.writeBoolean(e.nameHidden);
            b.writeVarInt(e.x);
            b.writeVarInt(e.y);
            b.writeVarInt(e.z);
            b.writeFloat(e.yaw);
        });
        buf.writeCollection(p.hidden, FriendlyByteBuf::writeUUID);
        buf.writeCollection(p.tagHidden, FriendlyByteBuf::writeUUID);
    }

    public static PlayerPositionsPacket decode(FriendlyByteBuf buf) {
        List<Entry> players = buf.readList(b -> new Entry(b.readUUID(), b.readUtf(16), b.readBoolean(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readFloat()));
        return new PlayerPositionsPacket(players, buf.readList(FriendlyByteBuf::readUUID), buf.readList(FriendlyByteBuf::readUUID));
    }

    public static void handle(PlayerPositionsPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RemotePlayers.accept(p));
        ctx.get().setPacketHandled(true);
    }
}
