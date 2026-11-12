package com.darkvolfi.dvteam;

import com.darkvolfi.dvteam.data.ClanData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "[TAG] Name" with the tag in the clan colour, in the tab list and in the server's display name (chat, death
 * messages). Name tags over heads are drawn by the client; {@code ClanNameTagsClient} does the same there.
 * Clan chat writes the tag itself next to the plain profile name, so it is not doubled.
 */
@Mod.EventBusSubscriber(modid = DVTeam.MOD_ID)
public final class ClanNameTags {
    /** Per online player, the tag and colour last shown, so names are only refreshed when they change. */
    private static final Map<UUID, String> SHOWN = new HashMap<>();

    private ClanNameTags() {}

    /** The tag prefix for a clan, or null without one. Shared with the client. */
    public static MutableComponent prefix(ClanData.Clan clan) {
        if (clan == null) return null;
        return Component.literal("[" + clan.tag + "] ").withStyle(s -> s.withColor(clan.textColor()));
    }

    private static ClanData.Clan clanOf(ServerPlayer player) {
        return player.getServer() == null ? null : ClanData.get(player.getServer()).clanOf(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MutableComponent prefix = prefix(clanOf(player));
        if (prefix != null) event.setDisplayname(prefix.append(event.getDisplayname()));
    }

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MutableComponent prefix = prefix(clanOf(player));
        if (prefix == null) return;
        Component name = event.getDisplayName() != null ? event.getDisplayName() : Component.literal(player.getGameProfile().getName());
        event.setDisplayName(prefix.append(name));
    }

    /**
     * Refreshes the names of online players whose clan tag or colour changed (founded, joined, left, kicked,
     * disbanded, edited). Called with every clan sync, which follows every clan change.
     */
    public static void refreshChanged(MinecraftServer server) {
        ClanData data = ClanData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ClanData.Clan clan = data.clanOf(p.getUUID());
            String shown = clan == null ? "" : clan.tag + "|" + clan.textColor();
            if (!shown.equals(SHOWN.put(p.getUUID(), shown))) {
                p.refreshDisplayName();
                p.refreshTabListName();
            }
        }
    }

    @SubscribeEvent
    public static void onNameTagsLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SHOWN.remove(event.getEntity().getUUID());
    }
}
