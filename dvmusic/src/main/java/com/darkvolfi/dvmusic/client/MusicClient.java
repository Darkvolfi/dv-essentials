package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvmusic.DVMusic;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Keys, the music folder as a resource pack, and reading the song lists when resources load. */
@Mod.EventBusSubscriber(modid = DVMusic.MOD_ID, value = Dist.CLIENT)
public final class MusicClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.dvmusic.open",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, DVLib.KEY_CATEGORY);
    public static final KeyMapping NEXT_KEY = new KeyMapping("key.dvmusic.next",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, DVLib.KEY_CATEGORY);
    public static final KeyMapping PAUSE_KEY = new KeyMapping("key.dvmusic.pause",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, DVLib.KEY_CATEGORY);

    private MusicClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new MusicScreen(null));
        }
        while (NEXT_KEY.consumeClick()) MusicPlayer.next();
        while (PAUSE_KEY.consumeClick()) MusicPlayer.togglePause();
    }

    @Mod.EventBusSubscriber(modid = DVMusic.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_KEY);
            event.register(NEXT_KEY);
            event.register(PAUSE_KEY);
        }

        /** The dvmusic folder as an always-on resource pack, so its .ogg files can play as sounds. */
        @SubscribeEvent
        public static void onAddPackFinders(AddPackFindersEvent event) {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
            event.addRepositorySource(consumer -> {
                Pack pack = Pack.readMetaAndCreate(CustomMusicPack.ID, Component.translatable("dvmusic.pack"), true,
                        id -> new CustomMusicPack(), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
                if (pack != null) consumer.accept(pack);
            });
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> TrackLibrary.reload());
        }
    }
}
