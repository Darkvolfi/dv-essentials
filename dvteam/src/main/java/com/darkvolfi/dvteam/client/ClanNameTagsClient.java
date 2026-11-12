package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvteam.ClanNameTags;
import com.darkvolfi.dvteam.DVTeam;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client side of {@link ClanNameTags}: the name tag over a player's head is the client's own display name of
 * that player, so the tag comes from the synced clan state. {@link ClientClanCache#apply} refreshes it.
 */
@Mod.EventBusSubscriber(modid = DVTeam.MOD_ID, value = Dist.CLIENT)
public final class ClanNameTagsClient {
    private ClanNameTagsClient() {}

    @SubscribeEvent
    public static void onClientNameFormat(PlayerEvent.NameFormat event) {
        if (!event.getEntity().level().isClientSide) return;
        MutableComponent prefix = ClanNameTags.prefix(ClientClanCache.clanOf(event.getEntity().getUUID()));
        if (prefix != null) event.setDisplayname(prefix.append(event.getDisplayname()));
    }
}
