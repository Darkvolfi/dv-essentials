package com.darkvolfi.dvteam.network;

import com.darkvolfi.dvteam.client.ClientClanCache;
import com.darkvolfi.dvteam.data.ClanData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The whole clan state, server → client. Written straight from {@link ClanData}, read into a snapshot. */
public final class SyncClanPacket {
    private final ClanData data;
    private final MinecraftServer server;
    private final ClanData.Snapshot snapshot;

    SyncClanPacket(ClanData data, MinecraftServer server) {
        this.data = data;
        this.server = server;
        this.snapshot = null;
    }

    private SyncClanPacket(ClanData.Snapshot snapshot) {
        this.data = null;
        this.server = null;
        this.snapshot = snapshot;
    }

    public static void encode(SyncClanPacket p, FriendlyByteBuf buf) {
        p.data.writeSync(buf, p.server);
    }

    public static SyncClanPacket decode(FriendlyByteBuf buf) {
        return new SyncClanPacket(ClanData.Snapshot.read(buf));
    }

    public static void handle(SyncClanPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientClanCache.apply(p.snapshot));
        ctx.get().setPacketHandled(true);
    }
}
