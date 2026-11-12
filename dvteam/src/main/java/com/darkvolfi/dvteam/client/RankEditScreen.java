package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVColorButton;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanRank;
import com.darkvolfi.dvteam.data.ClanTypes.Manage;
import com.darkvolfi.dvteam.data.ClanTypes.Perm;
import com.darkvolfi.dvteam.network.ClanActionPacket;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * A rank's name, colour, management rights and rights on the clan's own land. The leader edits (or adds) a rank;
 * everyone else sees the same screen read-only.
 */
public class RankEditScreen extends Screen {
    private static final int PANEL_W = 380;
    private static final int ROWS_TOP = 68;
    private static final int ROW_H = 20;
    private static final int TOGGLE_W = 22;
    /** Label column widths; long labels are cut to fit. */
    private static final int LABEL_W = 140;

    private final Screen parent;
    /** Null when adding a rank. */
    private final ClanRank rank;
    private final boolean editable;
    private final String originalName;
    private String name;
    private int color;
    private int manage;
    private int land;

    public RankEditScreen(Screen parent, ClanData.Clan clan, ClanRank rank, boolean editable) {
        super(Component.translatable(rank == null ? "dvteam.ranks.add_title" : editable ? "dvteam.ranks.edit_title" : "dvteam.ranks.view_title"));
        this.parent = parent;
        this.rank = rank;
        this.editable = editable;
        if (rank != null) {
            originalName = rank.displayName().getString();
            name = originalName;
            color = rank.color;
            manage = rank.manage;
            land = rank.land;
        } else {
            // A new rank goes to the bottom; it starts with the land rights of the current lowest rank.
            originalName = "";
            name = "";
            color = 0xAAAAAA;
            manage = 0;
            land = clan.lowest().land;
        }
    }

    private int rowCount() {
        return Math.max(Manage.values().length, Perm.values().length);
    }

    private int panelHeight() {
        return ROWS_TOP + rowCount() * ROW_H + 34;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    @Override
    protected void init() {
        int l = left();
        int t = top();

        EditBox nameBox = new EditBox(font, l + 10, t + 34, 200, 18, Component.translatable("dvteam.edit.name"));
        nameBox.setMaxLength(ClanRank.NAME_MAX);
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        nameBox.setEditable(editable);
        addRenderableWidget(nameBox);
        addRenderableWidget(new DVColorButton(l + 220, t + 34, 80, 18, color, c -> color = c)).active = editable;

        Manage[] rights = Manage.values();
        for (int i = 0; i < rights.length; i++) {
            Manage m = rights[i];
            addRenderableWidget(DVButton.builder(mark(m.in(manage)), b -> {
                manage ^= m.bit();
                b.setMessage(mark(m.in(manage)));
            }).bounds(l + 10 + LABEL_W + 4, t + ROWS_TOP + i * ROW_H, TOGGLE_W, 18).build()).active = editable;
        }
        Perm[] perms = Perm.values();
        for (int i = 0; i < perms.length; i++) {
            Perm p = perms[i];
            addRenderableWidget(DVButton.builder(mark(p.in(land)), b -> {
                land ^= p.bit();
                b.setMessage(mark(p.in(land)));
            }).bounds(l + PANEL_W - 10 - TOGGLE_W, t + ROWS_TOP + i * ROW_H, TOGGLE_W, 18).build()).active = editable;
        }

        int by = t + panelHeight() - 26;
        if (editable) {
            addRenderableWidget(DVButton.builder(CommonComponents.GUI_DONE, b -> save()).bounds(width / 2 - 104, by, 100, 20).build());
            addRenderableWidget(DVButton.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 + 4, by, 100, 20).build());
        } else {
            addRenderableWidget(DVButton.builder(CommonComponents.GUI_BACK, b -> onClose()).bounds(width / 2 - 50, by, 100, 20).build());
        }
    }

    private static Component mark(boolean on) {
        return Component.literal(on ? "✔" : "✘").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    private void save() {
        // An unchanged default name is sent back as its translation key, so it stays translated for everyone.
        String sent = rank != null && name.trim().equals(originalName) ? rank.name : name.trim();
        CompoundTag fields = new CompoundTag();
        fields.putInt("Color", color);
        fields.putInt("Manage", manage);
        fields.putInt("Land", land);
        TeamNetwork.CHANNEL.sendToServer(new ClanActionPacket(rank == null ? ClanActionPacket.Action.RANK_ADD
                : ClanActionPacket.Action.RANK_EDIT, new java.util.UUID(0L, 0L), sent, rank == null ? 0 : rank.id, fields));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int l = left();
        int t = top();
        DVUi.panel(g, l, t, PANEL_W, panelHeight(), false);
        g.renderOutline(l, t, PANEL_W, panelHeight(), 0xFF000000 | color);
        g.drawCenteredString(font, title, width / 2, t + 8, 0xFFFFFF);
        g.drawString(font, Component.translatable("dvteam.edit.name"), l + 10, t + 23, 0xCCCCCC);
        g.drawString(font, Component.translatable("dvteam.edit.color"), l + 220, t + 23, 0xCCCCCC);
        g.drawString(font, Component.translatable("dvteam.ranks.manage_header"), l + 10, t + ROWS_TOP - 12, 0xFFFFFF);
        int rightX = l + PANEL_W - 10 - TOGGLE_W - 4 - LABEL_W;
        g.drawString(font, Component.translatable("dvteam.ranks.land_header"), rightX, t + ROWS_TOP - 12, 0xFFFFFF);
        Manage[] rights = Manage.values();
        for (int i = 0; i < rights.length; i++) {
            label(g, Component.translatable(rights[i].translationKey()), l + 10, t + ROWS_TOP + i * ROW_H + 5);
        }
        Perm[] perms = Perm.values();
        for (int i = 0; i < perms.length; i++) {
            label(g, Component.translatable(perms[i].translationKey()), rightX, t + ROWS_TOP + i * ROW_H + 5);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphics g, Component text, int x, int y) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), LABEL_W), x, y, 0xE0E0E0);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
