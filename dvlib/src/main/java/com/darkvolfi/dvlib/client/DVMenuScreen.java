package com.darkvolfi.dvlib.client;

import com.darkvolfi.dvlib.DVLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** "DV Mods" menu, opened from the title screen: one button per installed DV mod, leading to its settings. */
@Mod.EventBusSubscriber(modid = DVLib.MOD_ID, value = Dist.CLIENT)
public class DVMenuScreen extends Screen {
    private static final Map<String, Function<Screen, Screen>> MODS = new TreeMap<>();

    private final Screen parent;

    static void add(String modId, Function<Screen, Screen> configScreen) {
        MODS.put(modId, configScreen);
    }

    public DVMenuScreen(Screen parent) {
        super(Component.translatable("dvlib.menu.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int i = 0;
        for (Map.Entry<String, Function<Screen, Screen>> mod : MODS.entrySet()) {
            String name = ModList.get().getModContainerById(mod.getKey())
                    .map(c -> c.getModInfo().getDisplayName()).orElse(mod.getKey());
            int x = (i % 2 == 0) ? width / 2 - 155 : width / 2 + 5;
            int y = 40 + (i / 2) * 24;
            addRenderableWidget(Button.builder(Component.literal(name),
                            b -> Minecraft.getInstance().setScreen(mod.getValue().apply(this)))
                    .bounds(x, y, 150, 20).build());
            i++;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        if (MODS.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("dvlib.menu.empty"), width / 2, 50, 0xA0A0A0);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /**
     * Small "DV" button: on the title screen to the right of the accessibility button,
     * in the pause menu to the left of "Options...".
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof TitleScreen) {
            int y = screen.height / 4 + 48 + 84; // same row as the language and accessibility buttons
            event.addListener(menuButton(screen, screen.width / 2 + 128, y));
        } else if (screen instanceof PauseScreen) {
            for (GuiEventListener widget : event.getListenersList()) {
                if (widget instanceof Button options && options.getMessage().getContents() instanceof TranslatableContents text
                        && text.getKey().equals("menu.options")) {
                    event.addListener(menuButton(screen, options.getX() - 24, options.getY()));
                    return;
                }
            }
        }
    }

    private static Button menuButton(Screen parent, int x, int y) {
        return Button.builder(Component.literal("DV"), b -> Minecraft.getInstance().setScreen(new DVMenuScreen(parent)))
                .bounds(x, y, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("dvlib.menu.title")))
                .build();
    }
}
