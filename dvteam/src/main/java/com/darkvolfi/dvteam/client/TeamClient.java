package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvmap.client.MapOverlayHook;
import com.darkvolfi.dvteam.DVTeam;
import com.darkvolfi.dvteam.TeamConfig;
import com.darkvolfi.dvteam.data.ClanData;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = DVTeam.MOD_ID, value = Dist.CLIENT)
public final class TeamClient {
    public static final KeyMapping OPEN_TEAM_KEY = new KeyMapping(
            "key.dvteam.open_team",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            DVLib.KEY_CATEGORY
    );

    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;
    private static UUID lastTerritoryTeamId;
    private static boolean initializedTerritory;

    private TeamClient() {}

    public static void init() {
        MapOverlayHook.register(new TeamMapIntegration());
    }

    @Mod.EventBusSubscriber(modid = DVTeam.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_TEAM_KEY);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        while (OPEN_TEAM_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new ClanScreen(null));
            }
        }

        if (TeamConfig.SHOW_TERRITORY_BANNER.get()) {
            ChunkPos cp = mc.player.chunkPosition();
            if (cp.x != lastChunkX || cp.z != lastChunkZ) {
                lastChunkX = cp.x;
                lastChunkZ = cp.z;
                ResourceKey<Level> dim = mc.level.dimension();
                ClanData.Settlement settlement = ClientClanCache.settlementAt(dim, cp.x, cp.z);
                UUID currentId = settlement != null ? settlement.id : null;

                if (initializedTerritory && !Objects.equals(currentId, lastTerritoryTeamId)) {
                    if (settlement == null) {
                        mc.player.displayClientMessage(
                                Component.translatable("dvteam.msg.entering_wilderness").withStyle(ChatFormatting.GRAY),
                                true
                        );
                    } else {
                        ClanData.Clan clan = ClientClanCache.clan(settlement.clanId);
                        if (clan != null) {
                            mc.player.displayClientMessage(
                                    Component.translatable("dvteam.msg.entering_territory",
                                            Component.literal(settlement.name).withStyle(s -> s.withColor(clan.color)),
                                            Component.translatable(settlement.type.translationKey()),
                                            Component.literal("[" + clan.tag + "] " + clan.name).withStyle(s -> s.withColor(clan.color))),
                                    true
                            );
                        }
                    }
                }
                lastTerritoryTeamId = currentId;
                initializedTerritory = true;
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientClanCache.clear();
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
        lastTerritoryTeamId = null;
        initializedTerritory = false;
    }
}
