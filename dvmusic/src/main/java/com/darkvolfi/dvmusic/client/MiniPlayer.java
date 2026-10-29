package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvlib.client.DVIconButton;
import com.darkvolfi.dvlib.client.DVSlider;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvmusic.DVMusic;
import com.darkvolfi.dvmusic.MusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * A small player in a corner of the pause menu (which corner is a setting): the song, its composer, how far
 * along it is, back / play-pause / next, repeat, the music volume, and a button for the full player.
 * Clicking the song's name also opens the full player.
 */
@Mod.EventBusSubscriber(modid = DVMusic.MOD_ID, value = Dist.CLIENT)
public final class MiniPlayer extends AbstractButton {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 46;
    private static final int BUTTON = 14;
    private static final int MARGIN = 4;
    /** The part that opens the full player: name, composer and progress. */
    private static final int INFO_HEIGHT = 27;

    private final Screen screen;
    private final DVIconButton play;
    private final DVIconButton repeat;

    private MiniPlayer(Screen screen, int x, int y, DVIconButton play, DVIconButton repeat) {
        super(x, y, WIDTH, INFO_HEIGHT, Component.translatable("dvmusic.title"));
        this.screen = screen;
        this.play = play;
        this.repeat = repeat;
        setTooltip(Tooltip.create(Component.translatable("dvmusic.open")));
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen) || !MusicConfig.SPEC.isLoaded()) return;
        MusicConfig.Corner corner = MusicConfig.PLAYER_CORNER.get();
        if (corner == MusicConfig.Corner.HIDDEN) return;
        boolean right = corner == MusicConfig.Corner.TOP_RIGHT || corner == MusicConfig.Corner.BOTTOM_RIGHT;
        boolean bottom = corner == MusicConfig.Corner.BOTTOM_RIGHT || corner == MusicConfig.Corner.BOTTOM_LEFT;
        int x = right ? screen.width - WIDTH - MARGIN : MARGIN;
        int y = bottom ? screen.height - HEIGHT - MARGIN : MARGIN;

        int by = y + INFO_HEIGHT + 1;
        int bx = x + 5;
        DVIconButton previous = new DVIconButton(bx, by, BUTTON, MusicIcons.PREVIOUS, Component.translatable("dvmusic.previous"), MusicPlayer::previous);
        DVIconButton play = new DVIconButton(bx + 16, by, BUTTON, MusicIcons.PLAY, Component.translatable("dvmusic.play_pause"), MusicPlayer::togglePause);
        DVIconButton next = new DVIconButton(bx + 32, by, BUTTON, MusicIcons.NEXT, Component.translatable("dvmusic.next"), MusicPlayer::next);
        DVIconButton[] repeat = new DVIconButton[1];
        repeat[0] = new DVIconButton(bx + 52, by, BUTTON, MusicIcons.REPEAT, Component.translatable("dvmusic.config.repeat"), () -> {
            MusicConfig.REPEAT.set(!MusicConfig.REPEAT.get());
            MusicConfig.SPEC.save();
        }).selected(MusicConfig.REPEAT.get());

        OptionInstance<Double> volume = Minecraft.getInstance().options.getSoundSourceOptionInstance(SoundSource.MUSIC);
        int sliderX = bx + 72 + 12;
        int sliderW = x + WIDTH - 5 - BUTTON - 4 - sliderX;
        DVSlider slider = new DVSlider(sliderX, by + 1, sliderW, 0, 100, (int) Math.round(volume.get() * 100), v -> volume.set(v / 100.0));
        slider.setTooltip(Tooltip.create(Component.translatable("dvmusic.volume")));
        DVIconButton list = new DVIconButton(x + WIDTH - 5 - BUTTON, by, BUTTON, MusicIcons.LIST, Component.translatable("dvmusic.open"),
                () -> Minecraft.getInstance().setScreen(new MusicScreen(screen)));

        event.addListener(new MiniPlayer(screen, x, y, play, repeat[0]));
        event.addListener(previous);
        event.addListener(play);
        event.addListener(next);
        event.addListener(repeat[0]);
        event.addListener(slider);
        event.addListener(list);
    }

    @Override
    public void onPress() {
        Minecraft.getInstance().setScreen(new MusicScreen(screen));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        DVUi.panel(g, x, y, WIDTH, HEIGHT, false);
        if (isHoveredOrFocused()) g.fill(x + 1, y + 1, x + WIDTH - 1, y + INFO_HEIGHT, 0x18FFFFFF);
        DVUi.icon(g, MusicIcons.NOTE, x + 9, y + 9, 1, DVUi.accent());

        Track track = MusicPlayer.current();
        String time = "";
        if (track != null) {
            int played = MusicPlayer.playedSeconds();
            int length = MusicPlayer.lengthSeconds();
            time = length > 0 ? clock(Math.min(played, length)) + "/" + clock(length) : clock(played);
        }
        int textRoom = WIDTH - 22 - font.width(time) - 6;
        String title = track == null ? Component.translatable("dvmusic.nothing").getString() : track.title();
        g.drawString(font, font.plainSubstrByWidth(title, textRoom), x + 16, y + 5, track == null ? 0xFFA0A0A0 : 0xFFE6E6F0, false);
        g.drawString(font, time, x + WIDTH - 6 - font.width(time), y + 5, 0xFFA0A0A0, false);
        if (track != null) {
            String artist = track.artist().isEmpty()
                    ? Component.translatable(track.custom() ? "dvmusic.source.yours" : "dvmusic.source.minecraft").getString()
                    : track.artist();
            g.drawString(font, font.plainSubstrByWidth(artist, WIDTH - 22), x + 16, y + 15, 0xFF808090, false);
        }

        // Progress along the bottom of the info part.
        int barX = x + 5;
        int barW = WIDTH - 10;
        int barY = y + INFO_HEIGHT - 2;
        g.fill(barX, barY, barX + barW, barY + 2, 0xFF0C0B10);
        int length = MusicPlayer.lengthSeconds();
        if (track != null && length > 0) {
            g.fill(barX, barY, barX + Math.round(barW * Math.min(1f, MusicPlayer.playedSeconds() / (float) length)), barY + 2, DVUi.accent());
        }

        // Volume speaker before the slider.
        DVUi.icon(g, MusicIcons.SPEAKER, x + 5 + 72 + 4, y + INFO_HEIGHT + 8, 1, 0xFFA0A0A0);
        play.setIcon(track != null && !MusicPlayer.paused() ? MusicIcons.PAUSE : MusicIcons.PLAY);
        repeat.selected(MusicConfig.REPEAT.get());
    }

    private static String clock(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
