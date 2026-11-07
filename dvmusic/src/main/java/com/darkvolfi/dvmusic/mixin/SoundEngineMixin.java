package com.darkvolfi.dvmusic.mixin;

import com.darkvolfi.dvmusic.client.MusicPlayer;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a pausing screen (pause menu, world map...) closes, the game resumes every sound channel. A song the
 * player paused must stay paused, so it is paused again right away; both commands run in order on the sound
 * thread, so not a moment of it is heard.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Inject(method = "resume", at = @At("TAIL"))
    private void dvmusic$keepPaused(CallbackInfo ci) {
        MusicPlayer.afterResume();
    }
}
