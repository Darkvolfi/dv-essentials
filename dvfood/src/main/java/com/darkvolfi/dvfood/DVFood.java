package com.darkvolfi.dvfood;

import com.darkvolfi.dvfood.network.FoodNetwork;
import com.darkvolfi.dvlib.client.DVConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DVFood.MOD_ID)
public class DVFood {
    public static final String MOD_ID = "dvfood";

    public DVFood() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FoodConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DVConfigScreen.register(MOD_ID, FoodConfig.SPEC, null, null));
        FoodNetwork.register();
    }
}
