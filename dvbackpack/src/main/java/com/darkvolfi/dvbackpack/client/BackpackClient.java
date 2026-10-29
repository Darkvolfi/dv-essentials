package com.darkvolfi.dvbackpack.client;

import com.darkvolfi.dvbackpack.BackpackBlock;
import com.darkvolfi.dvbackpack.BackpackBlockEntity;
import com.darkvolfi.dvbackpack.BackpackItem;
import com.darkvolfi.dvbackpack.BackpackTier;
import com.darkvolfi.dvbackpack.DVBackpack;
import com.darkvolfi.dvbackpack.SleepingBagBlockEntity;
import com.darkvolfi.dvbackpack.network.BackpackNetwork;
import com.darkvolfi.dvbackpack.network.OpenBackpackPacket;
import com.darkvolfi.dvlib.DVLib;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = DVBackpack.MOD_ID, value = Dist.CLIENT)
public final class BackpackClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.dvbackpack.open",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, DVLib.KEY_CATEGORY);

    private BackpackClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            var connection = mc.getConnection();
            if (mc.screen == null && connection != null && BackpackNetwork.CHANNEL.isRemotePresent(connection.getConnection())) {
                BackpackNetwork.CHANNEL.sendToServer(new OpenBackpackPacket());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = DVBackpack.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(DVBackpack.BACKPACK_MENU.get(), BackpackScreen::new));
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_KEY);
        }

        /** The bag is leather; layer 1 (straps and buckles) takes the tier's colour, layer 2 (the rolled sleeping bag) its own. */
        @SubscribeEvent
        public static void onItemColors(RegisterColorHandlersEvent.Item event) {
            for (BackpackTier tier : BackpackTier.values()) {
                event.register((stack, layer) -> switch (layer) {
                    case 1 -> 0xFF000000 | tier.color();
                    case 2 -> 0xFF000000 | BackpackItem.bagColor(stack);
                    default -> -1;
                }, DVBackpack.BACKPACKS.get(tier).get());
            }
        }

        /** A set-down backpack: tint 0 is its tier's colour, tint 1 its rolled sleeping bag; a laid-out bag takes its own colour. */
        @SubscribeEvent
        public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
            event.register((state, level, pos, layer) -> {
                if (layer == 0) return BackpackTier.values()[state.getValue(BackpackBlock.TIER)].color();
                if (layer == 1 && level != null && pos != null && level.getBlockEntity(pos) instanceof BackpackBlockEntity entity) {
                    return BackpackItem.bagColor(entity.backpack());
                }
                return layer == 1 ? BackpackItem.DEFAULT_BAG_COLOR : -1;
            }, DVBackpack.BACKPACK_BLOCK.get());
            event.register((state, level, pos, layer) -> {
                if (layer != 0) return -1;
                if (level != null && pos != null && level.getBlockEntity(pos) instanceof SleepingBagBlockEntity bag) return bag.color();
                return BackpackItem.DEFAULT_BAG_COLOR;
            }, DVBackpack.SLEEPING_BAG.get());
        }

        @SubscribeEvent
        public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
            event.register(BackpackLayer.WORN_MODEL);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(DVBackpack.BACKPACK_ENTITY.get(), BackpackTanksRenderer::new);
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer = event.getSkin(skin);
                if (renderer != null) renderer.addLayer(new BackpackLayer(renderer));
            }
        }
    }
}
