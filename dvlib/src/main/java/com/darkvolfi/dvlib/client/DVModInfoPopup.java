package com.darkvolfi.dvlib.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.fml.ModList;

import java.util.List;

/** Shown when a DV mod without settings is clicked in the DV menu: what it does, its version and its page. */
final class DVModInfoPopup extends DVPopup {
    private static final int WIDTH = 200;
    private static final int PAD = 6;
    private static final int LINE = 10;

    private final String modId;
    private final String page;
    private List<FormattedCharSequence> desc = List.of();

    DVModInfoPopup(AbstractWidget anchor, String modId) {
        super(anchor);
        this.modId = modId;
        this.page = DVMods.page(modId);
    }

    @Override
    protected int[] panelSize() {
        desc = font.split(Component.translatable("dvlib.mod." + modId + ".desc"), WIDTH - 2 * PAD);
        // Title, description, version, "no settings" and the optional page button.
        int h = PAD + LINE + 4 + desc.size() * LINE + 4 + 2 * LINE + (page != null ? 4 + 20 : 0) + PAD;
        return new int[] {WIDTH, h};
    }

    @Override
    protected void initPanel() {
        if (page != null) {
            addRenderableWidget(new DVButton(panelX + PAD, panelY + panelH - PAD - 20, panelW - 2 * PAD, 20,
                    Component.translatable("dvlib.menu.open_page"), b -> ConfirmLinkScreen.confirmLinkNow(page, parent, true)));
        }
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX + PAD;
        int y = panelY + PAD;
        g.drawString(font, Component.translatable("dvlib.mod." + modId + ".name").withStyle(ChatFormatting.BOLD), x, y, 0xFFFFFFFF);
        y += LINE + 4;
        for (FormattedCharSequence line : desc) {
            g.drawString(font, line, x, y, 0xFFB0A8C0);
            y += LINE;
        }
        y += 4;
        String version = ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        g.drawString(font, Component.translatable("dvlib.menu.version", version), x, y, 0xFFB0A8C0);
        g.drawString(font, Component.translatable("dvlib.menu.no_settings"), x, y + LINE, 0xFF8A8296);
    }
}
