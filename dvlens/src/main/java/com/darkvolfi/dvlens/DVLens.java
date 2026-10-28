package com.darkvolfi.dvlens;

import com.darkvolfi.dvlens.network.LensNetwork;
import com.darkvolfi.dvlib.client.DVConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DVLens.MOD_ID)
public class DVLens {
    public static final String MOD_ID = "dvlens";

    public DVLens() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LensConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, LensConfig.SERVER_SPEC);
        LensNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                DVConfigScreen.register(MOD_ID, LensConfig.SPEC, LensConfig.SERVER_SPEC, null));
    }
}
