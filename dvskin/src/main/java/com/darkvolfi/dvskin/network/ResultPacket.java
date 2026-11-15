package com.darkvolfi.dvskin.network;

import com.darkvolfi.dvskin.client.SkinClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** What the server did with the player's upload, so the screen can say why something didn't show. */
public record ResultPacket(int code) {
    public static final int OK = 0;
    public static final int NO_SKINS = 1;
    public static final int NO_CAPES = 2;
    public static final int NOTHING_ALLOWED = 3;
    public static final int INVALID = 4;
    public static final int TOO_FAST = 5;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(code);
    }

    public static ResultPacket decode(FriendlyByteBuf buf) {
        return new ResultPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinClient.onResult(code));
    }
}
