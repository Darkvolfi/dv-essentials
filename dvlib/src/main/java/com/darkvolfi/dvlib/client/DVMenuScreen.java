package com.darkvolfi.dvlib.client;

import com.darkvolfi.dvlib.DVLib;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * "DV Mods" menu, opened from the title screen: one button per installed DV mod, leading to its settings.
 * The search box keeps only the mods that have a matching option and opens them already filtered.
 */
@Mod.EventBusSubscriber(modid = DVLib.MOD_ID, value = Dist.CLIENT)
public class DVMenuScreen extends Screen {
    private final Screen parent;
    private final List<Button> modButtons = new ArrayList<>();
    private String query = "";

    public DVMenuScreen(Screen parent) {
        super(Component.translatable("dvlib.menu.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        EditBox search = addRenderableWidget(new EditBox(font, width / 2 - 155, 30, 310, 18, Component.translatable("dvlib.search")));
        search.setHint(Component.translatable("dvlib.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            placeModButtons();
        });

        modButtons.clear();
        for (DVMods.Mod mod : DVMods.all()) {
            MutableComponent name = Component.literal(mod.displayName());
            Component update = updateText(mod.modId());
            if (update != null) name.append(Component.literal(" ↑").withStyle(ChatFormatting.GREEN));
            Button button = addRenderableWidget(Button.builder(name, b -> Minecraft.getInstance().setScreen(
                            new DVConfigScreen(this, mod.modId(), mod.clientSpec(), mod.serverSpec(), mod.onSave(), query)))
                    .bounds(0, 0, 150, 20)
                    .tooltip(update == null ? null : Tooltip.create(update))
                    .build());
            modButtons.add(button);
        }
        placeModButtons();

        addRenderableWidget(Button.builder(Component.translatable("dvlib.profiles.title"),
                        b -> Minecraft.getInstance().setScreen(new DVProfileScreen(this)))
                .bounds(width / 2 - 155, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 + 5, height - 28, 150, 20).build());
    }

    /** Two columns of the mods that match the search. */
    private void placeModButtons() {
        int i = 0;
        int index = 0;
        for (DVMods.Mod mod : DVMods.all()) {
            Button button = modButtons.get(index++);
            button.visible = mod.matches(query);
            if (!button.visible) continue;
            button.setX((i % 2 == 0) ? width / 2 - 155 : width / 2 + 5);
            button.setY(56 + (i / 2) * 24);
            i++;
        }
    }

    /** "Update available: 0.2.0" when Forge's version check found a newer release, otherwise null. */
    private static Component updateText(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> {
            VersionChecker.CheckResult result = VersionChecker.getResult(c.getModInfo());
            boolean outdated = result.status() == VersionChecker.Status.OUTDATED
                    || result.status() == VersionChecker.Status.BETA_OUTDATED;
            return outdated ? (Component) Component.translatable("dvlib.menu.update", String.valueOf(result.target())) : null;
        }).orElse(null);
    }

    private static boolean anyUpdate() {
        for (DVMods.Mod mod : DVMods.all()) {
            if (updateText(mod.modId()) != null) return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        if (DVMods.all().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("dvlib.menu.empty"), width / 2, 62, 0xA0A0A0);
        } else if (modButtons.stream().noneMatch(b -> b.visible)) {
            g.drawCenteredString(font, Component.translatable("dvlib.search.none"), width / 2, 62, 0xA0A0A0);
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
        boolean update = anyUpdate();
        Component tooltip = Component.translatable("dvlib.menu.title");
        if (update) tooltip = tooltip.copy().append("\n").append(Component.translatable("dvlib.menu.updates").withStyle(ChatFormatting.GREEN));
        return Button.builder(Component.literal("DV").withStyle(update ? ChatFormatting.GREEN : ChatFormatting.RESET),
                        b -> Minecraft.getInstance().setScreen(new DVMenuScreen(parent)))
                .bounds(x, y, 20, 20)
                .tooltip(Tooltip.create(tooltip))
                .build();
    }
}
