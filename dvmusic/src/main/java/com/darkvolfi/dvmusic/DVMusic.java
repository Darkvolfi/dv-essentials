package com.darkvolfi.dvmusic;

import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvlib.client.DVMods;
import com.darkvolfi.dvmusic.client.MusicScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DVMusic.MOD_ID)
public class DVMusic {
    public static final String MOD_ID = "dvmusic";

    public DVMusic() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, MusicConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            DVConfigScreen.register(MOD_ID, MusicConfig.SPEC, null, null);
            DVMods.registerScreen(MOD_ID, MusicScreen::new);
        });
    }
}
