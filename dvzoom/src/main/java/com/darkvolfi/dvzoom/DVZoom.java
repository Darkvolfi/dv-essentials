package com.darkvolfi.dvzoom;

import com.darkvolfi.dvlib.client.DVConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DVZoom.MOD_ID)
public class DVZoom {
    public static final String MOD_ID = "dvzoom";

    public DVZoom() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ZoomConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DVConfigScreen.register(MOD_ID, ZoomConfig.SPEC, null, null));
    }
}
