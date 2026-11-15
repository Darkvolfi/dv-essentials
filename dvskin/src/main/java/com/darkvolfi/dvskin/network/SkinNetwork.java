package com.darkvolfi.dvskin.network;

import com.darkvolfi.dvskin.DVSkin;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Either side may lack DV Skin: the other one then simply never hears from it. */
public final class SkinNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DVSkin.MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private SkinNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(UploadPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UploadPacket::encode)
                .decoder(UploadPacket::decode)
                .consumerMainThread(UploadPacket::handle)
                .add();
        CHANNEL.messageBuilder(SkinPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkinPacket::encode)
                .decoder(SkinPacket::decode)
                .consumerMainThread(SkinPacket::handle)
                .add();
        CHANNEL.messageBuilder(ResultPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ResultPacket::encode)
                .decoder(ResultPacket::decode)
                .consumerMainThread(ResultPacket::handle)
                .add();
    }

    /** True when the other end of the connection has DV Skin. */
    public static boolean present(Connection connection) {
        return connection != null && CHANNEL.isRemotePresent(connection);
    }
}
