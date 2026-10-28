package com.darkvolfi.dvlib.client;

import com.darkvolfi.dvlib.DVLib;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * "DV Mods" menu, opened from the title and pause screens: every DV mod as its logo. Installed ones open their
 * settings; missing ones are dark silhouettes with a lock that lead to their download page. Names and details
 * show on hover. The search box dims the mods without a matching option and opens the rest already filtered.
 */
@Mod.EventBusSubscriber(modid = DVLib.MOD_ID, value = Dist.CLIENT)
public class DVMenuScreen extends Screen {
    private static final int TILE = 40;
    private static final int GAP = 6;
    private static final int COLS = 4;
    private static final int PAD = 12;
    private static final int GRID_TOP = 50;

    private final Screen parent;
    private final List<ModTile> tiles = new ArrayList<>();
    private String query = "";

    public DVMenuScreen(Screen parent) {
        super(Component.translatable("dvlib.menu.title"));
        this.parent = parent;
    }

    private int panelWidth() {
        return COLS * TILE + (COLS - 1) * GAP + 2 * PAD;
    }

    private int panelHeight() {
        int rows = (DVMods.KNOWN.size() + COLS - 1) / COLS;
        return GRID_TOP + rows * (TILE + GAP) - GAP + PAD + 12;
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    @Override
    protected void init() {
        int l = left();
        int t = top();
        int w = panelWidth();

        addRenderableWidget(new DVIconButton(l + w - 20, t + 6, 14, DVUi.CLOSE, CommonComponents.GUI_DONE, this::onClose));
        addRenderableWidget(new DVIconButton(l + w - 38, t + 6, 14, DVUi.PROFILES, Component.translatable("dvlib.profiles.title"),
                () -> Minecraft.getInstance().setScreen(new DVProfileScreen(this))));

        EditBox search = addRenderableWidget(new EditBox(font, l + PAD + 14, t + 26, w - 2 * PAD - 14, 16, Component.translatable("dvlib.search")));
        search.setHint(Component.translatable("dvlib.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            tiles.forEach(ModTile::refresh);
        });

        tiles.clear();
        for (int i = 0; i < DVMods.KNOWN.size(); i++) {
            int x = l + PAD + (i % COLS) * (TILE + GAP);
            int y = t + GRID_TOP + (i / COLS) * (TILE + GAP);
            tiles.add(addRenderableWidget(new ModTile(x, y, DVMods.KNOWN.get(i))));
        }
    }

    /** One mod's logo; coloured when installed, a dark silhouette with a lock when not. */
    private final class ModTile extends AbstractButton {
        private final String modId;
        private final DVMods.Mod mod;
        private final boolean installed;
        private final Component update;
        private final ResourceLocation logo;

        ModTile(int x, int y, String modId) {
            super(x, y, TILE, TILE, Component.translatable("dvlib.mod." + modId + ".name"));
            this.modId = modId;
            this.mod = DVMods.get(modId);
            this.installed = ModList.get().isLoaded(modId);
            this.update = installed ? updateText(modId) : null;
            this.logo = new ResourceLocation(DVLib.MOD_ID, "textures/gui/mods/" + modId + ".png");
            refresh();
        }

        /** Dimmed while a search is typed that none of its options match. */
        private boolean matches() {
            return query.isEmpty() || (mod != null && mod.matches(query));
        }

        void refresh() {
            MutableComponent tip = getMessage().copy().withStyle(ChatFormatting.BOLD)
                    .append(Component.literal("\n"))
                    .append(Component.translatable("dvlib.mod." + modId + ".desc").withStyle(s -> s.withBold(false).withColor(ChatFormatting.GRAY)));
            if (!installed) {
                String page = DVMods.page(modId);
                tip.append(Component.literal("\n")).append(Component.translatable(page == null ? "dvlib.menu.soon" : "dvlib.menu.not_installed")
                        .withStyle(s -> s.withBold(false).withColor(ChatFormatting.GOLD)));
            } else if (update != null) {
                tip.append(Component.literal("\n")).append(update.copy().withStyle(s -> s.withBold(false).withColor(ChatFormatting.GREEN)));
            }
            setTooltip(Tooltip.create(tip));
            active = installed ? mod != null && matches() : DVMods.page(modId) != null;
        }

        @Override
        public void onPress() {
            if (mod != null) {
                Minecraft.getInstance().setScreen(new DVConfigScreen(DVMenuScreen.this, modId, mod.clientSpec(), mod.serverSpec(), mod.onSave(), query));
            } else if (DVMods.page(modId) != null) {
                ConfirmLinkScreen.confirmLinkNow(DVMods.page(modId), DVMenuScreen.this, true);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            DVUi.panel(g, getX(), getY(), width, height, isHovered() && (installed || active));
            float shade = !installed ? 0.08f : matches() ? 1.0f : 0.3f;
            RenderSystem.setShaderColor(shade, shade, shade, 1.0f);
            g.blit(logo, getX() + 4, getY() + 4, 0, 0, 32, 32, 32, 32);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            if (!installed) DVUi.icon(g, DVUi.LOCK, getX() + width - 8, getY() + height - 8, 1, 0xFFB0A8C0);
            if (update != null) DVUi.icon(g, DVUi.UPDATE, getX() + width - 8, getY() + 8, 1, 0xFF7EE787);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
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
        int l = left();
        int t = top();
        DVUi.panel(g, l, t, panelWidth(), panelHeight(), false);
        g.drawString(font, title, l + PAD, t + 9, 0xFFE6E6F0);
        DVUi.icon(g, DVUi.SEARCH, l + PAD + 5, t + 34, 1, 0xFF8A80A8);
        if (!query.isEmpty() && tiles.stream().noneMatch(ModTile::matches)) {
            g.drawCenteredString(font, Component.translatable("dvlib.search.none"), l + panelWidth() / 2, t + panelHeight() - 14, 0xA0A0A0);
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
