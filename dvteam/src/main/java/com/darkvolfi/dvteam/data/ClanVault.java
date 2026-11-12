package com.darkvolfi.dvteam.data;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/**
 * A clan's shared chest, kept in the clan data instead of a block. It opens only on the clan's own land,
 * for the leader and ranks with the vault right, and closes when that stops being true.
 */
public final class ClanVault extends SimpleContainer {
    public static final int SIZE = 27;
    private final UUID clanId;

    ClanVault(UUID clanId) {
        super(SIZE);
        this.clanId = clanId;
    }

    /** Opens the vault for the player, or returns why not (a translation key). */
    public static String open(ServerPlayer player) {
        ClanData data = ClanData.get(player.getServer());
        ClanData.Clan clan = data.clanOf(player.getUUID());
        if (clan == null) return "dvteam.err.no_clan";
        String error = denied(data, clan, player);
        if (error != null) return error;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, clan.vault),
                Component.translatable("dvteam.vault.title", clan.name)));
        return null;
    }

    private static String denied(ClanData data, ClanData.Clan clan, Player player) {
        if (!clan.can(player.getUUID(), ClanTypes.Manage.VAULT)) return "dvteam.err.vault_rank";
        ChunkPos cp = player.chunkPosition();
        if (data.clanAt(player.level().dimension().location().toString(), cp.x, cp.z) != clan) return "dvteam.err.vault_land";
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer sp)) return true;
        ClanData data = ClanData.get(sp.getServer());
        ClanData.Clan clan = data.clanOf(player.getUUID());
        return clan != null && clan.id.equals(clanId) && denied(data, clan, player) == null;
    }
}
