package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvlib.client.DVUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/** "Now playing" in the corner for a few seconds, in DV's panel look. */
final class NowPlayingToast implements Toast {
    private static final long SHOW_MS = 4000;
    private static final int WIDTH = 160;

    private String[] icon;
    private Component heading;
    private Component title;
    private long shownAt = -1;

    private NowPlayingToast(String[] icon, Component heading, Component title) {
        this.icon = icon;
        this.heading = heading;
        this.title = title;
    }

    static void show(Track track) {
        Component heading = Component.translatable(track.custom() ? "dvmusic.toast.yours" : "dvmusic.toast.playing");
        if (!track.artist().isEmpty()) heading = Component.empty().append(heading).append(" · " + track.artist());
        add(new NowPlayingToast(MusicIcons.NOTE, heading, Component.literal(track.title())));
    }

    static void showRecord(Component title) {
        add(new NowPlayingToast(MusicIcons.DISC, Component.translatable("dvmusic.toast.record"), title));
    }

    /** One toast at a time: a new song replaces the one shown. */
    private static void add(NowPlayingToast toast) {
        ToastComponent toasts = Minecraft.getInstance().getToasts();
        NowPlayingToast shown = toasts.getToast(NowPlayingToast.class, Toast.NO_TOKEN);
        if (shown != null) {
            shown.icon = toast.icon;
            shown.heading = toast.heading;
            shown.title = toast.title;
            shown.shownAt = -1;
        } else {
            toasts.addToast(toast);
        }
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        if (shownAt < 0) shownAt = time;
        Font font = toasts.getMinecraft().font;
        DVUi.panel(g, 0, 0, WIDTH, height(), false);
        DVUi.icon(g, icon, 14, 16, 2, DVUi.accent());
        g.drawString(font, heading, 28, 7, 0xFFA0A0A0, false);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), WIDTH - 34), 28, 18, 0xFFE6E6F0, false);
        return time - shownAt >= SHOW_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
