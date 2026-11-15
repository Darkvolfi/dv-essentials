package com.darkvolfi.dvskin;

import com.darkvolfi.dvskin.network.ResultPacket;
import com.darkvolfi.dvskin.network.SkinNetwork;
import com.darkvolfi.dvskin.network.SkinPacket;
import com.darkvolfi.dvskin.network.UploadPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Collects each player's chunks, checks the pictures and passes the look on to everyone with DV Skin.
 * The server only ever handles bytes the players sent; it never connects anywhere itself.
 */
@Mod.EventBusSubscriber(modid = DVSkin.MOD_ID)
public final class SkinServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** An upload whose chunks don't all arrive within this is dropped. */
    private static final long UPLOAD_TIMEOUT_MS = 10_000;
    /** At most one new upload per player this often (the client waits on its own, so this only stops floods). */
    private static final long MIN_INTERVAL_MS = 1_500;

    private static final class Upload {
        final int count;
        final long started;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int next;

        Upload(int count, long started) {
            this.count = count;
            this.started = started;
        }
    }

    /** Checked looks of the players online, as sent to clients. */
    private static final Map<UUID, byte[]> LOOKS = new HashMap<>();
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final Map<UUID, Long> LAST_UPLOAD = new HashMap<>();

    private SkinServer() {}

    public static void receive(ServerPlayer player, UploadPacket packet) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Upload upload = UPLOADS.get(id);
        if (packet.index() == 0) {
            Long last = LAST_UPLOAD.get(id);
            if (last != null && now - last < MIN_INTERVAL_MS) {
                UPLOADS.remove(id);
                reply(player, ResultPacket.TOO_FAST);
                return;
            }
            if (packet.count() < 1 || packet.count() > SkinData.MAX_CHUNKS) {
                UPLOADS.remove(id);
                reply(player, ResultPacket.INVALID);
                return;
            }
            LAST_UPLOAD.put(id, now);
            upload = new Upload(packet.count(), now);
            UPLOADS.put(id, upload);
        }
        // A chunk out of order, from another upload, or too late: drop the whole upload
        if (upload == null || packet.index() != upload.next || packet.count() != upload.count
                || now - upload.started > UPLOAD_TIMEOUT_MS
                || upload.bytes.size() + packet.data().length > SkinData.MAX_BYTES) {
            if (upload != null) {
                UPLOADS.remove(id);
                reply(player, ResultPacket.INVALID);
            }
            return;
        }
        upload.bytes.writeBytes(packet.data());
        upload.next++;
        if (upload.next < upload.count) return;

        UPLOADS.remove(id);
        accept(player, upload.bytes.toByteArray());
    }

    private static void accept(ServerPlayer player, byte[] bytes) {
        SkinData data;
        try {
            data = SkinData.fromBytes(bytes);
        } catch (IOException e) {
            reply(player, ResultPacket.INVALID);
            return;
        }
        for (int slot = 0; slot < 3; slot++) {
            byte[] png = data.get(slot);
            if (png != null && !SkinCheck.isValid(png, slot)) {
                LOGGER.info("DV Skin: rejected a picture from {}", player.getGameProfile().getName());
                reply(player, ResultPacket.INVALID);
                return;
            }
        }

        boolean skins = SkinServerConfig.ALLOW_SKINS.get();
        boolean capes = SkinServerConfig.ALLOW_CAPES.get();
        SkinData shared = new SkinData(data.slim(), skins ? data.skin() : null,
                capes ? data.cape() : null, capes ? data.elytra() : null);
        byte[] out = shared.isEmpty() ? new byte[0] : shared.toBytes();
        if (out.length == 0) LOOKS.remove(player.getUUID());
        else LOOKS.put(player.getUUID(), out);
        broadcast(player, new SkinPacket(player.getUUID(), out));

        boolean droppedSkin = data.skin() != null && !skins;
        boolean droppedCape = (data.cape() != null || data.elytra() != null) && !capes;
        reply(player, droppedSkin && droppedCape ? ResultPacket.NOTHING_ALLOWED
                : droppedSkin ? ResultPacket.NO_SKINS : droppedCape ? ResultPacket.NO_CAPES : ResultPacket.OK);
    }

    private static void broadcast(ServerPlayer from, SkinPacket packet) {
        for (ServerPlayer p : from.server.getPlayerList().getPlayers()) {
            if (SkinNetwork.present(p.connection.connection)) {
                SkinNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            }
        }
    }

    private static void reply(ServerPlayer player, int code) {
        if (SkinNetwork.present(player.connection.connection)) {
            SkinNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ResultPacket(code));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SkinNetwork.present(player.connection.connection)) return;
        // Everyone already here, so the newcomer sees them too
        for (Map.Entry<UUID, byte[]> look : LOOKS.entrySet()) {
            SkinNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SkinPacket(look.getKey(), look.getValue()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        UPLOADS.remove(id);
        LAST_UPLOAD.remove(id);
        if (LOOKS.remove(id) != null) broadcast(player, new SkinPacket(id, new byte[0]));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOOKS.clear();
        UPLOADS.clear();
        LAST_UPLOAD.clear();
    }
}
