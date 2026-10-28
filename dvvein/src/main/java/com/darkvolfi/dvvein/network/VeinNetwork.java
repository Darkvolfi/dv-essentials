package com.darkvolfi.dvvein.network;

import com.darkvolfi.dvvein.DVVein;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class VeinNetwork {
    private static final String VERSION = "3"; // 2: key packet carries the shape; 3: blacklist packet
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
        CHANNEL.messageBuilder(BlacklistPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BlacklistPacket::encode)
                .decoder(BlacklistPacket::decode)
                .consumerMainThread(BlacklistPacket::handle)
                .add();
    }
}
