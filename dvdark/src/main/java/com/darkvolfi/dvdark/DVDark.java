package com.darkvolfi.dvdark;

import com.darkvolfi.dvdark.client.DarkClient;
import com.darkvolfi.dvlib.client.DVConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DVDark.MOD_ID)
public class DVDark {
    public static final String MOD_ID = "dvdark";

    public DVDark() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, DarkConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                DVConfigScreen.register(MOD_ID, DarkConfig.SPEC, null, DarkClient::applyIfChanged));
    }
}
