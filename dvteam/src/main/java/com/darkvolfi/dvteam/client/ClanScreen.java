package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvlib.client.DVTheme;
import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanTypes.Rank;
import com.darkvolfi.dvteam.data.ClanTypes.SettlementType;
import com.darkvolfi.dvteam.network.ClanActionPacket;
import com.darkvolfi.dvteam.network.ClanActionPacket.Action;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** The clan screen (Y): founding or joining a clan, then its members, settlements, diplomacy and invites. */
public class ClanScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 240;
    private static final int ROW_H = 22;
    private static final int ROWS_TOP = 52;

    private enum Tab { GENERAL, MEMBERS, SETTLEMENTS, DIPLOMACY, INVITE }

    private static Tab tab = Tab.GENERAL;

    private record Row(Component label, List<Button> buttons) {}

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;

    public ClanScreen(Screen parent) {
        super(Component.translatable("dvteam.screen.title"));
        this.parent = parent;
    }

    /** Called after every sync, so the screen always shows the current state. */
    public void refresh() {
        rebuildWidgets();
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    private int visibleRows() {
        return (PANEL_H - ROWS_TOP - 8) / ROW_H;
    }

    private static void send(ClanActionPacket packet) {
        TeamNetwork.CHANNEL.sendToServer(packet);
    }

    private static Button button(Component label, int width, Button.OnPress press) {
        return Button.builder(label, press).size(width, 18).build();
    }

    private void confirm(Component question, Runnable onYes) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) onYes.run();
            minecraft.setScreen(this);
        }, title, question));
    }

    @Override
    protected void init() {
        rows.clear();
        ClanData.Clan clan = ClientClanCache.myClan();
        int l = left();
        int t = top();
        if (!ClientClanCache.ready()) return;

        if (clan == null) {
            addRenderableWidget(Button.builder(Component.translatable("dvteam.screen.found"), b ->
                    minecraft.setScreen(new ClanEditScreen(this, null))).bounds(l + 10, t + 24, PANEL_W - 20, 20).build());
            buildNoClanRows();
        } else {
            Tab[] tabs = Tab.values();
            int w = (PANEL_W - 20) / tabs.length;
            for (int i = 0; i < tabs.length; i++) {
                Tab tb = tabs[i];
                Button b = addRenderableWidget(Button.builder(Component.translatable("dvteam.tab." + tb.name().toLowerCase(Locale.ROOT)), x -> {
                    tab = tb;
                    scroll = 0;
                    rebuildWidgets();
                }).bounds(l + 10 + i * w, t + 24, w - 2, 20).build());
                b.active = tab != tb;
            }
            switch (tab) {
                case GENERAL -> buildGeneral(clan);
                case MEMBERS -> buildMembers(clan);
                case SETTLEMENTS -> buildSettlements(clan);
                case DIPLOMACY -> buildDiplomacy(clan);
                case INVITE -> buildInvite(clan);
            }
        }

        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visibleRows()));
        for (int i = 0; i < visibleRows() && i + scroll < rows.size(); i++) {
            Row row = rows.get(i + scroll);
            int x = l + PANEL_W - 10;
            for (int j = row.buttons.size() - 1; j >= 0; j--) {
                Button b = row.buttons.get(j);
                x -= b.getWidth();
                b.setPosition(x, t + ROWS_TOP + i * ROW_H);
                addRenderableWidget(b);
                x -= 2;
            }
        }
    }

    private void buildNoClanRows() {
        for (ClanData.Clan c : ClientClanCache.invitesForMe()) {
            rows.add(new Row(Component.translatable("dvteam.screen.invite_from", clanLabel(c)), List.of(
                    button(Component.translatable("dvteam.action.join"), 50, b -> send(ClanActionPacket.target(Action.JOIN, c.id))),
                    button(Component.translatable("dvteam.action.decline"), 50, b -> send(ClanActionPacket.target(Action.DECLINE, c.id))))));
        }
        for (ClanData.Clan c : ClientClanCache.clans()) {
            rows.add(new Row(clanLabel(c).append(Component.literal("  " + c.members.size() + "👤").withStyle(ChatFormatting.GRAY)), List.of()));
        }
    }

    private void buildGeneral(ClanData.Clan clan) {
        ClanData.Snapshot lim = ClientClanCache.limits();
        boolean leader = ClientClanCache.iAm(Rank.LEADER);
        // The first three rows have no buttons: the flag is drawn on their right.
        rows.add(new Row(clanLabel(clan), List.of()));
        rows.add(new Row(Component.translatable("dvteam.screen.your_rank", Component.translatable(ClientClanCache.myRank().translationKey())), List.of()));
        rows.add(new Row(Component.translatable("dvteam.screen.counts",
                ClientClanCache.chunkCount(clan.id), lim.maxChunks(),
                ClientClanCache.settlementCount(clan.id, SettlementType.VILLAGE), lim.maxVillages(),
                ClientClanCache.forcedCount(clan.id), lim.maxForced()), List.of()));
        Button ff = button(onOff("dvteam.screen.friendly_fire", clan.friendlyFire), 120, b -> {
            CompoundTag fields = new CompoundTag();
            fields.putBoolean("FriendlyFire", !clan.friendlyFire);
            send(ClanActionPacket.fields(Action.EDIT, fields));
        });
        ff.active = leader;
        Button edit = button(Component.translatable("dvteam.screen.edit"), 60, b -> minecraft.setScreen(new ClanEditScreen(this, clan)));
        edit.active = leader;
        rows.add(new Row(Component.empty(), List.of(edit, ff,
                button(Component.translatable("dvteam.screen.get_banner"), 100, b -> send(ClanActionPacket.of(Action.GET_BANNER))))));
        List<Button> danger = new ArrayList<>();
        if (leader) {
            danger.add(button(Component.translatable("dvteam.screen.unclaim_all"), 90, b -> confirm(
                    Component.translatable("dvteam.confirm.unclaim_all"), () -> send(ClanActionPacket.of(Action.UNCLAIM_ALL)))));
            danger.add(button(Component.translatable("dvteam.screen.disband").withStyle(ChatFormatting.RED), 90, b -> confirm(
                    Component.translatable("dvteam.confirm.disband", clan.name), () -> send(ClanActionPacket.of(Action.DISBAND)))));
        } else {
            danger.add(button(Component.translatable("dvteam.screen.leave").withStyle(ChatFormatting.RED), 90, b -> confirm(
                    Component.translatable("dvteam.confirm.leave", clan.name), () -> send(ClanActionPacket.of(Action.LEAVE)))));
        }
        rows.add(new Row(Component.empty(), danger));
    }

    private void buildMembers(ClanData.Clan clan) {
        Rank mine = ClientClanCache.myRank();
        for (Map.Entry<UUID, Rank> e : clan.members.entrySet()) {
            UUID id = e.getKey();
            Rank rank = e.getValue();
            boolean online = ClientClanCache.online(id);
            MutableComponent label = Component.literal((rank == Rank.LEADER ? "★ " : rank == Rank.OFFICER ? "◆ " : "• ") + ClientClanCache.playerName(id))
                    .withStyle(online ? ChatFormatting.WHITE : ChatFormatting.GRAY)
                    .append(Component.literal("  ").append(Component.translatable(rank.translationKey())).withStyle(ChatFormatting.DARK_GRAY));
            List<Button> buttons = new ArrayList<>();
            if (mine == Rank.LEADER && rank != Rank.LEADER) {
                Rank next = rank == Rank.OFFICER ? Rank.MEMBER : Rank.OFFICER;
                buttons.add(button(Component.translatable(rank == Rank.OFFICER ? "dvteam.action.demote" : "dvteam.action.promote"), 60, b ->
                        send(new ClanActionPacket(Action.SET_RANK, id, "", next.ordinal(), new CompoundTag()))));
                buttons.add(button(Component.translatable("dvteam.action.make_leader"), 60, b -> confirm(
                        Component.translatable("dvteam.confirm.make_leader", ClientClanCache.playerName(id)),
                        () -> send(new ClanActionPacket(Action.SET_RANK, id, "", Rank.LEADER.ordinal(), new CompoundTag())))));
            }
            if (mine != null && mine.atLeast(Rank.OFFICER) && mine.ordinal() > rank.ordinal()) {
                buttons.add(button(Component.translatable("dvteam.action.kick"), 40, b -> send(ClanActionPacket.target(Action.KICK, id))));
            }
            rows.add(new Row(label, buttons));
        }
    }

    private void buildSettlements(ClanData.Clan clan) {
        for (ClanData.Settlement s : ClientClanCache.settlementsOf(clan.id)) {
            Component label = Component.literal(s.name).withStyle(st -> st.withColor(clan.color))
                    .append(Component.literal("  ").append(Component.translatable(s.type.translationKey()))
                            .append(" · ").append(Component.translatable("dvteam.settlement.chunks", ClientClanCache.settlementChunks(s.id)))
                            .withStyle(ChatFormatting.GRAY));
            rows.add(new Row(label, List.of(button(Component.translatable("dvteam.screen.settings"), 60, b ->
                    minecraft.setScreen(new SettlementScreen(this, s))))));
        }
        if (rows.isEmpty()) rows.add(new Row(Component.translatable("dvteam.screen.no_settlements").withStyle(ChatFormatting.GRAY), List.of()));
    }

    private void buildDiplomacy(ClanData.Clan clan) {
        boolean leader = ClientClanCache.iAm(Rank.LEADER);
        long now = System.currentTimeMillis();
        for (ClanData.Clan other : ClientClanCache.clans()) {
            if (other.id.equals(clan.id)) continue;
            MutableComponent label = clanLabel(other);
            List<Button> buttons = new ArrayList<>();
            ClanData.War war = ClientClanCache.warBetween(clan.id, other.id);
            if (war != null) {
                boolean active = war.active(now);
                long left = (active ? war.endsAt : war.startsAt) - now;
                label.append(Component.literal("  ").append(Component.translatable(active ? "dvteam.war.fighting" : "dvteam.war.preparing",
                        formatTime(left))).withStyle(ChatFormatting.RED));
                if (leader) {
                    boolean offered = war.attacker.equals(clan.id) ? war.attackerPeace : war.defenderPeace;
                    Button peace = button(Component.translatable(offered ? "dvteam.war.peace_offered" : "dvteam.war.offer_peace"), 70, b ->
                            send(ClanActionPacket.target(Action.WAR_PEACE, other.id)));
                    peace.active = !offered;
                    buttons.add(peace);
                    buttons.add(button(Component.translatable("dvteam.war.surrender"), 60, b -> confirm(
                            Component.translatable("dvteam.confirm.surrender", other.name), () -> send(ClanActionPacket.target(Action.WAR_SURRENDER, other.id)))));
                }
            } else {
                boolean allied = clan.allies.contains(other.id) && other.allies.contains(clan.id);
                if (allied) label.append(Component.literal("  ").append(Component.translatable("dvteam.diplo.allied")).withStyle(ChatFormatting.GREEN));
                if (leader) {
                    if (allied) {
                        buttons.add(button(Component.translatable("dvteam.diplo.break"), 70, b -> send(ClanActionPacket.target(Action.ALLY_BREAK, other.id))));
                    } else if (other.allyRequests.contains(clan.id)) {
                        buttons.add(button(Component.translatable("dvteam.diplo.accept"), 70, b -> send(ClanActionPacket.target(Action.ALLY_REQUEST, other.id))));
                    } else {
                        Button ally = button(Component.translatable(clan.allyRequests.contains(other.id) ? "dvteam.diplo.requested" : "dvteam.diplo.request"), 70, b ->
                                send(ClanActionPacket.target(Action.ALLY_REQUEST, other.id)));
                        ally.active = !clan.allyRequests.contains(other.id);
                        buttons.add(ally);
                    }
                    if (ClientClanCache.limits().warsEnabled()) {
                        buttons.add(button(Component.translatable("dvteam.war.declare").withStyle(ChatFormatting.RED), 60, b -> confirm(
                                Component.translatable("dvteam.confirm.war", other.name), () -> send(ClanActionPacket.target(Action.WAR_DECLARE, other.id)))));
                    }
                }
            }
            rows.add(new Row(label, buttons));
        }
        if (rows.isEmpty()) rows.add(new Row(Component.translatable("dvteam.screen.no_clans").withStyle(ChatFormatting.GRAY), List.of()));
    }

    private void buildInvite(ClanData.Clan clan) {
        boolean officer = ClientClanCache.iAm(Rank.OFFICER);
        for (Map.Entry<UUID, String> e : ClientClanCache.playerNames().entrySet()) {
            UUID id = e.getKey();
            if (!ClientClanCache.online(id) || ClientClanCache.clanOf(id) != null) continue;
            boolean invited = clan.invited.contains(id);
            Button b = button(Component.translatable(invited ? "dvteam.action.invited" : "dvteam.action.invite"), 70, x ->
                    send(ClanActionPacket.target(Action.INVITE, id)));
            b.active = officer && !invited;
            rows.add(new Row(Component.literal(e.getValue()), List.of(b)));
        }
        if (rows.isEmpty()) rows.add(new Row(Component.translatable("dvteam.screen.no_free_players").withStyle(ChatFormatting.GRAY), List.of()));
    }

    private static MutableComponent clanLabel(ClanData.Clan c) {
        return Component.literal("[" + c.tag + "] " + c.name).withStyle(s -> s.withColor(c.color));
    }

    private static Component onOff(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "options.on" : "options.off"));
    }

    private static String formatTime(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** War timers count down on the diplomacy tab. */
    @Override
    public void tick() {
        if (tab == Tab.DIPLOMACY && !ClientClanCache.wars().isEmpty() && minecraft.level != null
                && minecraft.level.getGameTime() % 20 == 0) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, rows.size() - visibleRows());
        if (max > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, max);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int l = left();
        int t = top();
        ClanData.Clan clan = ClientClanCache.myClan();
        int accent = clan == null ? 0x6A5ACD : clan.color;
        g.fill(l, t, l + PANEL_W, t + PANEL_H, DVTheme.shade(0xEE1C1A26));
        g.renderOutline(l, t, PANEL_W, PANEL_H, 0xFF000000 | accent);
        g.drawCenteredString(font, title, width / 2, t + 8, 0xFFFFFF);

        if (!ClientClanCache.ready()) {
            g.drawCenteredString(font, Component.translatable("dvteam.screen.no_server"), width / 2, t + PANEL_H / 2, 0xAAAAAA);
        } else if (clan == null && rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("dvteam.screen.no_clan_hint"), width / 2, t + ROWS_TOP + 6, 0xAAAAAA);
        }
        for (int i = 0; i < visibleRows() && i + scroll < rows.size(); i++) {
            Row row = rows.get(i + scroll);
            int y = t + ROWS_TOP + i * ROW_H;
            if (i % 2 == 0) g.fill(l + 6, y - 2, l + PANEL_W - 6, y + 20, 0x18FFFFFF);
            g.drawString(font, row.label, l + 12, y + 5, 0xFFFFFF);
        }
        if (clan != null && tab == Tab.GENERAL) BannerView.draw(g, clan.banner, l + PANEL_W - 40, t + ROWS_TOP, 56);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
