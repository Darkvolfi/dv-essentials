package com.darkvolfi.dvteam.command;

import com.darkvolfi.dvteam.ClanChat;
import com.darkvolfi.dvteam.DVTeam;
import com.darkvolfi.dvteam.TeamConfig;
import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanLevel;
import com.darkvolfi.dvteam.data.ClanTypes.SettlementType;
import com.darkvolfi.dvteam.data.ClanVault;
import com.darkvolfi.dvteam.network.ClanActionPacket;
import com.darkvolfi.dvteam.network.TeamNetwork;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/** /klan (also /clan and /dvteam): the everyday actions; the rest is in the clan screen (Y). */
@Mod.EventBusSubscriber(modid = DVTeam.MOD_ID)
public final class TeamCommands {
    private TeamCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        for (String root : new String[]{"klan", "clan", "dvteam"}) event.getDispatcher().register(buildTree(root));
    }

    private static int done(ServerPlayer player, String error, Component success) {
        if (error != null) {
            player.sendSystemMessage(Component.translatable(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        TeamNetwork.syncToAll(player.getServer());
        if (success != null) player.sendSystemMessage(success.copy().withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTree(String root) {
        return Commands.literal(root)
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ClanData data = ClanData.get(player.getServer());
                    ClanData.Clan clan = data.clanOf(player.getUUID());
                    if (clan == null) return done(player, "dvteam.err.no_clan", null);
                    player.sendSystemMessage(Component.translatable("dvteam.cmd.info", ClanActionPacket.clanName(clan),
                            clan.members.size(), data.chunkCount(clan.id),
                            ClanLevel.chunkLimit(TeamConfig.MAX_CHUNKS_PER_CLAN.get(), data.level(clan)), data.level(clan)));
                    return 1;
                }))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            ClanData data = ClanData.get(player.getServer());
                            String error = data.invite(player, target.getUUID());
                            if (error == null) {
                                target.sendSystemMessage(Component.translatable("dvteam.msg.invited_you",
                                        ClanActionPacket.clanName(data.clanOf(player.getUUID()))).withStyle(ChatFormatting.GOLD));
                            }
                            return done(player, error, Component.translatable("dvteam.msg.invited_sent", target.getGameProfile().getName()));
                        })))
                .then(Commands.literal("join")
                        .then(Commands.argument("clan", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String query = StringArgumentType.getString(ctx, "clan").trim();
                            ClanData data = ClanData.get(player.getServer());
                            for (ClanData.Clan c : data.clans()) {
                                if (c.name.equalsIgnoreCase(query) || c.tag.equalsIgnoreCase(query)) {
                                    return done(player, data.join(player, c.id), Component.translatable("dvteam.cmd.joined", c.name));
                                }
                            }
                            return done(player, "dvteam.err.no_invite", null);
                        })))
                .then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return done(player, ClanData.get(player.getServer()).leave(player.getServer(), player),
                            Component.translatable("dvteam.cmd.left"));
                }))
                .then(Commands.literal("msg")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> chat(ctx.getSource().getPlayerOrException(), ClanChat.Channel.CLAN,
                                        StringArgumentType.getString(ctx, "message")))))
                .then(Commands.literal("ally")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> chat(ctx.getSource().getPlayerOrException(), ClanChat.Channel.ALLY,
                                        StringArgumentType.getString(ctx, "message")))))
                .then(Commands.literal("chat")
                        .then(chatMode(ClanChat.Channel.ALL))
                        .then(chatMode(ClanChat.Channel.CLAN))
                        .then(chatMode(ClanChat.Channel.ALLY)))
                .then(Commands.literal("vault").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return done(player, ClanVault.open(player), null);
                }))
                .then(Commands.literal("claim")
                        .executes(ctx -> claimHere(ctx.getSource().getPlayerOrException(), ""))
                        .then(Commands.argument("settlementName", StringArgumentType.greedyString())
                                .executes(ctx -> claimHere(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "settlementName")))))
                .then(Commands.literal("unclaim").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ChunkPos cp = player.chunkPosition();
                    String dim = player.level().dimension().location().toString();
                    return done(player, ClanData.get(player.getServer()).unclaim(player.getServer(), player, dim, cp.x, cp.z),
                            Component.translatable("dvteam.cmd.unclaimed", cp.x, cp.z));
                }));
    }

    private static int chat(ServerPlayer player, ClanChat.Channel channel, String message) {
        return ClanChat.send(player, channel, message) ? 1 : done(player, "dvteam.err.no_clan", null);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> chatMode(ClanChat.Channel channel) {
        return Commands.literal(channel.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (channel != ClanChat.Channel.ALL && ClanData.get(player.getServer()).clanOf(player.getUUID()) == null) {
                return done(player, "dvteam.err.no_clan", null);
            }
            ClanChat.setChannel(player, channel);
            player.sendSystemMessage(Component.translatable("dvteam.chat.mode." + channel.name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        });
    }

    /** Claims the chunk the player stands in; a name is needed when it founds a new settlement (a village). */
    private static int claimHere(ServerPlayer player, String name) {
        ChunkPos cp = player.chunkPosition();
        String dim = player.level().dimension().location().toString();
        String error = ClanData.get(player.getServer()).claim(player.getServer(), player, dim, cp.x, cp.z, name, SettlementType.VILLAGE);
        return done(player, error, Component.translatable("dvteam.cmd.claimed", cp.x, cp.z));
    }
}
