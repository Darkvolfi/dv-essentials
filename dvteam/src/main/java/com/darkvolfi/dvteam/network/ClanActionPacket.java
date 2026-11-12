package com.darkvolfi.dvteam.network;

import com.darkvolfi.dvteam.TeamConfig;
import com.darkvolfi.dvteam.data.BannerData;
import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanVault;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Every clan action from the screens, client → server. The server checks ranks and rules; the client only
 * hides buttons it knows would fail.
 *
 * @param target a player, clan or settlement id, depending on the action
 * @param value  a rank id, or ally perms | public perms << 8
 * @param extra  clan fields for CREATE and EDIT: Name, Tag, Color, Banner, FriendlyFire (each optional for EDIT);
 *               rank fields for RANK_ADD and RANK_EDIT: Color, Manage, Land (the name is {@code text})
 */
public record ClanActionPacket(Action action, UUID target, String text, int value, CompoundTag extra) {
    public enum Action {
        CREATE, EDIT, DISBAND, LEAVE, INVITE, JOIN, DECLINE, KICK, SET_RANK,
        ALLY_REQUEST, ALLY_BREAK, WAR_DECLARE, WAR_PEACE, WAR_SURRENDER,
        RENAME_SETTLEMENT, SET_PERMS, UNCLAIM_ALL, GET_BANNER, VAULT_OPEN,
        RANK_ADD, RANK_EDIT, RANK_UP, RANK_DOWN, RANK_DELETE
    }

    public static ClanActionPacket rank(Action action, int rankId) {
        return new ClanActionPacket(action, NONE, "", rankId, new CompoundTag());
    }

    private static final UUID NONE = new UUID(0L, 0L);

    public static ClanActionPacket of(Action action) {
        return new ClanActionPacket(action, NONE, "", 0, new CompoundTag());
    }

    public static ClanActionPacket target(Action action, UUID target) {
        return new ClanActionPacket(action, target, "", 0, new CompoundTag());
    }

    public static ClanActionPacket fields(Action action, CompoundTag extra) {
        return new ClanActionPacket(action, NONE, "", 0, extra);
    }

    public static void encode(ClanActionPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeUUID(p.target);
        buf.writeUtf(p.text, 64);
        buf.writeVarInt(p.value);
        buf.writeNbt(p.extra);
    }

