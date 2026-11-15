package com.darkvolfi.dvskin.client;

import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvlib.client.DVMods;
import com.darkvolfi.dvskin.DVSkin;
import com.darkvolfi.dvskin.SkinData;
import com.darkvolfi.dvskin.SkinServerConfig;
import com.darkvolfi.dvskin.network.ResultPacket;
import com.darkvolfi.dvskin.network.SkinNetwork;
import com.darkvolfi.dvskin.network.UploadPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/** The key, showing the player's own choice, and sending it to a server that has DV Skin. */
@Mod.EventBusSubscriber(modid = DVSkin.MOD_ID, value = Dist.CLIENT)
public final class SkinClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.dvskin.open",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, DVLib.KEY_CATEGORY);
    /** Uploads are spaced at least this far apart (the server refuses faster ones). */
    private static final long SEND_INTERVAL_MS = 2_000;

    private static long lastSend;
    private static boolean sendPending;
    /** Set on joining: the own look is shown (and sent) on the next tick, once the player and channels are ready. */
    private static boolean applyPending;

    private SkinClient() {}

    public static void init() {
        // Only the server switches show as settings; the choice itself is made on the DV Skin screen.
        DVConfigScreen.register(DVSkin.MOD_ID, null, SkinServerConfig.SPEC, null);
        DVMods.registerScreen(DVSkin.MOD_ID, SkinScreen::new);
    }

    /** True when connected to a server that has DV Skin (single player always has it). */
    public static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && SkinNetwork.present(connection.getConnection());
    }

    /** Shows the saved choice on the own player at once, and sends it to the server when it has DV Skin. */
    public static void applyOwn() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        SkinData data = SkinChoice.load();
        SkinTextures.set(mc.player.getUUID(), data.isEmpty() ? null : data);
        if (serverHasMod()) sendPending = true;
    }

    private static void send() {
        sendPending = false;
        lastSend = System.currentTimeMillis();
        byte[] bytes = SkinChoice.load().toBytes();
        int count = Math.max(1, (bytes.length + SkinData.CHUNK - 1) / SkinData.CHUNK);
        for (int i = 0; i < count; i++) {
            int from = i * SkinData.CHUNK;
            byte[] chunk = Arrays.copyOfRange(bytes, from, Math.min(bytes.length, from + SkinData.CHUNK));
            SkinNetwork.CHANNEL.sendToServer(new UploadPacket(i, count, chunk));
        }
    }

    /** The server's answer to an upload: shown on the DV Skin screen, or above the hotbar. */
    public static void onResult(int code) {
        if (code == ResultPacket.OK) return;
        String key = switch (code) {
            case ResultPacket.NO_SKINS -> "dvskin.result.no_skins";
            case ResultPacket.NO_CAPES -> "dvskin.result.no_capes";
            case ResultPacket.NOTHING_ALLOWED -> "dvskin.result.nothing";
            case ResultPacket.TOO_FAST -> "dvskin.result.too_fast";
            default -> "dvskin.result.invalid";
        };
        Minecraft mc = Minecraft.getInstance();
        if (code == ResultPacket.TOO_FAST) sendPending = true; // try again once the wait is over
        if (mc.screen instanceof SkinScreen screen) screen.showError(Component.translatable(key));
        else mc.gui.setOverlayMessage(Component.translatable(key), false);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new SkinScreen(null));
        }
        if (applyPending && mc.player != null) {
            applyPending = false;
            applyOwn();
        }
        SkinTextures.tick();
        if (sendPending && System.currentTimeMillis() - lastSend >= SEND_INTERVAL_MS && serverHasMod()) send();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        lastSend = 0;
        applyPending = true;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        sendPending = false;
        applyPending = false;
        SkinTextures.clear();
    }

    @Mod.EventBusSubscriber(modid = DVSkin.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterSkinKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_KEY);
        }
    }
}
