package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvmusic.DVMusic;
import com.darkvolfi.dvmusic.MusicConfig;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DV Music's own music player, in place of vanilla's: it picks the songs (Minecraft's, the player's or both,
 * for the current situation), keeps the gaps, and can play a chosen song, skip, go back and pause.
 * Vanilla's music manager keeps running but every song it starts is muted.
 */
@Mod.EventBusSubscriber(modid = DVMusic.MOD_ID, value = Dist.CLIENT)
public final class MusicPlayer {
    private static final Logger LOG = LogUtils.getLogger();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int HISTORY = 20;

    private static Track current;
    private static TrackInstance playing;
    private static boolean paused;
    private static int playedTicks;
    private static int gapTicks = 40;
    private static final Deque<Track> history = new ArrayDeque<>();
    /** A jukebox record playing nearby: background music waits for it. */
    private static SoundInstance record;
    private static boolean pauseBroken;

    private MusicPlayer() {}

    public static Track current() {
        return current;
    }

    public static boolean paused() {
        return paused;
    }

    public static int playedSeconds() {
        return playedTicks / 20;
    }

    /** Length of the current song in seconds, or -1 while unknown. */
    public static int lengthSeconds() {
        return current == null ? -1 : OggLength.seconds(current);
    }

    public static Situation situation() {
        return Situation.of(Minecraft.getInstance().getSituationalMusic());
    }

    /** Songs that may play now, for the mode and the situation. */
    public static List<Track> pool() {
        Situation situation = situation();
        List<Track> pool = new ArrayList<>();
        MusicConfig.Mode mode = MusicConfig.MODE.get();
        if (mode != MusicConfig.Mode.MINECRAFT) {
            for (Track t : TrackLibrary.custom()) if (t.playsIn(situation)) pool.add(t);
        }
        if (mode != MusicConfig.Mode.MINE || pool.isEmpty()) {
            for (Track t : TrackLibrary.vanilla()) if (t.playsIn(situation)) pool.add(t);
        }
        return pool;
    }

    public static void play(Track track) {
        Minecraft mc = Minecraft.getInstance();
        stopSound();
        if (current != null && current != track) {
            history.push(current);
            while (history.size() > HISTORY) history.removeLast();
        }
        current = track;
        paused = false;
        playedTicks = 0;
        playing = new TrackInstance(track);
        mc.getSoundManager().play(playing);
        if (MusicConfig.SHOW_TOAST.get() && mc.getSoundManager().isActive(playing)) {
            NowPlayingToast.show(track);
        }
    }

    public static void next() {
        Track next = pick();
        if (next != null) play(next);
    }

    /** Back to the song before when the current one just started (6 seconds), otherwise restarts it. */
    public static void previous() {
        if (current != null && (playedTicks > 6 * 20 || history.isEmpty())) {
            replay();
        } else if (!history.isEmpty()) {
            current = null; // don't push the song we leave onto the history
            play(history.pop());
        }
    }

    /** The current song again from the start. */
    private static void replay() {
        Track track = current;
        current = null;
        play(track);
    }

    public static void togglePause() {
        if (current == null) {
            next();
            return;
        }
        paused = !paused;
        if (playing == null) {
            if (!paused) replay(); // paused by stopping (see onChannel)
        } else if (!onChannel(paused ? Channel::pause : Channel::unpause) && paused) {
            stopSound();
        }
    }

    /** A random song for now, not the one just played. */
    private static Track pick() {
        List<Track> pool = pool();
        if (pool.size() > 1 && current != null) pool.remove(current);
        return pool.isEmpty() ? null : pool.get(RANDOM.nextInt(pool.size()));
    }

    private static void stopSound() {
        if (playing != null) Minecraft.getInstance().getSoundManager().stop(playing);
        playing = null;
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getOriginalSound();
        if (sound == null) return;
        if (sound.getSource() == SoundSource.MUSIC && !(sound instanceof TrackInstance)) {
            event.setSound(null); // vanilla's pick; DV Music chooses the songs
        } else if (sound.getSource() == SoundSource.RECORDS) {
            record = sound;
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(sound.getLocation());
            RecordItem disc = soundEvent == null ? null : RecordItem.getBySound(soundEvent);
            if (disc != null && MusicConfig.SHOW_TOAST.get()) NowPlayingToast.showRecord(disc.getDisplayName());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !MusicConfig.SPEC.isLoaded()) return;
        Minecraft mc = Minecraft.getInstance();
        SoundManager sounds = mc.getSoundManager();

        if (record != null) {
            if (sounds.isActive(record)) {
                if (playing != null) {
                    stopSound();
                    current = null;
                }
                gapTicks = 60;
                return;
            }
            record = null;
        }
        if (playing != null) {
            if (sounds.isActive(playing)) {
                if (paused) {
                    // The game unpauses every channel when its own pause menu closes; keep ours paused.
                    if (mc.level == null || mc.level.getGameTime() % 10 == 0) onChannel(Channel::pause);
                    return;
                }
                if (mc.isPaused()) {
                    // A paused game pauses every sound and stops feeding streams. The music player keeps playing,
                    // so a song can be changed from the pause menu; the game won't notice it ending, so we do.
                    onChannel(channel -> {
                        channel.unpause();
                        channel.updateStream();
                    });
                    int length = lengthSeconds();
                    if (length > 0 && playedTicks > (length + 1) * 20) stopSound();
                }
                playedTicks++;
                Music music = mc.getSituationalMusic();
                if (music.replaceCurrentMusic() && !current.playsIn(Situation.of(music)) && !playingByChoice()) next();
                return;
            }
            playing = null;
            if (MusicConfig.REPEAT.get() && current != null) {
                replay();
                return;
            }
            current = null;
            gapTicks = gap(mc.getSituationalMusic());
        }
        if (paused) return;
        if (gapTicks > 0) {
            gapTicks--;
            return;
        }
        Track next = pick();
        if (next != null) play(next);
        else gapTicks = 200;
    }

    /** A song the player picked from the list keeps playing when the situation changes. */
    private static boolean playingByChoice() {
        return chosen;
    }

    private static boolean chosen;

    /** Plays a song the player picked; it isn't cut off by a situation change. */
    public static void choose(Track track) {
        play(track);
        chosen = true;
    }

    private static int gap(Music music) {
        chosen = false;
        return switch (MusicConfig.GAPS.get()) {
            case MINECRAFT -> music.getMinDelay() + RANDOM.nextInt(Math.max(1, music.getMaxDelay() - music.getMinDelay()));
            case SHORT -> 100 + RANDOM.nextInt(500);
            case NONE -> 20;
        };
    }

    /** Runs {@code action} on the playing song's sound channel (pausing one song needs it). */
    private static boolean onChannel(Consumer<Channel> action) {
        if (playing == null || pauseBroken) return false;
        try {
            SoundEngine engine = ObfuscationReflectionHelper.getPrivateValue(SoundManager.class, Minecraft.getInstance().getSoundManager(), "f_120349_");
            Map<SoundInstance, ChannelAccess.ChannelHandle> channels = ObfuscationReflectionHelper.getPrivateValue(SoundEngine.class, engine, "f_120226_");
            ChannelAccess.ChannelHandle handle = channels == null ? null : channels.get(playing);
            if (handle == null) return false;
            handle.execute(action);
            return true;
        } catch (RuntimeException e) {
            pauseBroken = true;
            LOG.warn("[DV Music] Pausing a single song is not possible here, pausing stops it instead: {}", e.toString());
            return false;
        }
    }
}
