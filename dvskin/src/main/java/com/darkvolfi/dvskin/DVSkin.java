package com.darkvolfi.dvskin;

import com.darkvolfi.dvskin.client.SkinClient;
import com.darkvolfi.dvskin.network.SkinNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(DVSkin.MOD_ID)
public class DVSkin {
    public static final String MOD_ID = "dvskin";

    public DVSkin() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SkinServerConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            // The choice is global (every server and single player), so it lives in config/dvskin/, not the world.
            try {
                Files.createDirectories(folder());
            } catch (IOException ignored) {
                // Forge reports the config file problem itself.
            }
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SkinClientConfig.SPEC, MOD_ID + "/dvskin-client.toml");
            SkinClient.init();
        });
        SkinNetwork.register();
    }

    /** config/dvskin: the client's choice and its pictures. */
    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
    }
}
