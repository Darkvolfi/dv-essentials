package com.darkvolfi.dvslots.network;

import com.darkvolfi.dvslots.DVSlots;
import com.darkvolfi.dvslots.slot.SlotData;
import com.darkvolfi.dvslots.slot.SlotType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;

public final class SlotsNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DVSlots.MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private SlotsNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(SyncSlotsPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncSlotsPacket::encode)
                .decoder(SyncSlotsPacket::decode)
                .consumerMainThread(SyncSlotsPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenSlotsPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenSlotsPacket::encode)
                .decoder(OpenSlotsPacket::decode)
                .consumerMainThread(OpenSlotsPacket::handle)
                .add();
    }

    public static void syncToTrackingAndSelf(ServerPlayer player) {
        syncTo(player, player);
        for (ServerPlayer viewer : player.serverLevel().players()) {
            if (viewer != player && viewer.distanceToSqr(player) < 128 * 128) syncTo(viewer, player);
        }
    }

    /** Only clients that have the mod get the packet. */
    public static void syncTo(ServerPlayer viewer, Player target) {
        if (!CHANNEL.isRemotePresent(viewer.connection.connection)) return;
        SlotData data = SlotData.get(target);
        if (data == null) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (SlotType type : SlotType.values()) stacks.add(data.get(type));
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new SyncSlotsPacket(target.getId(), stacks));
    }
}
