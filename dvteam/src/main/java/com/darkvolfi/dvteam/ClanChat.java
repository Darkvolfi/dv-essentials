package com.darkvolfi.dvteam;

import com.darkvolfi.dvteam.data.ClanData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Clan chat (members only) and ally chat (members and allied clans). With /clan chat clan|ally, everything the
 * player types in normal chat goes to that channel until they switch back with /clan chat all.
 */
@Mod.EventBusSubscriber(modid = DVTeam.MOD_ID)
public final class ClanChat {
    public enum Channel { ALL, CLAN, ALLY }

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    private ClanChat() {}

    public static void setChannel(ServerPlayer player, Channel channel) {
        if (channel == Channel.ALL) CHANNELS.remove(player.getUUID());
        else CHANNELS.put(player.getUUID(), channel);
    }

    /** Sends to the channel; returns false when the player has no clan. */
    public static boolean send(ServerPlayer player, Channel channel, String message) {
        MinecraftServer server = player.getServer();
        ClanData data = ClanData.get(server);
        ClanData.Clan clan = data.clanOf(player.getUUID());
        if (clan == null) return false;
        Set<UUID> to = new LinkedHashSet<>(clan.members.keySet());
        if (channel == Channel.ALLY) {
            for (UUID allyId : clan.allies) {
                ClanData.Clan ally = data.clan(allyId);
                if (ally != null && data.mutualAllies(clan, ally)) to.addAll(ally.members.keySet());
            }
        }
        Component line = Component.translatable(channel == Channel.ALLY ? "dvteam.chat.ally" : "dvteam.chat.clan")
                .withStyle(channel == Channel.ALLY ? ChatFormatting.AQUA : ChatFormatting.GREEN)
                .append(Component.literal(" [" + clan.tag + "] ").withStyle(s -> s.withColor(clan.color)))
                .append(Component.literal(player.getGameProfile().getName() + ": ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(message).withStyle(ChatFormatting.GRAY));
        for (UUID id : to) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(line);
        }
        return true;
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        Channel channel = CHANNELS.get(event.getPlayer().getUUID());
        if (channel == null) return;
        if (send(event.getPlayer(), channel, event.getRawText())) {
            event.setCanceled(true);
        } else {
            CHANNELS.remove(event.getPlayer().getUUID()); // left the clan: back to normal chat
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHANNELS.remove(event.getEntity().getUUID());
    }
}
