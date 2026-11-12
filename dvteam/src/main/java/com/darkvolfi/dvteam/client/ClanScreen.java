package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVChoicePopup;
import com.darkvolfi.dvlib.client.DVIconButton;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanLevel;
import com.darkvolfi.dvteam.data.ClanRank;
import com.darkvolfi.dvteam.data.ClanTypes.Manage;
import com.darkvolfi.dvteam.data.ClanTypes.SettlementType;
import com.darkvolfi.dvteam.network.ClanActionPacket;
import com.darkvolfi.dvteam.network.ClanActionPacket.Action;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
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
    private static final int FLAG_BOX_W = 36;

    private enum Tab {
        GENERAL(TeamMapIntegration.SHIELD),
        MEMBERS(new String[]{"##...##", "##...##", ".......", "###.###", "###.###", "###.###", "#.#.#.#"}),
        RANKS(new String[]{"...#...", "..#.#..", ".#...#.", "...#...", "..#.#..", ".#...#.", "#.....#"}),
        SETTLEMENTS(new String[]{"...#...", "..###..", ".#####.", "#######", ".##.##.", ".##.##.", ".#####."}),
        DIPLOMACY(new String[]{"#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "##...##"}),
        INVITE(new String[]{".##....", ".##..#.", ".....#.", "###.###", "###..#.", "###..#.", "#.#...."});

        /** 7×7 pattern, '#' filled. */
        final String[] icon;

        Tab(String[] icon) {
            this.icon = icon;
        }
    }

    private static Tab tab = Tab.GENERAL;

    private record Row(Component label, List<DVButton> buttons) {}

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

    private static DVButton button(Component label, int width, java.util.function.Consumer<DVButton> press) {
        return DVButton.builder(label, press).size(width, 18).build();
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
            addRenderableWidget(DVButton.builder(Component.translatable("dvteam.screen.found"), b ->
                    minecraft.setScreen(new ClanEditScreen(this, null))).bounds(l + 10, t + 24, PANEL_W - 20, 20).build());
            buildNoClanRows();
        } else {
            // Tabs are icons in a centred row; the selected one is lit, its name is the tooltip.
            Tab[] tabs = Tab.values();
            int size = 18;
            int x0 = l + (PANEL_W - tabs.length * (size + 3) + 3) / 2;
            for (int i = 0; i < tabs.length; i++) {
                Tab tb = tabs[i];
                addRenderableWidget(new DVIconButton(x0 + i * (size + 3), t + 24, size, tb.icon,
                        Component.translatable("dvteam.tab." + tb.name().toLowerCase(Locale.ROOT)), () -> {
                    tab = tb;
                    scroll = 0;
                    rebuildWidgets();
                }).selected(tab == tb));
            }
            switch (tab) {
                case GENERAL -> buildGeneral(clan);
                case MEMBERS -> buildMembers(clan);
                case RANKS -> buildRanks(clan);
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
                DVButton b = row.buttons.get(j);
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
        boolean leader = ClientClanCache.amLeader();
        // The first three rows have no buttons: the flag is drawn on their right.
        rows.add(new Row(clanLabel(clan), List.of()));
        rows.add(new Row(Component.translatable("dvteam.screen.your_rank",
                ClientClanCache.rankLabel(clan, minecraft.player.getUUID())), List.of()));
        rows.add(new Row(Component.translatable("dvteam.screen.counts",
                ClientClanCache.chunkCount(clan.id), ClientClanCache.maxChunks(clan),
                ClientClanCache.settlementCount(clan.id, SettlementType.VILLAGE), lim.maxVillages(),
                ClientClanCache.forcedCount(clan.id), ClientClanCache.maxForced(clan)), List.of()));
        int level = ClientClanCache.level(clan);
        int next = ClanLevel.nextPoints(level);
        Component levelLabel = next < 0
                ? Component.translatable("dvteam.screen.level_max", level, ClientClanCache.points(clan))
                : Component.translatable("dvteam.screen.level", level, ClientClanCache.points(clan), next);
        DVButton vault = button(Component.translatable("dvteam.screen.vault"), 50, b -> send(ClanActionPacket.of(Action.VAULT_OPEN)));
        vault.active = ClientClanCache.can(Manage.VAULT);
        vault.setTooltip(Tooltip.create(Component.translatable("dvteam.screen.vault.tooltip")));
        rows.add(new Row(levelLabel, List.of(vault)));
        DVButton ff = button(onOff("dvteam.screen.friendly_fire", clan.friendlyFire), 120, b -> {
            CompoundTag fields = new CompoundTag();
            fields.putBoolean("FriendlyFire", !clan.friendlyFire);
            send(ClanActionPacket.fields(Action.EDIT, fields));
        });
        ff.active = leader;
        DVButton edit = button(Component.translatable("dvteam.screen.edit"), 60, b -> minecraft.setScreen(new ClanEditScreen(this, clan)));
        edit.active = leader;
        rows.add(new Row(Component.empty(), List.of(edit, ff,
                button(Component.translatable("dvteam.screen.get_banner"), 100, b -> send(ClanActionPacket.of(Action.GET_BANNER))))));
        List<DVButton> danger = new ArrayList<>();
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
        boolean leader = ClientClanCache.amLeader();
        int mine = ClientClanCache.myOrder();
        // Highest first: the leader, then each rank in order.
        List<UUID> sorted = new ArrayList<>(clan.members.keySet());
        sorted.sort((a, b) -> Integer.compare(clan.order(a), clan.order(b)));
        for (UUID id : sorted) {
            boolean below = mine < clan.order(id);
            boolean online = ClientClanCache.online(id);
            MutableComponent label = Component.literal((clan.isLeader(id) ? "★ " : "• ") + ClientClanCache.playerName(id))
                    .withStyle(online ? ChatFormatting.WHITE : ChatFormatting.GRAY)
                    .append(Component.literal("  ")).append(ClientClanCache.rankLabel(clan, id));
            List<DVButton> buttons = new ArrayList<>();
            if (below && ClientClanCache.can(Manage.RANKS)) {
                // Only ranks strictly below my own can be given.
                List<DVChoicePopup.Option<Integer>> options = new ArrayList<>();
                for (ClanRank r : clan.ranks) {
                    if (clan.orderOf(r.id) > mine) {
                        options.add(new DVChoicePopup.Option<>(r.id, r.displayName().copy().withStyle(s -> s.withColor(r.textColor()))));
                    }
                }
                if (!options.isEmpty()) {
                    buttons.add(button(Component.translatable("dvteam.action.set_rank"), 60, b -> DVChoicePopup.open(b, options,
                            clan.members.get(id), picked -> send(new ClanActionPacket(Action.SET_RANK, id, "", picked, new CompoundTag())))).menu());
                }
            }
            if (leader && !clan.isLeader(id)) {
                buttons.add(button(Component.translatable("dvteam.action.make_leader"), 60, b -> confirm(
                        Component.translatable("dvteam.confirm.make_leader", ClientClanCache.playerName(id), clan.ranks.get(0).displayName()),
                        () -> send(new ClanActionPacket(Action.SET_RANK, id, "", ClanRank.LEADER_ID, new CompoundTag())))));
            }
            if (below && ClientClanCache.can(Manage.KICK)) {
                buttons.add(button(Component.translatable("dvteam.action.kick"), 40, b -> send(ClanActionPacket.target(Action.KICK, id))));
            }
            rows.add(new Row(label, buttons));
        }
    }

    /** Everyone sees the ranks and what they may do; only the leader adds, edits, orders and deletes them. */
    private void buildRanks(ClanData.Clan clan) {
        boolean leader = ClientClanCache.amLeader();
        rows.add(new Row(Component.literal("★ ").append(Component.translatable("dvteam.rank.leader"))
                .withStyle(ChatFormatting.GOLD).append(Component.literal("  ").append(Component.translatable("dvteam.ranks.leader_all"))
                        .withStyle(ChatFormatting.GRAY)), List.of()));
        for (int i = 0; i < clan.ranks.size(); i++) {
            ClanRank r = clan.ranks.get(i);
            long count = clan.members.values().stream().filter(v -> v == r.id).count();
            MutableComponent label = Component.literal("■ ").withStyle(s -> s.withColor(r.color))
                    .append(r.displayName().copy().withStyle(s -> s.withColor(r.textColor())))
                    .append(Component.literal("  " + count + "👤").withStyle(ChatFormatting.GRAY));
            if (i == clan.ranks.size() - 1) label.append(Component.literal("  ").append(Component.translatable("dvteam.ranks.entry")).withStyle(ChatFormatting.DARK_GRAY));
            List<DVButton> buttons = new ArrayList<>();
            if (leader) {
                DVButton up = button(Component.literal("▲"), 18, b -> send(ClanActionPacket.rank(Action.RANK_UP, r.id)));
                up.active = i > 0;
                DVButton down = button(Component.literal("▼"), 18, b -> send(ClanActionPacket.rank(Action.RANK_DOWN, r.id)));
                down.active = i < clan.ranks.size() - 1;
                buttons.add(up);
                buttons.add(down);
                buttons.add(button(Component.translatable("dvteam.screen.edit"), 60, b -> minecraft.setScreen(new RankEditScreen(this, clan, r, true))));
                // Members of a deleted rank move one rank down, or up when it was the lowest.
                ClanRank heir = clan.ranks.size() < 2 ? null : clan.ranks.get(i + 1 < clan.ranks.size() ? i + 1 : i - 1);
                DVButton delete = button(Component.literal("×").withStyle(ChatFormatting.RED), 18, b -> confirm(
                        Component.translatable("dvteam.confirm.rank_delete", r.displayName(), heir.displayName()),
                        () -> send(ClanActionPacket.rank(Action.RANK_DELETE, r.id))));
                delete.active = heir != null;
                buttons.add(delete);
            } else {
                buttons.add(button(Component.translatable("dvteam.ranks.view"), 60, b -> minecraft.setScreen(new RankEditScreen(this, clan, r, false))));
            }
            rows.add(new Row(label, buttons));
        }
        if (leader) {
            DVButton add = button(Component.translatable("dvteam.ranks.add"), 120, b -> minecraft.setScreen(new RankEditScreen(this, clan, null, true)));
            add.active = clan.ranks.size() < ClanRank.MAX_RANKS;
            rows.add(new Row(Component.translatable("dvteam.ranks.count", clan.ranks.size(), ClanRank.MAX_RANKS).withStyle(ChatFormatting.GRAY), List.of(add)));
        }
    }

    private void buildSettlements(ClanData.Clan clan) {
        for (ClanData.Settlement s : ClientClanCache.settlementsOf(clan.id)) {
            Component label = Component.literal(s.name).withStyle(st -> st.withColor(clan.textColor()))
                    .append(Component.literal("  ").append(Component.translatable(s.type.translationKey()))
                            .append(" · ").append(Component.translatable("dvteam.settlement.chunks", ClientClanCache.settlementChunks(s.id)))
                            .withStyle(ChatFormatting.GRAY));
            rows.add(new Row(label, List.of(button(Component.translatable("dvteam.screen.settings"), 60, b ->
                    minecraft.setScreen(new SettlementScreen(this, s))))));
        }
        if (rows.isEmpty()) rows.add(new Row(Component.translatable("dvteam.screen.no_settlements").withStyle(ChatFormatting.GRAY), List.of()));
    }

    private void buildDiplomacy(ClanData.Clan clan) {
        boolean leader = ClientClanCache.can(Manage.DIPLOMACY);
        long now = System.currentTimeMillis();
        for (ClanData.Clan other : ClientClanCache.clans()) {
            if (other.id.equals(clan.id)) continue;
            MutableComponent label = clanLabel(other);
            List<DVButton> buttons = new ArrayList<>();
            ClanData.War war = ClientClanCache.warBetween(clan.id, other.id);
            if (war != null) {
                boolean active = war.active(now);
                long left = (active ? war.endsAt : war.startsAt) - now;
                label.append(Component.literal("  ").append(Component.translatable(active ? "dvteam.war.fighting" : "dvteam.war.preparing",
                        formatTime(left))).withStyle(ChatFormatting.RED));
                if (leader) {
                    boolean offered = war.attacker.equals(clan.id) ? war.attackerPeace : war.defenderPeace;
                    DVButton peace = button(Component.translatable(offered ? "dvteam.war.peace_offered" : "dvteam.war.offer_peace"), 70, b ->
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
                        DVButton ally = button(Component.translatable(clan.allyRequests.contains(other.id) ? "dvteam.diplo.requested" : "dvteam.diplo.request"), 70, b ->
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
        boolean officer = ClientClanCache.can(Manage.INVITE);
        for (Map.Entry<UUID, String> e : ClientClanCache.playerNames().entrySet()) {
            UUID id = e.getKey();
            if (!ClientClanCache.online(id) || ClientClanCache.clanOf(id) != null) continue;
            boolean invited = clan.invited.contains(id);
            DVButton b = button(Component.translatable(invited ? "dvteam.action.invited" : "dvteam.action.invite"), 70, x ->
                    send(ClanActionPacket.target(Action.INVITE, id)));
            b.active = officer && !invited;
            rows.add(new Row(Component.literal(e.getValue()), List.of(b)));
        }
        if (rows.isEmpty()) rows.add(new Row(Component.translatable("dvteam.screen.no_free_players").withStyle(ChatFormatting.GRAY), List.of()));
    }

    private static MutableComponent clanLabel(ClanData.Clan c) {
        return Component.literal("[" + c.tag + "] " + c.name).withStyle(s -> s.withColor(c.textColor()));
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
        DVUi.panel(g, l, t, PANEL_W, PANEL_H, false);
        g.renderOutline(l, t, PANEL_W, PANEL_H, 0xFF000000 | accent);
        g.drawCenteredString(font, title, width / 2, t + 8, 0xFFFFFF);

        if (!ClientClanCache.ready()) {
            g.drawCenteredString(font, Component.translatable("dvteam.screen.no_server"), width / 2, t + PANEL_H / 2, 0xAAAAAA);
        } else if (clan == null && rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("dvteam.screen.no_clan_hint"), width / 2, t + ROWS_TOP + 6, 0xAAAAAA);
        }
        // On the general tab the flag sits in its own frame beside the first three rows (which have no buttons).
        boolean flag = clan != null && tab == Tab.GENERAL && scroll == 0;
        int flagX = l + PANEL_W - 10 - FLAG_BOX_W;
        for (int i = 0; i < visibleRows() && i + scroll < rows.size(); i++) {
            Row row = rows.get(i + scroll);
            int y = t + ROWS_TOP + i * ROW_H;
            int stripeRight = flag && i < 3 ? flagX - 4 : l + PANEL_W - 6;
            if (i % 2 == 0) g.fill(l + 6, y - 2, stripeRight, y + 20, 0x18FFFFFF);
            g.drawString(font, row.label, l + 12, y + 5, 0xFFFFFF);
        }
        if (flag) {
            int flagY = t + ROWS_TOP - 2;
            int boxH = 3 * ROW_H - 2;
            DVUi.panel(g, flagX, flagY, FLAG_BOX_W, boxH, false);
            int flagH = boxH - 8;
            BannerView.draw(g, clan.banner, flagX + (FLAG_BOX_W - flagH / 2) / 2, flagY + 4, flagH);
        }

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
