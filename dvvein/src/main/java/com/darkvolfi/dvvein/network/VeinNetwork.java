package com.darkvolfi.dvvein.network;

import com.darkvolfi.dvvein.DVVein;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class VeinNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DVVein.MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private VeinNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(VeinKeyPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VeinKeyPacket::encode)
                .decoder(VeinKeyPacket::decode)
                .consumerMainThread(VeinKeyPacket::handle)
                .add();
    }
}
