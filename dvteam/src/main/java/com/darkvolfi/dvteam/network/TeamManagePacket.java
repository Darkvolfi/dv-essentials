package com.darkvolfi.dvteam.network;

import com.darkvolfi.dvteam.data.TeamSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class TeamManagePacket {
    public enum Action {
        RENAME,
        CYCLE_COLOR,
        TOGGLE_FRIENDLY_FIRE,
        TOGGLE_ALLY_INTERACT,
        TOGGLE_ALLY_EDIT,
        TOGGLE_PUBLIC_INTERACT,
        INVITE_PLAYER,
        ACCEPT_INVITE,
        LEAVE_TEAM,
        KICK_MEMBER,
        TOGGLE_ALLY_TEAM,
        UNCLAIM_ALL
    }

    private final Action action;
    private final String textArg;
    private final UUID targetId;

    public TeamManagePacket(Action action, String textArg, UUID targetId) {
        this.action = action;
        this.textArg = textArg == null ? "" : textArg;
        this.targetId = targetId == null ? new UUID(0L, 0L) : targetId;
    }

    public static TeamManagePacket simple(Action action) {
        return new TeamManagePacket(action, "", new UUID(0L, 0L));
    }

    public static TeamManagePacket withText(Action action, String text) {
        return new TeamManagePacket(action, text, new UUID(0L, 0L));
    }

    public static TeamManagePacket withTarget(Action action, UUID targetId) {
        return new TeamManagePacket(action, "", targetId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(textArg, 48);
        buf.writeUUID(targetId);
    }

    public static TeamManagePacket decode(FriendlyByteBuf buf) {
        return new TeamManagePacket(buf.readEnum(Action.class), buf.readUtf(48), buf.readUUID());
    }

    public static void handle(TeamManagePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            TeamSavedData data = TeamSavedData.get(server);
            TeamSavedData.TeamRecord team = data.ensurePlayerTeam(player);
            boolean isOwner = team.owner.equals(player.getUUID());

            switch (pkt.action) {
                case RENAME -> {
                    String trimmed = pkt.textArg.trim();
                    if (!trimmed.isEmpty() && trimmed.length() <= 32) {
                        team.name = trimmed;
                        data.setDirty();
                    }
                }
                case CYCLE_COLOR -> {
                    int idx = 0;
                    for (int i = 0; i < TeamSavedData.TEAM_PALETTE.length; i++) {
                        if (TeamSavedData.TEAM_PALETTE[i] == (team.color & 0xFFFFFF)) {
                            idx = (i + 1) % TeamSavedData.TEAM_PALETTE.length;
                            break;
                        }
                    }
                    team.color = TeamSavedData.TEAM_PALETTE[idx];
                    data.setDirty();
                }
                case TOGGLE_FRIENDLY_FIRE -> {
                    team.friendlyFire = !team.friendlyFire;
                    data.setDirty();
                }
                case TOGGLE_ALLY_INTERACT -> {
                    team.allowAllyInteract = !team.allowAllyInteract;
                    data.setDirty();
                }
                case TOGGLE_ALLY_EDIT -> {
                    team.allowAllyEdit = !team.allowAllyEdit;
                    data.setDirty();
                }
                case TOGGLE_PUBLIC_INTERACT -> {
                    team.publicInteract = !team.publicInteract;
                    data.setDirty();
                }
                case INVITE_PLAYER -> {
                    if (!team.members.contains(pkt.targetId)) {
                        team.invited.add(pkt.targetId);
                        data.setDirty();
                        ServerPlayer target = server.getPlayerList().getPlayer(pkt.targetId);
                        if (target != null) {
                            target.sendSystemMessage(Component.translatable("dvteam.msg.invited_you", team.name)
                                    .withStyle(ChatFormatting.GOLD));
                        }
                    }
                }
                case ACCEPT_INVITE -> data.joinTeam(server, player, pkt.targetId);
                case LEAVE_TEAM -> data.leaveTeam(server, player);
                case KICK_MEMBER -> {
                    if (isOwner) data.kickMember(server, player, pkt.targetId);
                }
                case TOGGLE_ALLY_TEAM -> {
                    if (!team.id.equals(pkt.targetId) && data.getTeam(pkt.targetId) != null) {
                        if (!team.allies.remove(pkt.targetId)) {
                            team.allies.add(pkt.targetId);
                        }
                        data.setDirty();
                    }
                }
                case UNCLAIM_ALL -> data.unclaimAllForTeam(server, team.id);
            }
            TeamNetwork.syncToAll(server);
        });
        ctx.get().setPacketHandled(true);
    }
}
