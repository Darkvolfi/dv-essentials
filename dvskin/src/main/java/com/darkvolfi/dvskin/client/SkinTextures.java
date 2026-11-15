package com.darkvolfi.dvskin.client;

import com.darkvolfi.dvskin.DVSkin;
import com.darkvolfi.dvskin.SkinData;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The looks in use on this client, per player, put straight into each player's {@link PlayerInfo}: its texture map
 * (skin, cape, elytra) and arm model. No mixin: the game may still write the Mojang skin over them when it finishes
 * downloading, so every tick the chosen ones are checked and put back if needed.
 */
public final class SkinTextures {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MinecraftProfileTexture.Type[] TYPES = {
            MinecraftProfileTexture.Type.SKIN, MinecraftProfileTexture.Type.CAPE, MinecraftProfileTexture.Type.ELYTRA};

    // PlayerInfo fields by SRG name (checked against the 1.20.1 mappings)
    private static final Field TEXTURES = field("f_105299_");     // textureLocations
    private static final Field PENDING = field("f_105302_");      // pendingTextures (true once requested)
    private static final Field MODEL = field("f_105303_");        // skinModel

    /** One player's look: texture per slot (null: not chosen) and arm model. */
    private record Look(ResourceLocation[] textures, boolean slim) {
        ResourceLocation get(int slot) {
            return textures[slot];
        }
    }

    private static final Map<UUID, Look> LOOKS = new HashMap<>();
    private static int counter;

    private SkinTextures() {}

    private static Field field(String srg) {
        try {
            return ObfuscationReflectionHelper.findField(PlayerInfo.class, srg);
        } catch (RuntimeException e) {
            LOGGER.error("DV Skin: PlayerInfo field {} not found; looks can't be shown", srg, e);
            return null;
        }
    }

    private static boolean usable() {
        return TEXTURES != null && PENDING != null && MODEL != null;
    }

    /** A look from the server; empty data clears it. */
    public static void receive(UUID player, byte[] data) {
        if (data.length == 0) {
            set(player, null);
            return;
        }
        try {
            set(player, SkinData.fromBytes(data));
        } catch (IOException e) {
            LOGGER.warn("DV Skin: unreadable look for {}", player);
        }
    }

    /** Uses {@code data} for the player from now on (null: back to their own skin and cape). */
    public static void set(UUID player, SkinData data) {
        if (!usable()) return;
        Minecraft mc = Minecraft.getInstance();
        Look look = null;
        if (data != null && !data.isEmpty()) {
            ResourceLocation[] textures = new ResourceLocation[3];
            for (int slot = 0; slot < 3; slot++) {
                byte[] png = data.get(slot);
                if (png == null) continue;
                try {
                    NativeImage image = SkinImages.read(png, slot);
                    ResourceLocation id = new ResourceLocation(DVSkin.MOD_ID, String.format(Locale.ROOT, "look/%s_%d_%d",
                            player.toString().replace("-", ""), slot, counter++));
                    mc.getTextureManager().register(id, new DynamicTexture(image));
                    textures[slot] = id;
                } catch (SkinException e) {
                    LOGGER.warn("DV Skin: skipped a picture for {} ({})", player, e.key);
                }
            }
            look = new Look(textures, data.slim());
        }

        Look old = look == null ? LOOKS.remove(player) : LOOKS.put(player, look);
        PlayerInfo info = info(player);
        if (old != null) {
            if (info != null) restore(info, old, look);
            for (ResourceLocation id : old.textures()) {
                if (id != null) mc.getTextureManager().release(id);
            }
        }
        if (look != null && info != null) apply(info, look);
    }

    /** True when this client shows a chosen skin for the player (so their PlayerInfo skin isn't their own). */
    public static boolean has(UUID player, int slot) {
        Look look = LOOKS.get(player);
        return look != null && look.get(slot) != null;
    }

    public static void tick() {
        if (LOOKS.isEmpty() || !usable()) return;
        for (Map.Entry<UUID, Look> entry : LOOKS.entrySet()) {
            PlayerInfo info = info(entry.getKey());
            if (info != null) apply(info, entry.getValue());
        }
    }

    /** Forgets every look (leaving a world). */
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Look look : LOOKS.values()) {
            for (ResourceLocation id : look.textures()) {
                if (id != null) mc.getTextureManager().release(id);
            }
        }
        LOOKS.clear();
    }

    private static PlayerInfo info(UUID player) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.getPlayerInfo(player);
    }

    @SuppressWarnings("unchecked")
    private static Map<MinecraftProfileTexture.Type, ResourceLocation> textures(PlayerInfo info) throws IllegalAccessException {
        return (Map<MinecraftProfileTexture.Type, ResourceLocation>) TEXTURES.get(info);
    }

    /** Puts the look's textures in place; cheap when they already are. */
    private static void apply(PlayerInfo info, Look look) {
        try {
            Map<MinecraftProfileTexture.Type, ResourceLocation> map = textures(info);
            for (int slot = 0; slot < 3; slot++) {
                ResourceLocation id = look.get(slot);
                if (id != null && !id.equals(map.get(TYPES[slot]))) map.put(TYPES[slot], id);
            }
            if (look.get(SkinData.SKIN) != null) {
                String model = look.slim() ? "slim" : "default";
                if (!model.equals(MODEL.get(info))) MODEL.set(info, model);
            }
        } catch (IllegalAccessException e) {
            LOGGER.error("DV Skin: can't set a player's textures", e);
        }
    }

    /**
     * Takes back what {@code old} set and {@code now} doesn't, and lets the game fetch the player's own textures
     * again, so a real Mojang skin or cape comes back.
     */
    private static void restore(PlayerInfo info, Look old, Look now) {
        try {
            Map<MinecraftProfileTexture.Type, ResourceLocation> map = textures(info);
            boolean reload = false;
            for (int slot = 0; slot < 3; slot++) {
                ResourceLocation was = old.get(slot);
                if (was == null || now != null && now.get(slot) != null) continue;
                if (was.equals(map.get(TYPES[slot]))) map.remove(TYPES[slot]);
                if (slot == SkinData.SKIN) MODEL.set(info, null);
                reload = true;
            }
            // false = "not requested yet": the next texture lookup asks the skin manager again
            if (reload) PENDING.set(info, false);
        } catch (IllegalAccessException e) {
            LOGGER.error("DV Skin: can't restore a player's textures", e);
        }
    }
}
