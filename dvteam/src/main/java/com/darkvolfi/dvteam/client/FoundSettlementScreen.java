package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVChoicePopup;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanTypes.SettlementType;
import com.darkvolfi.dvteam.network.ChunkClaimActionPacket;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Asked when a claim on the map founds a new settlement: its name and, unless it's the capital,
 * whether it's a village or a trade center.
 */
public class FoundSettlementScreen extends Screen {
    private static final int PANEL_W = 240;
    private static final int PANEL_H = 110;

    private final Screen parent;
    private final String dimension;
    private final int chunkX;
    private final int chunkZ;
    private final boolean capital;
    private String name = "";
    private SettlementType type = SettlementType.VILLAGE;
    private DVButton foundButton;

    public FoundSettlementScreen(Screen parent, String dimension, int chunkX, int chunkZ) {
        super(Component.translatable("dvteam.found.title"));
        this.parent = parent;
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        ClanData.Clan mine = ClientClanCache.myClan();
        this.capital = mine != null && ClientClanCache.settlementCount(mine.id, SettlementType.CAPITAL) == 0;
    }

    private Component typeLabel() {
        return Component.translatable("dvteam.found.type").append(": ").append(Component.translatable(type.translationKey()));
    }

    @Override
    protected void init() {
        int l = (width - PANEL_W) / 2;
        int t = (height - PANEL_H) / 2;
        EditBox nameBox = new EditBox(font, l + 10, t + 24, PANEL_W - 20, 18, Component.translatable("dvteam.edit.name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(name);
        nameBox.setResponder(v -> {
            name = v;
            foundButton.active = v.trim().length() >= 3;
        });
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        if (!capital) {
            addRenderableWidget(DVButton.builder(typeLabel(), b -> {
                List<DVChoicePopup.Option<SettlementType>> options = List.of(
                        new DVChoicePopup.Option<>(SettlementType.VILLAGE, Component.translatable(SettlementType.VILLAGE.translationKey())),
                        new DVChoicePopup.Option<>(SettlementType.TRADE, Component.translatable(SettlementType.TRADE.translationKey())));
                DVChoicePopup.open(b, options, type, picked -> {
                    type = picked;
                    b.setMessage(typeLabel());
                });
            }).bounds(l + 10, t + 48, PANEL_W - 20, 18).build().menu());
        }

        foundButton = addRenderableWidget(DVButton.builder(Component.translatable("dvteam.edit.found"), b -> {
            TeamNetwork.CHANNEL.sendToServer(new ChunkClaimActionPacket(ChunkClaimActionPacket.Action.CLAIM,
                    dimension, chunkX, chunkZ, name.trim(), type));
            onClose();
        }).bounds(width / 2 - 104, t + PANEL_H - 26, 100, 20).build());
        foundButton.active = name.trim().length() >= 3;
        addRenderableWidget(DVButton.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 + 4, t + PANEL_H - 26, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int l = (width - PANEL_W) / 2;
        int t = (height - PANEL_H) / 2;
        ClanData.Clan mine = ClientClanCache.myClan();
        DVUi.panel(g, l, t, PANEL_W, PANEL_H, false);
        g.renderOutline(l, t, PANEL_W, PANEL_H, 0xFF000000 | (mine == null ? 0xFFFFFF : mine.color));
        Component heading = capital ? Component.translatable("dvteam.found.capital") : title;
        g.drawCenteredString(font, heading, width / 2, t + 9, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
