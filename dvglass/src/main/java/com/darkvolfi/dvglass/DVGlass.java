package com.darkvolfi.dvglass;

import com.darkvolfi.dvglass.client.GlassClient;
import com.darkvolfi.dvlib.client.DVConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DVGlass.MOD_ID)
public class DVGlass {
    public static final String MOD_ID = "dvglass";

    public DVGlass() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, GlassConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DVConfigScreen.register(MOD_ID, GlassConfig.SPEC, null, GlassClient::redrawWorld));
    }
}
