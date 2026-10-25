package com.darkvolfi.dvfood.network;

import com.darkvolfi.dvfood.DVFood;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vanilla only sends saturation when hunger changes and never sends exhaustion,
 * so the server pushes both whenever they change. Optional on either side.
 */
@Mod.EventBusSubscriber(modid = DVFood.MOD_ID)
public final class FoodNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DVFood.MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private static final Map<UUID, float[]> lastSent = new HashMap<>();

    private FoodNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(FoodSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FoodSyncPacket::encode)
                .decoder(FoodSyncPacket::decode)
                .consumerMainThread(FoodSyncPacket::handle)
                .add();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!CHANNEL.isRemotePresent(player.connection.connection)) return;

        FoodData food = player.getFoodData();
        float saturation = food.getSaturationLevel();
        float exhaustion = food.getExhaustionLevel();
        float[] last = lastSent.get(player.getUUID());
        if (last != null && last[0] == saturation && last[1] == exhaustion) return;

        lastSent.put(player.getUUID(), new float[]{saturation, exhaustion});
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FoodSyncPacket(saturation, exhaustion));
    }

    // The client gets a fresh FoodData in these cases, so force a resend.
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lastSent.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        lastSent.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        lastSent.remove(event.getEntity().getUUID());
    }
}
