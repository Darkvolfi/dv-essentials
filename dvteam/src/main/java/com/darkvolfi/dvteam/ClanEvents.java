package com.darkvolfi.dvteam;

import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.network.ClanActionPacket;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;

/** Server lifecycle: syncing players, restoring force-loaded chunks, starting and ending wars on time. */
@Mod.EventBusSubscriber(modid = DVTeam.MOD_ID)
public final class ClanEvents {
    private static int ticks;

    private ClanEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ClanData.get(event.getServer()).applyAllForceLoads(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ClanData.get(player.getServer()).rememberName(player);
        TeamNetwork.syncToAll(player.getServer()); // online list changed for everyone
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TeamNetwork.syncToAll(player.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < 20) return;
        ticks = 0;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ClanData data = ClanData.get(server);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (ClanData.War war : new ArrayList<>(data.wars())) {
            Component a = ClanActionPacket.clanName(data.clan(war.attacker));
            Component d = ClanActionPacket.clanName(data.clan(war.defender));
            if (now >= war.endsAt) {
                data.endWar(war);
                announce(server, Component.translatable("dvteam.msg.war_ended", a, d));
                changed = true;
            } else if (!war.announcedStart && now >= war.startsAt) {
                war.announcedStart = true;
                data.setDirty();
                announce(server, Component.translatable("dvteam.msg.war_started", a, d).withStyle(ChatFormatting.RED));
                changed = true;
            }
        }
        if (changed) TeamNetwork.syncToAll(server);
    }

    private static void announce(MinecraftServer server, Component message) {
        TeamNetwork.broadcast(server, message.copy().withStyle(ChatFormatting.GOLD));
    }
}
