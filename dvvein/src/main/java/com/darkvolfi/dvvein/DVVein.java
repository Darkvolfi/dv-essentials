package com.darkvolfi.dvvein;

import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvvein.network.VeinNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DVVein.MOD_ID)
public class DVVein {
    public static final String MOD_ID = "dvvein";

    public DVVein() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, VeinConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, VeinConfig.CLIENT_SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                DVConfigScreen.register(MOD_ID, VeinConfig.CLIENT_SPEC, VeinConfig.SERVER_SPEC, null));
        VeinNetwork.register();
    }
}
