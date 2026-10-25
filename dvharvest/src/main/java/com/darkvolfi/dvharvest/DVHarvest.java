package com.darkvolfi.dvharvest;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod(DVHarvest.MOD_ID)
public class DVHarvest {
    public static final String MOD_ID = "dvharvest";

    /**
     * Carries no packets: it only lets the client see whether the server has the mod,
     * so a client-only install doesn't swallow right-clicks on crops.
     */
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
}