    public static ClanActionPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID target = buf.readUUID();
        String text = buf.readUtf(64);
        int value = buf.readVarInt();
        CompoundTag extra = buf.readNbt();
        return new ClanActionPacket(action, target, text, value, extra == null ? new CompoundTag() : extra);
    }

    public static void handle(ClanActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        ServerPlayer player = ctx.get().getSender();
        if (player == null || overLimit(player)) return;
        MinecraftServer server = player.getServer();
        ClanData data = ClanData.get(server);
        long revision = data.revision();
        ClanData.Clan clan = data.clanOf(player.getUUID());
        CompoundTag x = p.extra;

        String error = switch (p.action) {
            case CREATE -> data.create(player, x.getString("Name"), x.getString("Tag"), x.getInt("Color"),
                    BannerData.load(x.getCompound("Banner")));
            case EDIT -> data.edit(player,
                    x.contains("Name") ? x.getString("Name") : null,
                    x.contains("Tag") ? x.getString("Tag") : null,
                    x.contains("Color") ? x.getInt("Color") : null,
                    x.contains("Banner") ? BannerData.load(x.getCompound("Banner")) : null,
                    x.contains("FriendlyFire") ? x.getBoolean("FriendlyFire") : null);
            case DISBAND -> {
                if (clan == null || !clan.isLeader(player.getUUID())) yield "dvteam.err.rank";
                broadcast(server, "dvteam.msg.disbanded", clanName(clan));
                data.disband(server, clan);
                yield null;
            }
            case LEAVE -> data.leave(server, player);
            case INVITE -> {
                String e = data.invite(player, p.target);
                ServerPlayer invited = server.getPlayerList().getPlayer(p.target);
                if (e == null && invited != null) {
                    invited.sendSystemMessage(Component.translatable("dvteam.msg.invited_you", clanName(clan))
                            .withStyle(ChatFormatting.GOLD));
                }
                yield e;
            }
            case JOIN -> data.join(player, p.target);
            case DECLINE -> {
                data.decline(player, p.target);
                yield null;
            }
            case KICK -> data.kick(player, p.target);
            case SET_RANK -> data.setRank(player, p.target, p.value);
            case ALLY_REQUEST -> {
                String e = data.requestAlliance(player, p.target);
                ClanData.Clan other = data.clan(p.target);
                if (e == null && clan != null && other != null && data.mutualAllies(clan, other)) {
                    broadcast(server, "dvteam.msg.allied", clanName(clan), clanName(other));
                }
                yield e;
            }
            case ALLY_BREAK -> data.breakAlliance(player, p.target);
            case WAR_DECLARE -> {
                String e = data.declareWar(player, p.target);
                if (e == null) {
                    broadcast(server, "dvteam.msg.war_declared", clanName(clan), clanName(data.clan(p.target)),
                            TeamConfig.WAR_WARMUP_MINUTES.get());
                }
                yield e;
            }
            case WAR_PEACE -> {
                if (data.offerPeace(player, p.target)) {
                    broadcast(server, "dvteam.msg.war_peace", clanName(clan), clanName(data.clan(p.target)));
                }
                yield null;
            }
            case WAR_SURRENDER -> {
                if (data.surrender(player, p.target) != null) {
                    broadcast(server, "dvteam.msg.war_surrender", clanName(clan), clanName(data.clan(p.target)));
                }
                yield null;
            }
            case RENAME_SETTLEMENT -> data.renameSettlement(player, p.target, p.text);
            case SET_PERMS -> data.setPerms(player, p.target, p.value & 0xFF, (p.value >> 8) & 0xFF);
            case UNCLAIM_ALL -> {
                data.unclaimAll(server, player);
                yield null;
            }
            case GET_BANNER -> giveBanner(player, clan);
            case VAULT_OPEN -> ClanVault.open(player);
            case RANK_ADD -> data.addRank(player, p.text, x.getInt("Color"), x.getInt("Manage"), x.getInt("Land"));
            case RANK_EDIT -> data.editRank(player, p.value, p.text, x.getInt("Color"), x.getInt("Manage"), x.getInt("Land"));
            case RANK_UP -> data.moveRank(player, p.value, true);
            case RANK_DOWN -> data.moveRank(player, p.value, false);
            case RANK_DELETE -> data.deleteRank(player, p.value);
        };
        TeamNetwork.tell(player, error);
        // The sync goes to every player, so only send it when the action really changed something.
        if (data.revision() != revision) TeamNetwork.syncToAll(server);
    }

    /** Screens send one packet per click (two for the settlement screen); more than this is a modified client. */
    private static final int MAX_PER_SECOND = 5;
    /** Per player: {tick the current second started, packets in it}. Weak, so logged-out players drop out. */
    private static final Map<ServerPlayer, int[]> RECENT = new WeakHashMap<>();

    /** Packets past the limit are dropped silently. */
    private static boolean overLimit(ServerPlayer player) {
        int now = player.server.getTickCount();
        int[] recent = RECENT.computeIfAbsent(player, p -> new int[] {now, 0});
        if (now - recent[0] >= 20) {
            recent[0] = now;
            recent[1] = 0;
        }
        return ++recent[1] > MAX_PER_SECOND;
    }

    /** Swaps one banner from the inventory for a banner with the clan's flag. */
    private static String giveBanner(ServerPlayer player, ClanData.Clan clan) {
        if (clan == null) return "dvteam.err.no_clan";
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof BannerItem) {
                stack.shrink(1);
                ItemHandlerHelper.giveItemToPlayer(player, clan.banner.toItem());
                return null;
            }
        }
        return "dvteam.err.need_banner";
    }

    public static MutableComponent clanName(ClanData.Clan clan) {
        if (clan == null) return Component.literal("?");
        return Component.literal("[" + clan.tag + "] " + clan.name).withStyle(s -> s.withColor(clan.textColor()));
    }

    private static void broadcast(MinecraftServer server, String key, Object... args) {
        TeamNetwork.broadcast(server, Component.translatable(key, args).withStyle(ChatFormatting.GOLD));
    }
}
