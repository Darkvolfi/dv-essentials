package com.darkvolfi.dvbackpack.network;

import com.darkvolfi.dvbackpack.DVBackpack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BackpackNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DVBackpack.MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private BackpackNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(OpenBackpackPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenBackpackPacket::encode)
                .decoder(OpenBackpackPacket::decode)
                .consumerMainThread(OpenBackpackPacket::handle)
                .add();
        CHANNEL.messageBuilder(SetBagColorPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetBagColorPacket::encode)
                .decoder(SetBagColorPacket::decode)
                .consumerMainThread(SetBagColorPacket::handle)
                .add();
    }
}
