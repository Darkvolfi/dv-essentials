package com.darkvolfi.dvslots.client;

import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvlib.client.DVIconButton;
import com.darkvolfi.dvslots.DVSlots;
import com.darkvolfi.dvslots.network.OpenSlotsPacket;
import com.darkvolfi.dvslots.network.SlotsNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = DVSlots.MOD_ID, value = Dist.CLIENT)
public final class SlotsClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.dvslots.open",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, DVLib.KEY_CATEGORY);

    // Next to the recipe book button, below the crafting grid.
    private static final int BUTTON_X = 126;
    private static final int BUTTON_Y = 61;
    // Creative inventory tab: right of the player, clear of the armour and offhand slots.
    private static final int CREATIVE_X = 127;
    private static final int CREATIVE_Y = 19;

    /** A pair of elytra wings, 7×7. */
    private static final String[] WINGS = {"##...##", "###.###", "#######", "###.###", "##...##", "#.....#", "#.....#"};

    private static DVIconButton inventoryButton;
    private static DVIconButton creativeButton;

    private SlotsClient() {}

    private static boolean serverHasMod() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && SlotsNetwork.CHANNEL.isRemotePresent(connection.getConnection());
    }

    private static void requestOpen() {
        SlotsNetwork.CHANNEL.sendToServer(new OpenSlotsPacket());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (OPEN_KEY.consumeClick()) {
            if (serverHasMod() && Minecraft.getInstance().screen == null) requestOpen();
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!serverHasMod()) return;
        if (event.getScreen() instanceof InventoryScreen screen) {
            inventoryButton = new DVIconButton(screen.getGuiLeft() + BUTTON_X, screen.getGuiTop() + BUTTON_Y, 18, WINGS,
                    Component.translatable("container.dvslots"), SlotsClient::requestOpen);
            event.addListener(inventoryButton);
        } else if (event.getScreen() instanceof CreativeModeInventoryScreen screen) {
            creativeButton = new DVIconButton(screen.getGuiLeft() + CREATIVE_X, screen.getGuiTop() + CREATIVE_Y, 18, WINGS,
                    Component.translatable("container.dvslots"), SlotsClient::requestOpen);
            event.addListener(creativeButton);
        }
    }

    /** The recipe book shifts the inventory sideways; follow it. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof InventoryScreen screen && inventoryButton != null) {
            inventoryButton.setX(screen.getGuiLeft() + BUTTON_X);
        }
        // In creative only the inventory tab has room for it (the other tabs fill that space with items).
        if (event.getScreen() instanceof CreativeModeInventoryScreen screen && creativeButton != null) {
            creativeButton.visible = screen.isInventoryOpen();
        }
    }

    @Mod.EventBusSubscriber(modid = DVSlots.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(DVSlots.SLOTS_MENU.get(), SlotsScreen::new));
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_KEY);
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer = event.getSkin(skin);
                if (renderer != null) renderer.addLayer(new BackElytraLayer(renderer, event.getEntityModels()));
            }
        }
    }
}
