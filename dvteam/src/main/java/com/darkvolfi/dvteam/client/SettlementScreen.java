package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanTypes.Perm;
import com.darkvolfi.dvteam.data.ClanTypes.Manage;
import com.darkvolfi.dvteam.network.ClanActionPacket;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** A settlement's name, and what allies and everyone else may do there. */
public class SettlementScreen extends Screen {
    private static final int PANEL_W = 300;

    private final Screen parent;
    private final ClanData.Settlement settlement;
    private String name;
    private int allyPerms;
    private int publicPerms;

    public SettlementScreen(Screen parent, ClanData.Settlement settlement) {
        super(Component.literal(settlement.name));
        this.parent = parent;
        this.settlement = settlement;
        this.name = settlement.name;
        this.allyPerms = settlement.allyPerms;
        this.publicPerms = settlement.publicPerms;
    }

    private int panelHeight() {
        return 92 + Perm.values().length * 20;
    }

    @Override
    protected void init() {
        int l = (width - PANEL_W) / 2;
        int t = (height - panelHeight()) / 2;
        boolean officer = ClientClanCache.can(Manage.CLAIM); // renaming goes with founding settlements
        boolean leader = ClientClanCache.can(Manage.SETTLEMENTS);

        EditBox nameBox = new EditBox(font, l + 10, t + 22, 190, 18, Component.translatable("dvteam.edit.name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        nameBox.setEditable(officer);
        addRenderableWidget(nameBox);

        Perm[] perms = Perm.values();
        for (int i = 0; i < perms.length; i++) {
            Perm p = perms[i];
            int y = t + 62 + i * 20;
            DVButton ally = addRenderableWidget(DVButton.builder(mark(p.in(allyPerms)), b -> {
                allyPerms ^= p.bit();
                b.setMessage(mark(p.in(allyPerms)));
            }).bounds(l + 170, y, 50, 18).build());
            DVButton pub = addRenderableWidget(DVButton.builder(mark(p.in(publicPerms)), b -> {
                publicPerms ^= p.bit();
                b.setMessage(mark(p.in(publicPerms)));
            }).bounds(l + 230, y, 50, 18).build());
            ally.active = leader;
            pub.active = leader;
        }

        int by = t + panelHeight() - 26;
        addRenderableWidget(DVButton.builder(CommonComponents.GUI_DONE, b -> save()).bounds(width / 2 - 104, by, 100, 20).build())
                .active = officer || leader;
        addRenderableWidget(DVButton.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 + 4, by, 100, 20).build());
    }

    private static Component mark(boolean on) {
        return Component.literal(on ? "✔" : "✘").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    private void save() {
        if (!name.trim().equals(settlement.name)) {
            TeamNetwork.CHANNEL.sendToServer(new ClanActionPacket(ClanActionPacket.Action.RENAME_SETTLEMENT,
                    settlement.id, name, 0, new CompoundTag()));
        }
        if (allyPerms != settlement.allyPerms || publicPerms != settlement.publicPerms) {
            TeamNetwork.CHANNEL.sendToServer(new ClanActionPacket(ClanActionPacket.Action.SET_PERMS,
                    settlement.id, "", allyPerms | publicPerms << 8, new CompoundTag()));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int l = (width - PANEL_W) / 2;
        int t = (height - panelHeight()) / 2;
        ClanData.Clan clan = ClientClanCache.clan(settlement.clanId);
        int color = clan == null ? 0xFFFFFF : clan.color;
        DVUi.panel(g, l, t, PANEL_W, panelHeight(), false);
        g.renderOutline(l, t, PANEL_W, panelHeight(), 0xFF000000 | color);
        g.drawString(font, Component.translatable(settlement.type.translationKey()).withStyle(ChatFormatting.GRAY), l + 10, t + 8, 0xFFFFFF);
        g.drawString(font, Component.translatable("dvteam.settlement.chunks", ClientClanCache.settlementChunks(settlement.id)),
                l + 210, t + 27, 0xAAAAAA);
        g.drawString(font, Component.translatable("dvteam.perm.header"), l + 10, t + 48, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("dvteam.perm.allies"), l + 195, t + 48, 0xCCCCCC);
        g.drawCenteredString(font, Component.translatable("dvteam.perm.everyone"), l + 255, t + 48, 0xCCCCCC);
        Perm[] perms = Perm.values();
        for (int i = 0; i < perms.length; i++) {
            g.drawString(font, Component.translatable(perms[i].translationKey()), l + 10, t + 67 + i * 20, 0xE0E0E0);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
