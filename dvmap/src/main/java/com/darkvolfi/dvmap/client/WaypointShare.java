package com.darkvolfi.dvmap.client;

import com.darkvolfi.dvmap.DVMap;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sharing a waypoint posts it to chat as plain text that anyone can read. Players with DV Map get a clickable
 * [+] after it, which runs a client-side command that opens the new-waypoint screen pre-filled.
 */
@Mod.EventBusSubscriber(modid = DVMap.MOD_ID, value = Dist.CLIENT)
public final class WaypointShare {
    private static final String TAG = "[DV Map]";
    private static final String COMMAND = "dvmap_waypoint";
    private static final Pattern SHARED = Pattern.compile(
            Pattern.quote(TAG) + " (.{1,32}?) \\((-?\\d{1,8}), (-?\\d{1,4}), (-?\\d{1,8})\\) ([a-z0-9_.-]+:[a-z0-9_/.-]+)");

    private WaypointShare() {}

    public static void share(Minecraft mc, Waypoint wp) {
        if (mc.getConnection() == null) return;
        mc.getConnection().sendChat(TAG + " " + wp.name + " (" + wp.x + ", " + wp.y + ", " + wp.z + ") " + wp.dimension);
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        Matcher m = SHARED.matcher(event.getMessage().getString());
        if (!m.find()) return;
        String command = "/" + COMMAND + " " + m.group(2) + " " + m.group(3) + " " + m.group(4) + " " + m.group(5) + " " + m.group(1);
        Component add = Component.literal(" [+]").withStyle(s -> s.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("dvmap.waypoint.share.add"))));
        event.setMessage(event.getMessage().copy().append(add));
    }

    /**
     * Forge 1.20.1 only runs client commands typed into chat; a clicked RUN_COMMAND goes straight to the server,
     * which doesn't know ours. So the click on our [+] is caught here and run on the client.
     */
    @SubscribeEvent
    public static void onChatClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen) || event.getButton() != 0) return;
        Style style = Minecraft.getInstance().gui.getChat().getClickedComponentStyleAt(event.getMouseX(), event.getMouseY());
        ClickEvent click = style == null ? null : style.getClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND || !click.getValue().startsWith("/" + COMMAND + " ")) return;
        ClientCommandHandler.runCommand(click.getValue().substring(1));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND)
                .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                .then(Commands.argument("z", IntegerArgumentType.integer())
                .then(Commands.argument("dimension", ResourceLocationArgument.id())
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(WaypointShare::open)))))));
    }

    private static int open(CommandContext<CommandSourceStack> ctx) {
        Minecraft mc = Minecraft.getInstance();
        WaypointEditScreen screen = new WaypointEditScreen(null, null,
                IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"),
                ResourceLocationArgument.getId(ctx, "dimension").toString())
                .withName(StringArgumentType.getString(ctx, "name"));
        // The chat screen is still closing when the command runs.
        mc.tell(() -> mc.setScreen(screen));
        return 1;
    }
}
