package com.darkvolfi.dvlens.network;

import com.darkvolfi.dvlens.DVLens;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Things the client can't know on its own (an animal's exact age, an offline pet owner's name, mob effects,
 * container contents, furnace progress...) are asked from the server for the entity or block being looked at. Optional on either side: without it on the server the
 * lens just shows less.
 */
public final class LensNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DVLens.MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private LensNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(EntityInfoRequest.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EntityInfoRequest::encode)
                .decoder(EntityInfoRequest::decode)
                .consumerMainThread(EntityInfoRequest::handle)
                .add();
        CHANNEL.messageBuilder(EntityInfoReply.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EntityInfoReply::encode)
                .decoder(EntityInfoReply::decode)
                .consumerMainThread(EntityInfoReply::handle)
                .add();
        CHANNEL.messageBuilder(BlockInfoRequest.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BlockInfoRequest::encode)
                .decoder(BlockInfoRequest::decode)
                .consumerMainThread(BlockInfoRequest::handle)
                .add();
        CHANNEL.messageBuilder(BlockInfoReply.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlockInfoReply::encode)
                .decoder(BlockInfoReply::decode)
                .consumerMainThread(BlockInfoReply::handle)
                .add();
    }
}
