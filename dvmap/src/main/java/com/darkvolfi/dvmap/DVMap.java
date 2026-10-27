package com.darkvolfi.dvmap;

import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvmap.network.MapNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DVMap.MOD_ID)
public class DVMap {
    public static final String MOD_ID = "dvmap";

    public DVMap() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, MapConfig.SPEC);
        MapNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DVConfigScreen.register(MOD_ID, MapConfig.SPEC, null, null));
    }
}
