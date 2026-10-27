package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvlib.client.DVTheme;
import com.darkvolfi.dvmap.client.MapOverlayHook;
import com.darkvolfi.dvmap.client.WorldMapScreen;
import com.darkvolfi.dvteam.TeamConfig;
import com.darkvolfi.dvteam.network.ChunkClaimActionPacket;
import com.darkvolfi.dvteam.network.SyncTeamDataPacket;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Integrates DV Team's chunk claiming, force-loading and team management directly into DV Map.
 */
public final class TeamMapIntegration implements MapOverlayHook {
    private boolean claimMode;

    @Override
    public void onWorldMapInit(WorldMapScreen screen, Consumer<AbstractWidget> addWidget) {
        int rightX = screen.width - 30;
        int y = 4;

        // Team management button
        addWidget.accept(Button.builder(Component.translatable("dvteam.map.team_button"), b ->
                Minecraft.getInstance().setScreen(new TeamScreen(screen)))
                .bounds(rightX - 60, y, 56, 20).build());

        // Claim mode toggle button
        addWidget.accept(Button.builder(claimModeLabel(), b -> {
            claimMode = !claimMode;
            b.setMessage(claimModeLabel());
        }).bounds(rightX - 154, y, 90, 20).build());
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

        int fillAlpha = isMinimap ? (0x2E << 24) : (0x48 << 24);
        int borderAlpha = isMinimap ? (0xAA << 24) : (0xE0 << 24);

        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                SyncTeamDataPacket.ClaimEntry claim = ClientTeamCache.getClaim(dimension, cx, cz);
                if (claim == null) continue;
                SyncTeamDataPacket.TeamEntry team = ClientTeamCache.getTeam(claim.teamId());
                if (team == null) continue;

                int x0 = Math.round(centerScreenX + (float) ((cx * 16.0 - centerWorldX) * scale));
                int y0 = Math.round(centerScreenY + (float) ((cz * 16.0 - centerWorldZ) * scale));
                int x1 = Math.round(centerScreenX + (float) (((cx + 1) * 16.0 - centerWorldX) * scale));
                int y1 = Math.round(centerScreenY + (float) (((cz + 1) * 16.0 - centerWorldZ) * scale));
                if (x1 <= x0 || y1 <= y0) continue;

                int rgb = team.color() & 0xFFFFFF;
                g.fill(x0, y0, x1, y1, fillAlpha | rgb);

                int borderCol = borderAlpha | rgb;
                UUID tid = team.id();
                int thickness = (!isMinimap && scale >= 1.5f) ? 2 : 1;

                // Connected territory borders: only draw edges facing a different team or wilderness
                if (!sameTeam(dimension, cx, cz - 1, tid)) {
                    g.fill(x0, y0, x1, y0 + thickness, borderCol);
                }
                if (!sameTeam(dimension, cx, cz + 1, tid)) {
                    g.fill(x0, y1 - thickness, x1, y1, borderCol);
                }
                if (!sameTeam(dimension, cx - 1, cz, tid)) {
                    g.fill(x0, y0, x0 + thickness, y1, borderCol);
                }
                if (!sameTeam(dimension, cx + 1, cz, tid)) {
                    g.fill(x1 - thickness, y0, x1, y1, borderCol);
                }

                // Force-loaded indicator badge
                if (claim.forceLoaded() && (x1 - x0) >= 8) {
                    int bx = x0 + 2;
                    int by = y0 + 2;
                    g.fill(bx, by, bx + 4, by + 4, 0xFF000000);
                    g.fill(bx + 1, by + 1, bx + 3, by + 3, 0xFFFFD54F);
                }
            }
        }
    }

    private static boolean sameTeam(ResourceKey<Level> dimension, int cx, int cz, UUID teamId) {
        SyncTeamDataPacket.ClaimEntry neighbor = ClientTeamCache.getClaim(dimension, cx, cz);
        return neighbor != null && neighbor.teamId().equals(teamId);
    }

    @Override
    public Component getChunkHoverText(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        SyncTeamDataPacket.ClaimEntry claim = ClientTeamCache.getClaim(dimension, chunkX, chunkZ);
        if (claim == null) return null;
        SyncTeamDataPacket.TeamEntry team = ClientTeamCache.getTeam(claim.teamId());
        if (team == null) return null;

        MutableComponent comp = Component.literal("■ " + team.name())
                .withStyle(s -> s.withColor(team.color()));
        if (claim.forceLoaded()) {
            comp.append(Component.literal(" ").append(Component.translatable("dvteam.map.force_loaded_tag"))
                    .withStyle(ChatFormatting.GOLD));
        }
        return comp;
    }

    @Override
    public boolean onWorldMapClick(WorldMapScreen screen, ResourceKey<Level> dimension,
                                   int chunkX, int chunkZ, int blockX, int blockZ, int button) {
        if (!claimMode) return false;
        if (button != 0 && button != 1) return false;
        sendClaimAction(dimension, chunkX, chunkZ, button);
        return true;
    }

    @Override
    public boolean onWorldMapDrag(WorldMapScreen screen, ResourceKey<Level> dimension,
                                  int chunkX, int chunkZ, int button) {
        if (!claimMode) return false;
        if (button != 0 && button != 1) return false;
        sendClaimAction(dimension, chunkX, chunkZ, button);
        return true;
    }

    private void sendClaimAction(ResourceKey<Level> dimension, int chunkX, int chunkZ, int button) {
        boolean shift = Screen.hasShiftDown();
        ChunkClaimActionPacket.Action action;
        if (button == 0) {
            action = shift ? ChunkClaimActionPacket.Action.FORCE_LOAD : ChunkClaimActionPacket.Action.CLAIM;
        } else {
            action = shift ? ChunkClaimActionPacket.Action.UNFORCE_LOAD : ChunkClaimActionPacket.Action.UNCLAIM;
        }
        TeamNetwork.CHANNEL.sendToServer(new ChunkClaimActionPacket(action, dimension.location().toString(), chunkX, chunkZ));
    }

    @Override
    public void renderWorldMapExtra(GuiGraphics g, WorldMapScreen screen, int mouseX, int mouseY) {
        if (!claimMode) return;
        SyncTeamDataPacket.TeamEntry myTeam = ClientTeamCache.getMyTeam();
        if (myTeam == null) return;

        int claimed = ClientTeamCache.countClaims(myTeam.id());
        int maxClaims = ClientTeamCache.maxClaimsFor(myTeam);
        int forced = ClientTeamCache.countForceLoaded(myTeam.id());
        int maxForced = ClientTeamCache.maxForceLoadedFor(myTeam);

        int barY = WorldMapScreen.TOP_BAR_HEIGHT;
        g.fill(0, barY, screen.width, barY + 18, DVTheme.shade(0xDD221E30));
        g.hLine(0, screen.width, barY + 18, 0xFF4A4266);

        MutableComponent text = Component.literal("■ " + myTeam.name() + "   ")
                .withStyle(s -> s.withColor(myTeam.color()))
                .append(Component.translatable("dvteam.map.banner_counts", claimed, maxClaims, forced, maxForced)
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   •   ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("dvteam.map.banner_help").withStyle(ChatFormatting.YELLOW));

        g.drawCenteredString(Minecraft.getInstance().font, text, screen.width / 2, barY + 5, 0xFFFFFF);
    }
}
