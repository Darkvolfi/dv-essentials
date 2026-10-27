package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvlib.client.DVTheme;
import com.darkvolfi.dvteam.network.SyncTeamDataPacket;
import com.darkvolfi.dvteam.network.TeamManagePacket;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamScreen extends Screen {
    public enum Tab {
        MEMBERS,
        INVITE,
        INVITES_IN,
        ALLIES
    }

    private final Screen parent;
    private Tab activeTab = Tab.MEMBERS;
    private EditBox nameBox;
    private String draftName = "";
    private int rightScroll;
    private int maxRightScroll;

    private record RightRow(Component text, int color, Component buttonLabel, Runnable onPress) {}
    private final List<RightRow> rightRows = new ArrayList<>();

    public TeamScreen(Screen parent) {
        super(Component.translatable("dvteam.screen.title"));
        this.parent = parent;
    }

    public void refreshFromSync() {
        SyncTeamDataPacket.TeamEntry myTeam = ClientTeamCache.getMyTeam();
        if (myTeam != null && (nameBox == null || !nameBox.isFocused())) {
            draftName = myTeam.name();
        }
        init();
    }

    @Override
    protected void init() {
        clearWidgets();
        SyncTeamDataPacket.TeamEntry myTeam = ClientTeamCache.getMyTeam();
        if (myTeam != null && draftName.isEmpty()) {
            draftName = myTeam.name();
        }

        int cx = width / 2;
        int leftX = cx - 165;
        int rightX = cx + 5;
        int top = 34;

        // Left column: Team name, colour, permissions, actions
        nameBox = new EditBox(font, leftX, top + 12, 112, 18, Component.translatable("dvteam.screen.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(draftName);
        nameBox.setResponder(v -> draftName = v);
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.translatable("dvteam.screen.save_name"), b -> {
            if (!draftName.trim().isEmpty()) {
                TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.withText(TeamManagePacket.Action.RENAME, draftName.trim()));
            }
        }).bounds(leftX + 116, top + 11, 44, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("dvteam.screen.change_color"), b ->
                TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.simple(TeamManagePacket.Action.CYCLE_COLOR)))
                .bounds(leftX, top + 36, 160, 20).build());

        boolean ff = myTeam != null && myTeam.friendlyFire();
        addRenderableWidget(Button.builder(toggleText("dvteam.screen.friendly_fire", ff), b ->
                TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.simple(TeamManagePacket.Action.TOGGLE_FRIENDLY_FIRE)))
                .bounds(leftX, top + 60, 160, 20).build());

        boolean allyInt = myTeam == null || myTeam.allowAllyInteract();
        addRenderableWidget(Button.builder(toggleText("dvteam.screen.ally_interact", allyInt), b ->
                TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.simple(TeamManagePacket.Action.TOGGLE_ALLY_INTERACT)))
                .bounds(leftX, top + 84, 160, 20).build());

        boolean allyEdit = myTeam != null && myTeam.allowAllyEdit();
        addRenderableWidget(Button.builder(toggleText("dvteam.screen.ally_edit", allyEdit), b ->
                TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.simple(TeamManagePacket.Action.TOGGLE_ALLY_EDIT)))
                .bounds(leftX, top + 108, 160, 20).build());

        boolean pubInt = myTeam != null && myTeam.publicInteract();
        addRenderableWidget(Button.builder(toggleText("dvteam.screen.public_interact", pubInt), b ->
                TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.simple(TeamManagePacket.Action.TOGGLE_PUBLIC_INTERACT)))
                .bounds(leftX, top + 132, 160, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("dvteam.screen.unclaim_all"), b ->
                TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.simple(TeamManagePacket.Action.UNCLAIM_ALL)))
                .bounds(leftX, top + 158, 78, 20).build());

        Button leaveBtn = addRenderableWidget(Button.builder(Component.translatable("dvteam.screen.leave"), b ->
                TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.simple(TeamManagePacket.Action.LEAVE_TEAM)))
                .bounds(leftX + 82, top + 158, 78, 20).build());
        leaveBtn.active = myTeam != null && myTeam.members().size() > 1;

        // Right column: 4 tabs (Members | Invite | Invites | Allies)
        Tab[] tabs = Tab.values();
        int tabW = 39;
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            Component label = Component.translatable("dvteam.tab." + tab.name().toLowerCase())
                    .withStyle(tab == activeTab ? ChatFormatting.GOLD : ChatFormatting.WHITE);
            addRenderableWidget(Button.builder(label, b -> {
                activeTab = tab;
                rightScroll = 0;
                init();
            }).bounds(rightX + i * 40, top, tabW, 20).build());
        }

        buildRightRows(myTeam);
        int listTop = top + 26;
        int maxVisible = Math.max(1, (height - listTop - 38) / 22);
        maxRightScroll = Math.max(0, rightRows.size() - maxVisible);
        rightScroll = Mth.clamp(rightScroll, 0, maxRightScroll);

        for (int i = 0; i < maxVisible && (i + rightScroll) < rightRows.size(); i++) {
            RightRow row = rightRows.get(i + rightScroll);
            int ry = listTop + i * 22;
            if (row.buttonLabel != null && row.onPress != null) {
                addRenderableWidget(Button.builder(row.buttonLabel, b -> row.onPress.run())
                        .bounds(rightX + 108, ry, 52, 18).build());
            }
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx - 100, height - 26, 200, 20).build());
    }

    private void buildRightRows(SyncTeamDataPacket.TeamEntry myTeam) {
        rightRows.clear();
        if (minecraft.player == null || myTeam == null) return;
        UUID myId = minecraft.player.getUUID();
        boolean isOwner = myTeam.owner().equals(myId);

        switch (activeTab) {
            case MEMBERS -> {
                for (UUID memberId : myTeam.members()) {
                    String name = ClientTeamCache.getPlayerName(memberId);
                    boolean owner = myTeam.owner().equals(memberId);
                    Component label = Component.literal((owner ? "★ " : "• ") + name);
                    if (isOwner && !memberId.equals(myId)) {
                        rightRows.add(new RightRow(label, 0xFFFFFF,
                                Component.translatable("dvteam.action.kick"),
                                () -> TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.withTarget(TeamManagePacket.Action.KICK_MEMBER, memberId))));
                    } else {
                        rightRows.add(new RightRow(label, owner ? 0xFFD54F : 0xFFFFFF, null, null));
                    }
                }
            }
            case INVITE -> {
                for (UUID onlineId : ClientTeamCache.getOnlinePlayers()) {
                    if (myTeam.members().contains(onlineId)) continue;
                    String name = ClientTeamCache.getPlayerName(onlineId);
                    boolean alreadyInvited = myTeam.invited().contains(onlineId);
                    rightRows.add(new RightRow(
                            Component.literal(name),
                            alreadyInvited ? 0xAAAAAA : 0xFFFFFF,
                            Component.translatable(alreadyInvited ? "dvteam.action.invited" : "dvteam.action.invite"),
                            alreadyInvited ? null : () -> TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.withTarget(TeamManagePacket.Action.INVITE_PLAYER, onlineId))
                    ));
                }
            }
            case INVITES_IN -> {
                for (SyncTeamDataPacket.TeamEntry team : ClientTeamCache.getTeams()) {
                    if (team.id().equals(myTeam.id())) continue;
                    if (team.invited().contains(myId)) {
                        rightRows.add(new RightRow(
                                Component.literal("■ " + team.name()),
                                0xFF000000 | team.color(),
                                Component.translatable("dvteam.action.join"),
                                () -> TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.withTarget(TeamManagePacket.Action.ACCEPT_INVITE, team.id()))
                        ));
                    }
                }
            }
            case ALLIES -> {
                for (SyncTeamDataPacket.TeamEntry other : ClientTeamCache.getTeams()) {
                    if (other.id().equals(myTeam.id())) continue;
                    boolean allied = myTeam.allies().contains(other.id());
                    rightRows.add(new RightRow(
                            Component.literal("■ " + other.name()),
                            0xFF000000 | other.color(),
                            Component.translatable(allied ? "dvteam.action.allied" : "dvteam.action.ally"),
                            () -> TeamNetwork.CHANNEL.sendToServer(TeamManagePacket.withTarget(TeamManagePacket.Action.TOGGLE_ALLY_TEAM, other.id()))
                    ));
                }
            }
        }
    }

    private static Component toggleText(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "options.on" : "options.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxRightScroll > 0) {
            rightScroll = Mth.clamp(rightScroll - (int) Math.signum(delta), 0, maxRightScroll);
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2;
        int leftX = cx - 165;
        int rightX = cx + 5;
        int top = 34;

        g.drawCenteredString(font, title, cx, 12, 0xFFFFFF);
        g.drawString(font, Component.translatable("dvteam.screen.name"), leftX, top, 0xCCCCCC);

        SyncTeamDataPacket.TeamEntry myTeam = ClientTeamCache.getMyTeam();
        int listTop = top + 26;
        int listBottom = height - 34;
        g.fill(rightX, listTop - 2, rightX + 162, listBottom, DVTheme.shade(0x7714121E));
        g.renderOutline(rightX, listTop - 2, 162, listBottom - (listTop - 2), 0xFF3E3852);

        int maxVisible = Math.max(1, (height - listTop - 38) / 22);
        if (rightRows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("dvteam.screen.empty_list"), rightX + 81, listTop + 24, 0x999999);
        } else {
            for (int i = 0; i < maxVisible && (i + rightScroll) < rightRows.size(); i++) {
                RightRow row = rightRows.get(i + rightScroll);
                int ry = listTop + i * 22;
                g.drawString(font, row.text, rightX + 6, ry + 5, row.color);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (myTeam != null) {
            int swatchCol = 0xFF000000 | myTeam.color();
            g.fill(leftX + 138, top + 40, leftX + 152, top + 52, swatchCol);
            g.renderOutline(leftX + 137, top + 39, 16, 14, 0xFF000000);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
