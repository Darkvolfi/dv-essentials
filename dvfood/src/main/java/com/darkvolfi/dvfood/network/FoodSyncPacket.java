package com.darkvolfi.dvfood.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FoodSyncPacket(float saturation, float exhaustion) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(saturation);
        buf.writeFloat(exhaustion);
    }

    public static FoodSyncPacket decode(FriendlyByteBuf buf) {
        return new FoodSyncPacket(buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(this));
    }

    private static final class ClientHandler {
        static void apply(FoodSyncPacket packet) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            player.getFoodData().setSaturation(packet.saturation);
            player.getFoodData().setExhaustion(packet.exhaustion);
        }
    }
}
