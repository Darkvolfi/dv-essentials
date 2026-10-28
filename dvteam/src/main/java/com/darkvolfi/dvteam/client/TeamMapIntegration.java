package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvlib.client.DVTheme;
import com.darkvolfi.dvmap.MapConfig;
import com.darkvolfi.dvmap.client.MapOverlayHook;
import com.darkvolfi.dvmap.client.WorldMapScreen;
import com.darkvolfi.dvteam.TeamConfig;
import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanTypes.Rank;
import com.darkvolfi.dvteam.data.ClanTypes.SettlementType;
import com.darkvolfi.dvteam.network.ChunkClaimActionPacket;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** Clan territory on DV Map: borders, settlement names and icons, and claiming from the world map. */
public final class TeamMapIntegration implements MapOverlayHook {
    private static final int WAR_RED = 0xFF3030;
    /** 7x7 pixel icons per settlement type; '#' is filled. */
    private static final String[] CASTLE = {"#.#.#.#", "#######", ".#####.", ".##.##.", ".##.##.", ".#####.", ".#####."};
    private static final String[] HOUSE = {"...#...", "..###..", ".#####.", "#######", ".##.##.", ".##.##.", ".#####."};
    private static final String[] BAG = {"..###..", "...#...", ".#####.", "#######", "#######", "#######", ".#####."};

    private boolean claimMode;

    @Override
    public void onWorldMapInit(WorldMapScreen screen, Consumer<AbstractWidget> addWidget) {
        int rightX = screen.width - 30;
        addWidget.accept(Button.builder(Component.translatable("dvteam.map.team_button"), b ->
                Minecraft.getInstance().setScreen(new ClanScreen(screen))).bounds(rightX - 60, 4, 56, 20).build());
        addWidget.accept(Button.builder(claimModeLabel(), b -> {
            claimMode = !claimMode;
            b.setMessage(claimModeLabel());
        }).bounds(rightX - 154, 4, 90, 20).build());
    }

    private Component claimModeLabel() {
        return Component.translatable(claimMode ? "dvteam.map.claim_mode_on" : "dvteam.map.claim_mode_off")
                .withStyle(claimMode ? ChatFormatting.GOLD : ChatFormatting.WHITE);
    }

