package com.darkvolfi.dvnewcrafts.client;

import com.darkvolfi.dvnewcrafts.DVNewCrafts;
import net.minecraft.client.particle.FlameParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DVNewCrafts.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NewCraftsClient {
    private NewCraftsClient() {}

    /** The green flame moves like the game's own torch flame. */
    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(DVNewCrafts.COPPER_FLAME, FlameParticle.Provider::new);
    }
}
