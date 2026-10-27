package com.darkvolfi.dvteam.command;

import com.darkvolfi.dvteam.DVTeam;
import com.darkvolfi.dvteam.data.TeamSavedData;
import com.darkvolfi.dvteam.network.TeamNetwork;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = DVTeam.MOD_ID)
public final class TeamCommands {
    private TeamCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(buildTree("dvteam"));
        event.getDispatcher().register(buildTree("dvt"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTree(String root) {
        return Commands.literal(root)
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    MinecraftServer server = player.getServer();
                    TeamSavedData data = TeamSavedData.get(server);
                    TeamSavedData.TeamRecord team = data.ensurePlayerTeam(player);
                    int claims = data.countClaims(team.id);
                    int maxClaims = data.maxClaimsFor(team);
                    int forced = data.countForceLoaded(team.id);
                    int maxForced = data.maxForceLoadedFor(team);
                    player.sendSystemMessage(Component.translatable("dvteam.cmd.info",
                            team.name, team.members.size(), claims, maxClaims, forced, maxForced)
                            .withStyle(ChatFormatting.GOLD));
                    return 1;
                }))
                .then(Commands.literal("name")
                        .then(Commands.argument("teamName", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String newName = StringArgumentType.getString(ctx, "teamName").trim();
                            if (newName.isEmpty() || newName.length() > 32) return 0;
                            TeamSavedData data = TeamSavedData.get(player.getServer());
                            TeamSavedData.TeamRecord team = data.ensurePlayerTeam(player);
                            team.name = newName;
                            data.setDirty();
                            TeamNetwork.syncToAll(player.getServer());
                            player.sendSystemMessage(Component.translatable("dvteam.cmd.renamed", newName).withStyle(ChatFormatting.GREEN));
                            return 1;
                        })))
                .then(Commands.literal("invite")
                        .then(Commands.argument("target", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            TeamSavedData data = TeamSavedData.get(player.getServer());
                            TeamSavedData.TeamRecord team = data.ensurePlayerTeam(player);
                            team.invited.add(target.getUUID());
                            data.setDirty();
                            TeamNetwork.syncToAll(player.getServer());
                            player.sendSystemMessage(Component.translatable("dvteam.msg.invited_sent", target.getGameProfile().getName())
                                    .withStyle(ChatFormatting.GREEN));
                            target.sendSystemMessage(Component.translatable("dvteam.msg.invited_you", team.name)
                                    .withStyle(ChatFormatting.GOLD));
                            return 1;
                        })))
                .then(Commands.literal("join")
                        .then(Commands.argument("teamName", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String query = StringArgumentType.getString(ctx, "teamName").trim();
                            TeamSavedData data = TeamSavedData.get(player.getServer());
                            for (TeamSavedData.TeamRecord tr : data.getTeams()) {
                                if (tr.invited.contains(player.getUUID()) && tr.name.equalsIgnoreCase(query)) {
                                    data.joinTeam(player.getServer(), player, tr.id);
                                    TeamNetwork.syncToAll(player.getServer());
                                    player.sendSystemMessage(Component.translatable("dvteam.cmd.joined", tr.name)
                                            .withStyle(ChatFormatting.GREEN));
                                    return 1;
                                }
                            }
                            player.sendSystemMessage(Component.translatable("dvteam.cmd.no_invite", query).withStyle(ChatFormatting.RED));
                            return 0;
                        })))
                .then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    TeamSavedData data = TeamSavedData.get(player.getServer());
                    data.leaveTeam(player.getServer(), player);
                    TeamNetwork.syncToAll(player.getServer());
                    player.sendSystemMessage(Component.translatable("dvteam.cmd.left").withStyle(ChatFormatting.YELLOW));
                    return 1;
                }))
                .then(Commands.literal("msg")
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String msg = StringArgumentType.getString(ctx, "message");
                            MinecraftServer server = player.getServer();
                            TeamSavedData data = TeamSavedData.get(server);
                            TeamSavedData.TeamRecord team = data.ensurePlayerTeam(player);
                            Component line = Component.literal("[" + team.name + "] ")
                                    .withStyle(s -> s.withColor(team.color))
                                    .append(Component.literal(player.getGameProfile().getName() + ": ").withStyle(ChatFormatting.WHITE))
                                    .append(Component.literal(msg).withStyle(ChatFormatting.GRAY));
                            for (UUID memberId : team.members) {
                                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                                if (member != null) {
                                    member.sendSystemMessage(line);
                                }
                            }
                            return 1;
                        })))
                .then(Commands.literal("claim").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ChunkPos cp = player.chunkPosition();
                    String dim = player.level().dimension().location().toString();
                    TeamSavedData data = TeamSavedData.get(player.getServer());
                    if (data.claimChunk(player.getServer(), player, dim, cp.x, cp.z, false)) {
                        TeamNetwork.syncToAll(player.getServer());
                        player.sendSystemMessage(Component.translatable("dvteam.cmd.claimed", cp.x, cp.z).withStyle(ChatFormatting.GREEN));
                        return 1;
                    }
                    player.sendSystemMessage(Component.translatable("dvteam.cmd.claim_failed").withStyle(ChatFormatting.RED));
                    return 0;
                }))
                .then(Commands.literal("unclaim").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ChunkPos cp = player.chunkPosition();
                    String dim = player.level().dimension().location().toString();
                    TeamSavedData data = TeamSavedData.get(player.getServer());
                    if (data.unclaimChunk(player.getServer(), player, dim, cp.x, cp.z, false)) {
                        TeamNetwork.syncToAll(player.getServer());
                        player.sendSystemMessage(Component.translatable("dvteam.cmd.unclaimed", cp.x, cp.z).withStyle(ChatFormatting.YELLOW));
                        return 1;
                    }
                    return 0;
                }));
    }
}
