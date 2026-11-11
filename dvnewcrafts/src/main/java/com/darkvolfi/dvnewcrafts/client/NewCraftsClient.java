package com.darkvolfi.dvnewcrafts.client;

import com.darkvolfi.dvnewcrafts.DVNewCrafts;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.particle.FlameParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = DVNewCrafts.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NewCraftsClient {
    private NewCraftsClient() {}

    /** The green flame moves like the game's own torch flame. */
    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(DVNewCrafts.COPPER_FLAME, FlameParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(DVNewCrafts.RECIPE_EDITOR.get(), RecipeEditorScreen::new));
    }

    /**
     * The recipe screen opens from the DV menu when DV Lib is installed, and from the mods list's Config button
     * either way. Called from the mod constructor.
     */
    public static void registerRecipeScreen() {
        if (ModList.get().isLoaded("dvlib")) DVLibLink.register();
        // Registered after DV Lib's, which also sets a Config button factory, so this one wins.
        // Deprecated in Forge 47.4, but the replacement doesn't exist on NeoForge 1.20.1.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> RecipeNoticeScreen.open(parent)));
    }
}
