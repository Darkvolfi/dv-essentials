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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The music player: what is playing and how far along, play/pause, back and next, which songs play
 * (mode or a category), the music volume, and every song, Minecraft's and the player's, to pick from.
 * A category can be opened to tick the songs that belong to it. Files dropped on the window go into the music folder.
 */
public class MusicScreen extends Screen {
    private static final int MAX_WIDTH = 320;
    private static final int PAD = 8;
    private static final int ROW = 13;
    private static final int BOX = 12;

    private record Row(Component text, Track track, Component tag) {
        boolean header() {
            return track == null;
        }
    }

    /** The category being looked at (its name), kept while the game runs; empty: all songs. */
    private static String viewing = "";

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private EditBox search;
    private DVIconButton playButton;
    private int scroll;
    private Component message;
    private long messageUntil;

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
        return top() + 130;
    }

    private int listBottom() {
        return height - 10 - PAD - 12;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW);
    }

    private static Categories.Category view() {
        return viewing.isEmpty() ? null : Categories.get(viewing);
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

        addRenderableWidget(new DVButton(l + PAD + 87, cy + 1, 92, 16, playsLabel(), b -> {
            List<DVChoicePopup.Option<String>> options = new ArrayList<>();
            for (MusicConfig.Mode m : MusicConfig.Mode.values()) options.add(new DVChoicePopup.Option<>("mode:" + m.name(), modeLabel(m)));
            for (Categories.Category c : Categories.all()) {
                options.add(new DVChoicePopup.Option<>("cat:" + c.name(), Component.translatable("dvmusic.plays.category", c.name())));
            }
            Categories.Category active = Categories.active();
            String current = active != null ? "cat:" + active.name() : "mode:" + MusicConfig.MODE.get().name();
            DVChoicePopup.open(b, options, current, v -> {
                if (v.startsWith("cat:")) {
                    Categories.setActive(Categories.get(v.substring(4)));
                } else {
                    Categories.setActive(null);
                    MusicConfig.MODE.set(MusicConfig.Mode.valueOf(v.substring(5)));
                    MusicConfig.SPEC.save();
                }
                b.setMessage(playsLabel());
            });
        }).menu()).setTooltip(Tooltip.create(Component.translatable("dvmusic.config.mode")));

        OptionInstance<Double> volume = minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC);
        int sliderX = l + PAD + 87 + 92 + 8;
        addRenderableWidget(new DVSlider(sliderX, cy + 4, l + w - PAD - sliderX, 0, 100, (int) Math.round(volume.get() * 100),
                v -> volume.set(v / 100.0)));

        // Search, and the music folder: a labelled button, since that is where songs go.
        int folderW = 96;
        String typed = search == null ? "" : search.getValue(); // kept when the widgets are rebuilt
        search = new EditBox(font, l + PAD + 1, t + 92, w - 2 * PAD - folderW - 24, 14, Component.translatable("dvmusic.search"));
        search.setHint(Component.translatable("dvmusic.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(typed);
        search.setResponder(text -> {
            scroll = 0;
            buildRows();
        });
        addRenderableWidget(search);
        addRenderableWidget(new DVButton(l + w - PAD - folderW - 18, t + 91, folderW, 16, Component.translatable("dvmusic.folder"),
                b -> Util.getPlatform().openUri(TrackLibrary.folder().toUri())))
                .setTooltip(Tooltip.create(Component.translatable("dvmusic.folder.tip")));
        addRenderableWidget(new DVIconButton(l + w - PAD - 16, t + 91, 16, DVUi.LOAD, Component.translatable("dvmusic.refresh"), () -> {
            TrackLibrary.reload();
            buildRows();
        }));

        // Categories: which one is open, a new one, and editing the open one.
        Categories.Category view = view();
        if (view == null) viewing = "";
        int catW = Math.min(150, w - 2 * PAD - 40);
        addRenderableWidget(new DVButton(l + PAD, t + 110, catW, 16, viewLabel(view), b -> {
            List<DVChoicePopup.Option<String>> options = new ArrayList<>();
            options.add(new DVChoicePopup.Option<>("", Component.translatable("dvmusic.category.all")));
            for (Categories.Category c : Categories.all()) options.add(new DVChoicePopup.Option<>(c.name(), Component.literal(c.name())));
            DVChoicePopup.open(b, options, viewing, v -> {
                viewing = v;
                scroll = 0;
                rebuildWidgets();
            });
        }).menu()).setTooltip(Tooltip.create(Component.translatable("dvmusic.category.view")));
        addRenderableWidget(new DVIconButton(l + PAD + catW + 4, t + 111, 14, DVUi.PLUS, Component.translatable("dvmusic.category.new"),
                () -> minecraft.setScreen(new CategoryScreen(this, null, created -> viewing = created.name()))));
        if (view != null) {
            addRenderableWidget(new DVIconButton(l + PAD + catW + 22, t + 111, 14, DVUi.GEAR, Component.translatable("dvmusic.category.edit"),
                    () -> minecraft.setScreen(new CategoryScreen(this, view, edited -> viewing = edited == null ? "" : edited.name()))));
        }
        buildRows();
    }

    private static Component modeLabel(MusicConfig.Mode mode) {
        return Component.translatable("dvmusic.config.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private static Component playsLabel() {
        Categories.Category active = Categories.active();
        return active != null ? Component.translatable("dvmusic.plays.category", active.name()) : modeLabel(MusicConfig.MODE.get());
    }

    private static Component viewLabel(Categories.Category view) {
        return view == null ? Component.translatable("dvmusic.category.all") : Component.translatable("dvmusic.category.named", view.name());
    }

    private void buildRows() {
        rows.clear();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        Categories.Category view = view();
        if (view != null) {
            List<Track> in = new ArrayList<>();
            for (Track t : TrackLibrary.custom()) if (view.contains(t)) in.add(t);
            for (Track t : TrackLibrary.vanilla()) if (view.contains(t)) in.add(t);
            in = filter(in, query);
            rows.add(new Row(Component.translatable("dvmusic.category.songs", view.name(), in.size()), null, null));
            for (Track t : in) rows.add(new Row(Component.literal(t.title()), t, where(t)));
            if (view.songs.isEmpty()) rows.add(new Row(Component.translatable("dvmusic.category.empty").withStyle(ChatFormatting.GRAY), null, null));
        }
        List<Track> mine = filter(outside(TrackLibrary.custom(), view), query);
        rows.add(new Row(Component.translatable(view == null ? "dvmusic.list.yours" : "dvmusic.list.add_yours", mine.size()), null, null));
        for (Track t : mine) rows.add(new Row(Component.literal(t.title()), t, where(t)));
        if (TrackLibrary.custom().isEmpty()) {
            rows.add(new Row(Component.translatable("dvmusic.list.empty").withStyle(ChatFormatting.GRAY), null, null));
        }
        if (TrackLibrary.unplayable() > 0) {
            rows.add(new Row(Component.translatable("dvmusic.list.unplayable", TrackLibrary.unplayable()).withStyle(ChatFormatting.GOLD), null, null));
        }
        List<Track> minecraftTracks = filter(outside(TrackLibrary.vanilla(), view), query);
        rows.add(new Row(Component.translatable(view == null ? "dvmusic.list.minecraft" : "dvmusic.list.add_minecraft", minecraftTracks.size()), null, null));
        for (Track t : minecraftTracks) rows.add(new Row(Component.literal(t.title()), t, where(t)));
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visibleRows()));
    }

    /** The songs not in {@code view} (all of them when no category is open). */
    private static List<Track> outside(List<Track> tracks, Categories.Category view) {
        if (view == null) return tracks;
        List<Track> out = new ArrayList<>();
        for (Track t : tracks) if (!view.contains(t)) out.add(t);
        return out;
    }

    private static List<Track> filter(List<Track> tracks, String query) {
        if (query.isEmpty()) return tracks;
        List<Track> out = new ArrayList<>();
        for (Track t : tracks) {
            if ((t.title() + " " + t.artist()).toLowerCase(Locale.ROOT).contains(query)) out.add(t);
        }
        return out;
    }

    /** Where a song plays: "Everywhere", or its places. */
    private static Component where(Track track) {
        Set<Situation> places = Categories.places(track);
        if (places.isEmpty()) return Component.translatable("dvmusic.situation.everywhere");
        List<Situation> list = new ArrayList<>(places);
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

        Component footer = message != null && Util.getMillis() < messageUntil ? message : Component.translatable("dvmusic.formats");
        g.drawString(font, font.plainSubstrByWidth(footer.getString(), w - 2 * PAD), l + PAD, listBottom() + 3,
                footer == message ? DVUi.accent() : 0xFF707080, false);
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
        Categories.Category view = view();
        int indent = view == null ? 0 : BOX + 2;
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
            if (view != null) {
                boolean in = view.contains(row.track());
                boolean boxHover = hover && mouseX < l + PAD + BOX;
                int bx = l + PAD + 1, by = y + 1;
                g.fill(bx, by, bx + BOX - 1, by + BOX - 1, boxHover ? DVUi.accent() : 0xFF000000);
                g.fill(bx + 1, by + 1, bx + BOX - 2, by + BOX - 2, in ? 0xFF4CAF50 : 0xFF3A3548);
                if (in) DVUi.icon(g, DVUi.CHECK, bx + 5, by + 5, 1, 0xFFE6E6F0);
            }
            if (now) DVUi.icon(g, MusicIcons.PLAY, l + PAD + indent + 5, y + 6, 1, DVUi.accent());
            int tagW = font.width(row.tag());
            int room = w - 2 * PAD - 22 - indent - tagW;
            String name = font.plainSubstrByWidth(row.text().getString(), room);
            int nameX = l + PAD + indent + 12;
            g.drawString(font, name, nameX, y + 3, now ? DVUi.accent() : 0xFFE6E6F0, false);
            String artist = row.track().artist();
            if (!artist.isEmpty() && font.width(name) + 6 < room) {
                g.drawString(font, font.plainSubstrByWidth(artist, room - font.width(name) - 6), nameX + font.width(name) + 6, y + 3, 0xFF707080, false);
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
                Categories.Category view = view();
                if (view != null && mouseX < l + PAD + BOX + 1) {
                    Categories.toggle(view, rows.get(i).track());
                    buildRows();
                } else {
                    MusicPlayer.choose(rows.get(i).track());
                }
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

    /** Songs dragged onto the window are copied into the music folder (and into the open category). */
    @Override
    public void onFilesDrop(List<Path> files) {
        TrackLibrary.Import result = TrackLibrary.importFiles(files);
        Categories.Category view = view();
        if (view != null && !result.added().isEmpty()) {
            view.songs.addAll(result.added());
            Categories.save();
        }
        message = result.skipped() > 0
                ? Component.translatable("dvmusic.drop.some", result.added().size(), result.skipped())
                : Component.translatable("dvmusic.drop.added", result.added().size());
        messageUntil = Util.getMillis() + 6000;
        buildRows();
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