    @Override
    public void renderChunkOverlay(GuiGraphics g, ResourceKey<Level> dimension,
                                   int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                                   float centerScreenX, float centerScreenY,
                                   double centerWorldX, double centerWorldZ,
                                   float scale, boolean isMinimap) {
        if (isMinimap && !TeamConfig.SHOW_CLAIMS_ON_MINIMAP.get()) return;
        int fillAlpha = isMinimap ? 0x2E << 24 : 0x40 << 24;
        int borderAlpha = isMinimap ? 0xAA << 24 : 0xE0 << 24;

        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                ClanData.Claim claim = ClientClanCache.claimAt(dimension, cx, cz);
                ClanData.Settlement s = claim == null ? null : ClientClanCache.settlement(claim.settlementId);
                ClanData.Clan clan = s == null ? null : ClientClanCache.clan(s.clanId);
                if (clan == null) continue;

                int x0 = Math.round(centerScreenX + (float) ((cx * 16.0 - centerWorldX) * scale));
                int y0 = Math.round(centerScreenY + (float) ((cz * 16.0 - centerWorldZ) * scale));
                int x1 = Math.round(centerScreenX + (float) (((cx + 1) * 16.0 - centerWorldX) * scale));
                int y1 = Math.round(centerScreenY + (float) (((cz + 1) * 16.0 - centerWorldZ) * scale));
                if (x1 <= x0 || y1 <= y0) continue;

                boolean war = ClientClanCache.atWarWithMe(clan.id);
                g.fill(x0, y0, x1, y1, fillAlpha | clan.color);
                int border = borderAlpha | (war ? WAR_RED : clan.color);
                int th = (!isMinimap && scale >= 1.5f) || war ? 2 : 1;
                // Only the settlement's outer edges get a border.
                if (!same(dimension, cx, cz - 1, s)) g.fill(x0, y0, x1, y0 + th, border);
                if (!same(dimension, cx, cz + 1, s)) g.fill(x0, y1 - th, x1, y1, border);
                if (!same(dimension, cx - 1, cz, s)) g.fill(x0, y0, x0 + th, y1, border);
                if (!same(dimension, cx + 1, cz, s)) g.fill(x1 - th, y0, x1, y1, border);

                if (claim.forced && x1 - x0 >= 8) {
                    g.fill(x0 + 2, y0 + 2, x0 + 6, y0 + 6, 0xFF000000);
                    g.fill(x0 + 3, y0 + 3, x0 + 5, y0 + 5, 0xFFFFD54F);
                }
            }
        }

        // Rotated minimap text would turn with the map, so only label it while it points north.
        if (isMinimap && MapConfig.ROTATE_WITH_PLAYER.get()) return;
        String dim = dimension.location().toString();
        Font font = Minecraft.getInstance().font;
        for (ClanData.Settlement s : ClientClanCache.settlements()) {
            if (!s.dimension.equals(dim)) continue;
            double[] c = ClientClanCache.centre(s.id);
            ClanData.Clan clan = ClientClanCache.clan(s.clanId);
            if (c == null || clan == null) continue;
            int cxChunk = (int) Math.floor(c[0] / 16);
            int czChunk = (int) Math.floor(c[1] / 16);
            if (cxChunk < minChunkX - 1 || cxChunk > maxChunkX + 1 || czChunk < minChunkZ - 1 || czChunk > maxChunkZ + 1) continue;
            int sx = Math.round(centerScreenX + (float) ((c[0] - centerWorldX) * scale));
            int sy = Math.round(centerScreenY + (float) ((c[1] - centerWorldZ) * scale));
            drawSettlement(g, font, s, clan, sx, sy, scale, isMinimap);
        }
    }

    private static boolean same(ResourceKey<Level> dim, int cx, int cz, ClanData.Settlement s) {
        ClanData.Claim n = ClientClanCache.claimAt(dim, cx, cz);
        return n != null && n.settlementId.equals(s.id);
    }

    /** Icon (bigger castle for the capital) with the name under it; the capital also shows the clan flag. */
    private static void drawSettlement(GuiGraphics g, Font font, ClanData.Settlement s, ClanData.Clan clan,
                                       int sx, int sy, float scale, boolean minimap) {
        boolean capital = s.type == SettlementType.CAPITAL;
        String[] icon = capital ? CASTLE : s.type == SettlementType.TRADE ? BAG : HOUSE;
        int px = capital && !minimap ? 2 : 1;
        drawIcon(g, icon, sx, sy, px, clan.color);
        if (minimap || !capital && scale < 0.5f) return;

        int half = 7 * px / 2;
        if (capital) BannerView.draw(g, clan.banner, sx - half - 14, sy - 12, 24);
        Component name = Component.literal(s.name).withStyle(capital ? ChatFormatting.BOLD : ChatFormatting.RESET);
        int w = font.width(name);
        int ty = sy + half + 3;
        g.fill(sx - w / 2 - 2, ty - 1, sx + w / 2 + 2, ty + 9, 0x99000000);
        g.drawCenteredString(font, name, sx, ty, 0xFF000000 | clan.color);
    }

    private static void drawIcon(GuiGraphics g, String[] rows, int cx, int cy, int px, int rgb) {
        int x0 = cx - 7 * px / 2;
        int y0 = cy - 7 * px / 2;
        for (int pass = 0; pass < 2; pass++) {
            int color = pass == 0 ? 0xFF000000 : 0xFF000000 | rgb;
            int grow = pass == 0 ? 1 : 0;
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 7; c++) {
                    if (rows[r].charAt(c) != '#') continue;
                    g.fill(x0 + c * px - grow, y0 + r * px - grow, x0 + (c + 1) * px + grow, y0 + (r + 1) * px + grow, color);
                }
            }
        }
    }

    @Override
    public Component getChunkHoverText(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ClanData.Claim claim = ClientClanCache.claimAt(dimension, chunkX, chunkZ);
        ClanData.Settlement s = claim == null ? null : ClientClanCache.settlement(claim.settlementId);
        ClanData.Clan clan = s == null ? null : ClientClanCache.clan(s.clanId);
        if (clan == null) return null;
        MutableComponent text = Component.literal("[" + clan.tag + "] " + s.name).withStyle(st -> st.withColor(clan.color))
                .append(Component.literal(" (").append(Component.translatable(s.type.translationKey())).append(")")
                        .withStyle(ChatFormatting.GRAY));
        if (ClientClanCache.atWarWithMe(clan.id)) text.append(Component.literal(" ⚔").withStyle(ChatFormatting.RED));
        if (claim.forced) text.append(Component.literal(" ").append(Component.translatable("dvteam.map.force_loaded_tag")).withStyle(ChatFormatting.GOLD));
        return text;
    }

    @Override
    public boolean onWorldMapClick(WorldMapScreen screen, ResourceKey<Level> dimension,
                                   int chunkX, int chunkZ, int blockX, int blockZ, int button) {
        if (!claimMode || button != 0 && button != 1) return false;
        ClanData.Clan mine = ClientClanCache.myClan();
        if (mine == null || !ClientClanCache.iAm(Rank.OFFICER)) return true; // the bar explains why
        if (button == 0 && !Screen.hasShiftDown() && ClientClanCache.claimAt(dimension, chunkX, chunkZ) == null
                && !nextToMine(mine, dimension, chunkX, chunkZ)) {
            Minecraft.getInstance().setScreen(new FoundSettlementScreen(screen, dimension.location().toString(), chunkX, chunkZ));
            return true;
        }
        send(dimension, chunkX, chunkZ, button);
        return true;
    }

    @Override
    public boolean onWorldMapDrag(WorldMapScreen screen, ResourceKey<Level> dimension, int chunkX, int chunkZ, int button) {
        if (!claimMode || button != 0 && button != 1) return false;
        ClanData.Clan mine = ClientClanCache.myClan();
        if (mine == null) return true;
        // Dragging only grows existing settlements; founding one always goes through the name prompt.
        if (button == 0 && !Screen.hasShiftDown() && !nextToMine(mine, dimension, chunkX, chunkZ)) return true;
        send(dimension, chunkX, chunkZ, button);
        return true;
    }

    private static boolean nextToMine(ClanData.Clan mine, ResourceKey<Level> dim, int cx, int cz) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            ClanData.Settlement s = ClientClanCache.settlementAt(dim, cx + d[0], cz + d[1]);
            if (s != null && s.clanId.equals(mine.id)) return true;
        }
        return false;
    }

    private static void send(ResourceKey<Level> dimension, int chunkX, int chunkZ, int button) {
        boolean shift = Screen.hasShiftDown();
        ChunkClaimActionPacket.Action action = button == 0
                ? shift ? ChunkClaimActionPacket.Action.FORCE_LOAD : ChunkClaimActionPacket.Action.CLAIM
                : shift ? ChunkClaimActionPacket.Action.UNFORCE_LOAD : ChunkClaimActionPacket.Action.UNCLAIM;
        TeamNetwork.CHANNEL.sendToServer(ChunkClaimActionPacket.of(action, dimension.location().toString(), chunkX, chunkZ));
    }

    @Override
    public void renderWorldMapExtra(GuiGraphics g, WorldMapScreen screen, int mouseX, int mouseY) {
        if (!claimMode) return;
        int barY = WorldMapScreen.TOP_BAR_HEIGHT;
        g.fill(0, barY, screen.width, barY + 18, DVTheme.shade(0xDD221E30));
        g.hLine(0, screen.width, barY + 18, 0xFF4A4266);
        Font font = Minecraft.getInstance().font;

        ClanData.Clan mine = ClientClanCache.myClan();
        Component text;
        if (mine == null) {
            text = Component.translatable("dvteam.map.need_clan").withStyle(ChatFormatting.YELLOW);
        } else if (!ClientClanCache.iAm(Rank.OFFICER)) {
            text = Component.translatable("dvteam.map.need_officer").withStyle(ChatFormatting.YELLOW);
        } else {
            ClanData.Snapshot lim = ClientClanCache.limits();
            text = Component.literal("[" + mine.tag + "] ").withStyle(s -> s.withColor(mine.color))
                    .append(Component.translatable("dvteam.map.banner_counts",
                            ClientClanCache.chunkCount(mine.id), ClientClanCache.maxChunks(mine),
                            ClientClanCache.settlementCount(mine.id, SettlementType.VILLAGE), lim.maxVillages(),
                            ClientClanCache.forcedCount(mine.id), ClientClanCache.maxForced(mine)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("   •   ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("dvteam.map.banner_help").withStyle(ChatFormatting.YELLOW));
        }
        g.drawCenteredString(font, text, screen.width / 2, barY + 5, 0xFFFFFF);
    }
}
