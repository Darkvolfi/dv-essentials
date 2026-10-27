package com.darkvolfi.dvteam;

import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvteam.client.TeamClient;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DVTeam.MOD_ID)
public class DVTeam {
    public static final String MOD_ID = "dvteam";

    public DVTeam() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TeamConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TeamConfig.SERVER_SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            DVConfigScreen.register(MOD_ID, TeamConfig.CLIENT_SPEC, TeamConfig.SERVER_SPEC, null);
            TeamClient.init();
        });
        TeamNetwork.register();
    }
}
