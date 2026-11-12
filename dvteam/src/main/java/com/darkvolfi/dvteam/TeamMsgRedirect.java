package com.darkvolfi.dvteam;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The game's /teammsg (/tm) only knows scoreboard teams. A player who is in a clan but on no scoreboard team gets
 * their message sent to clan chat instead of the "You must be on a team" error. Players on a team are untouched.
 */
@Mod.EventBusSubscriber(modid = DVTeam.MOD_ID)
public final class TeamMsgRedirect {
    private TeamMsgRedirect() {}

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        List<ParsedCommandNode<CommandSourceStack>> nodes = parse.getContext().getNodes();
        if (nodes.isEmpty()) return;
        String root = nodes.get(0).getNode().getName();
        if (!root.equals("teammsg") && !root.equals("tm")) return;
        if (!(parse.getContext().getSource().getEntity() instanceof ServerPlayer player) || player.getTeam() != null) return;
        String input = parse.getReader().getString();
        int space = input.indexOf(' ');
        String message = space < 0 ? "" : input.substring(space + 1).trim();
        if (message.isEmpty()) return; // let the game show its usage error
        if (ClanChat.send(player, ClanChat.Channel.CLAN, message)) event.setCanceled(true);
    }
}
