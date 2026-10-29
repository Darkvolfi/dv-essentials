package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVChoicePopup;
import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvlib.client.DVIconButton;
import com.darkvolfi.dvlib.client.DVMods;
import com.darkvolfi.dvlib.client.DVSlider;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvmusic.DVMusic;
import com.darkvolfi.dvmusic.MusicConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The music player: what is playing and how far along, play/pause, back and next, which songs play
 * (mode), the music volume, and every song, Minecraft's and the player's, to pick from.
 */
public class MusicScreen extends Screen {
    private static final int MAX_WIDTH = 320;
    private static final int PAD = 8;
    private static final int ROW = 13;

    private record Row(Component text, Track track, Component tag) {
        boolean header() {
            return track == null;
        }
    }

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private EditBox search;
    private DVIconButton playButton;
    private int scroll;

    public MusicScreen(Screen parent) {
        super(Component.translatable("dvmusic.title"));
        this.parent = parent;
    }

    private int panelWidth() {
        return Math.min(MAX_WIDTH, width - 20);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int top() {
        return 10;
    }

    private int listTop() {
        return top() + 110;
    }

    private int listBottom() {
        return height - 10 - PAD;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW);
    }

    @Override
    protected void init() {
        int l = left();
        int w = panelWidth();
        int t = top();

        addRenderableWidget(new DVIconButton(l + w - 20, t + 6, 14, DVUi.CLOSE, CommonComponents.GUI_DONE, this::onClose));
        DVMods.Mod mod = DVMods.get(DVMusic.MOD_ID);
        if (mod != null) {
            addRenderableWidget(new DVIconButton(l + w - 38, t + 6, 14, DVUi.GEAR, Component.translatable("dvmusic.settings"),
                    () -> minecraft.setScreen(new DVConfigScreen(this, DVMusic.MOD_ID, mod.clientSpec(), mod.serverSpec(), mod.onSave()))));
        }

        int cy = t + 68;
        addRenderableWidget(new DVIconButton(l + PAD, cy, 18, MusicIcons.PREVIOUS, Component.translatable("dvmusic.previous"), MusicPlayer::previous));
        playButton = addRenderableWidget(new DVIconButton(l + PAD + 21, cy, 18, MusicIcons.PLAY, Component.translatable("dvmusic.play_pause"), MusicPlayer::togglePause));
        addRenderableWidget(new DVIconButton(l + PAD + 42, cy, 18, MusicIcons.NEXT, Component.translatable("dvmusic.next"), MusicPlayer::next));
        DVIconButton[] repeat = new DVIconButton[1];
        repeat[0] = addRenderableWidget(new DVIconButton(l + PAD + 63, cy, 18, MusicIcons.REPEAT, Component.translatable("dvmusic.config.repeat"), () -> {
            MusicConfig.REPEAT.set(!MusicConfig.REPEAT.get());
            MusicConfig.SPEC.save();
            repeat[0].selected(MusicConfig.REPEAT.get());
        }).selected(MusicConfig.REPEAT.get()));

        addRenderableWidget(new DVButton(l + PAD + 87, cy + 1, 92, 16, modeLabel(MusicConfig.MODE.get()), b -> {
            List<DVChoicePopup.Option<MusicConfig.Mode>> options = new ArrayList<>();
            for (MusicConfig.Mode m : MusicConfig.Mode.values()) options.add(new DVChoicePopup.Option<>(m, modeLabel(m)));
            DVChoicePopup.open(b, options, MusicConfig.MODE.get(), m -> {
                MusicConfig.MODE.set(m);
                MusicConfig.SPEC.save();
                b.setMessage(modeLabel(m));
            });
        }).menu()).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("dvmusic.config.mode")));

        OptionInstance<Double> volume = minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC);
        int sliderX = l + PAD + 87 + 92 + 8;
        addRenderableWidget(new DVSlider(sliderX, cy + 4, l + w - PAD - sliderX, 0, 100, (int) Math.round(volume.get() * 100),
                v -> volume.set(v / 100.0)));

        search = new EditBox(font, l + PAD + 1, t + 92, w - 2 * PAD - 40, 14, Component.translatable("dvlib.search"));
        search.setHint(Component.translatable("dvlib.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(text -> {
            scroll = 0;
            buildRows();
        });
        addRenderableWidget(search);
        addRenderableWidget(new DVIconButton(l + w - PAD - 36, t + 91, 16, DVUi.FOLDER, Component.translatable("dvmusic.folder"),
                () -> Util.getPlatform().openUri(TrackLibrary.folder().toUri())));
        addRenderableWidget(new DVIconButton(l + w - PAD - 16, t + 91, 16, DVUi.LOAD, Component.translatable("dvmusic.refresh"), () -> {
            TrackLibrary.reload();
            buildRows();
        }));
        buildRows();
    }

    private static Component modeLabel(MusicConfig.Mode mode) {
        return Component.translatable("dvmusic.config.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private void buildRows() {
        rows.clear();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Track> mine = filter(TrackLibrary.custom(), query);
        rows.add(new Row(Component.translatable("dvmusic.list.yours", mine.size()), null, null));
        for (Track t : mine) rows.add(new Row(Component.literal(t.title()), t, where(t)));
        if (TrackLibrary.custom().isEmpty()) {
            rows.add(new Row(Component.translatable("dvmusic.list.empty").withStyle(ChatFormatting.GRAY), null, null));
        }
        if (TrackLibrary.unplayable() > 0) {
            rows.add(new Row(Component.translatable("dvmusic.list.unplayable", TrackLibrary.unplayable()).withStyle(ChatFormatting.GOLD), null, null));
        }
        List<Track> minecraftTracks = filter(TrackLibrary.vanilla(), query);
        rows.add(new Row(Component.translatable("dvmusic.list.minecraft", minecraftTracks.size()), null, null));
        for (Track t : minecraftTracks) rows.add(new Row(Component.literal(t.title()), t, where(t)));
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visibleRows()));
    }

    private static List<Track> filter(List<Track> tracks, String query) {
        if (query.isEmpty()) return tracks;
        List<Track> out = new ArrayList<>();
        for (Track t : tracks) {
            if ((t.title() + " " + t.artist()).toLowerCase(Locale.ROOT).contains(query)) out.add(t);
        }
        return out;
    }

    /** Where a song plays: "Everywhere", or its situations. */
    private static Component where(Track track) {
        if (track.situations().isEmpty()) return Component.translatable("dvmusic.situation.everywhere");
        List<Situation> list = new ArrayList<>(track.situations());
        list.sort(null);
        Component text = Component.translatable(list.get(0).translationKey());
        return list.size() == 1 ? text : Component.empty().append(text).append(" +" + (list.size() - 1));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int l = left();
        int w = panelWidth();
        int t = top();
        DVUi.panel(g, l, t, w, height - 20, false);
        DVUi.icon(g, MusicIcons.NOTE, l + PAD + 4, t + 13, 1, DVUi.accent());
        g.drawString(font, title, l + PAD + 12, t + 9, 0xFFE6E6F0, false);

        renderNowPlaying(g, l, t + 26, w);
        playButton.setIcon(MusicPlayer.current() != null && !MusicPlayer.paused() ? MusicIcons.PAUSE : MusicIcons.PLAY);
        renderList(g, l, w, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderNowPlaying(GuiGraphics g, int l, int y, int w) {
        Track track = MusicPlayer.current();
        g.fill(l + PAD, y, l + w - PAD, y + 38, 0x40000000);
        if (track == null) {
            g.drawString(font, Component.translatable("dvmusic.nothing"), l + PAD + 4, y + 5, 0xFFA0A0A0, false);
            g.drawString(font, Component.translatable("dvmusic.situation_now", Component.translatable(MusicPlayer.situation().translationKey())),
                    l + PAD + 4, y + 17, 0xFF707080, false);
            return;
        }
        g.drawString(font, font.plainSubstrByWidth(track.title(), w - 2 * PAD - 8), l + PAD + 4, y + 4, DVUi.accent(), false);
        Component source = track.artist().isEmpty()
                ? Component.translatable(track.custom() ? "dvmusic.source.yours" : "dvmusic.source.minecraft")
                : Component.literal(track.artist());
        g.drawString(font, Component.empty().append(source).append(" · ").append(where(track)), l + PAD + 4, y + 15, 0xFFA0A0A0, false);

        int played = MusicPlayer.playedSeconds();
        int length = MusicPlayer.lengthSeconds();
        String time = length > 0 ? clock(Math.min(played, length)) + " / " + clock(length) : clock(played);
        int barX = l + PAD + 4;
        int barW = w - 2 * PAD - 12 - font.width(time) - 6;
        int barY = y + 28;
        g.fill(barX, barY, barX + barW, barY + 4, 0xFF0C0B10);
        if (length > 0) g.fill(barX, barY, barX + Math.round(barW * Math.min(1f, played / (float) length)), barY + 4, DVUi.accent());
        g.drawString(font, time, barX + barW + 6, barY - 2, 0xFFE6E6F0, false);
    }

    private static String clock(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void renderList(GuiGraphics g, int l, int w, int mouseX, int mouseY) {
        int y = listTop();
        int end = Math.min(rows.size(), scroll + visibleRows());
        for (int i = scroll; i < end; i++, y += ROW) {
            Row row = rows.get(i);
            if (row.header()) {
                g.drawString(font, row.text(), l + PAD, y + 3, row.text().getStyle().getColor() == null ? DVUi.accent() : 0xFFFFFFFF, false);
                continue;
            }
            boolean hover = mouseX >= l + PAD && mouseX < l + w - PAD && mouseY >= y && mouseY < y + ROW;
            boolean now = row.track().equals(MusicPlayer.current());
            if (hover) g.fill(l + PAD, y, l + w - PAD, y + ROW, 0x30FFFFFF);
            if (now) DVUi.icon(g, MusicIcons.PLAY, l + PAD + 5, y + 6, 1, DVUi.accent());
            int tagW = font.width(row.tag());
            int room = w - 2 * PAD - 22 - tagW;
            String name = font.plainSubstrByWidth(row.text().getString(), room);
            g.drawString(font, name, l + PAD + 12, y + 3, now ? DVUi.accent() : 0xFFE6E6F0, false);
            String artist = row.track().artist();
            int artistX = l + PAD + 12 + font.width(name) + 6;
            if (!artist.isEmpty() && font.width(name) + 6 < room) {
                g.drawString(font, font.plainSubstrByWidth(artist, room - font.width(name) - 6), artistX, y + 3, 0xFF707080, false);
            }
            g.drawString(font, row.tag(), l + w - PAD - 4 - tagW, y + 3, 0xFF707080, false);
        }
        if (rows.size() > visibleRows()) {
            int trackH = listBottom() - listTop();
            int thumbH = Math.max(10, trackH * visibleRows() / rows.size());
            int thumbY = listTop() + (trackH - thumbH) * scroll / (rows.size() - visibleRows());
            g.fill(l + w - 4, thumbY, l + w - 2, thumbY + thumbH, DVUi.accent());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int l = left();
        if (mouseX >= l + PAD && mouseX < l + panelWidth() - PAD && mouseY >= listTop() && mouseY < listTop() + visibleRows() * ROW) {
            int i = scroll + (int) ((mouseY - listTop()) / ROW);
            if (i < rows.size() && !rows.get(i).header()) {
                MusicPlayer.choose(rows.get(i).track());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 3, 0, Math.max(0, rows.size() - visibleRows()));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.options.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
