package com.darkvolfi.dvsouls.client;

import com.darkvolfi.dvsouls.DVSouls;
import com.darkvolfi.dvsouls.Soul;
import com.darkvolfi.dvsouls.SoulDistiller;
import com.darkvolfi.dvsouls.SoulFuser;
import com.darkvolfi.dvsouls.charm.EssenceItem;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = DVSouls.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SoulsClient {
    /** The pale soul-fire blue, for creatures without a spawn egg. */
    private static final int SOUL_COLOR = 0x7FD8E0;

    private SoulsClient() {}

    /** The soul in a jar (layers 1, 2) and the gem of a collar shard (layer 1) take the colours of its creature's spawn egg: body, then spots. */
    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 0 ? -1 : 0xFF000000 | soulColor(stack, layer - 1),
                DVSouls.FILLED_SOUL_JAR.get(), DVSouls.COLLAR_SHARD.get());
        // An essence's glow (layer 1) takes its creature's egg body colour.
        event.register((stack, layer) -> layer == 0 ? -1 : 0xFF000000 | eggColor(EssenceItem.creature(stack), 0), DVSouls.SOUL_ESSENCE.get());
    }

    private static int soulColor(ItemStack stack, int part) {
        return eggColor(Soul.type(Soul.get(stack)).orElse(null), part);
    }

    private static int eggColor(@Nullable EntityType<?> type, int part) {
        SpawnEggItem egg = type == null ? null : ForgeSpawnEggItem.fromEntityType(type);
        return egg == null ? SOUL_COLOR : egg.getColor(part);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        HybridRenderers.registerLayers(event);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        HybridRenderers.registerRenderers(event);
    }

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(DVSouls.SOUL_FURNACE_MENU.get(), SoulScreens.FurnaceScreen::new);
            MenuScreens.register(DVSouls.SOUL_FUSER_MENU.get(), SoulScreens.MachineScreen<SoulFuser.Menu>::new);
            MenuScreens.register(DVSouls.SOUL_DISTILLER_MENU.get(), SoulScreens.MachineScreen<SoulDistiller.Menu>::new);
        });
    }
}
