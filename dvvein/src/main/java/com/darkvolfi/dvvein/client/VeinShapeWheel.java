package com.darkvolfi.dvvein.client;

import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvvein.VeinShape;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The shape wheel: hold the shape key, move the mouse towards a shape and let go (or click) to pick it.
 * Shapes sit on a ring around the crosshair; the one in the mouse's direction is highlighted.
 */
public final class VeinShapeWheel extends Screen {
    private static final int RADIUS = 58;
    private static final int SLOT = 36;
    private static final int CELL = 4;
    private static final int STONE = 0xFF505058;
    private static final int MINED = 0xFFFFD040;
    private static final int PLAYER = 0xFF6FB8FF;
    /** The mouse must be this far from the centre before a direction counts. */
    private static final int DEAD_ZONE = 10;

    /**
     * Little block diagrams, 7×7 cells: 'o' is broken (gold, like the in-world outline), 'p' the player,
     * '.' stone. Tunnels are seen from the side with the player on the left looking right; the 3×3 face from the front.
     */
    private static final String[][] DIAGRAMS = {
            {".......", "..oo...", ".ooo...", "..oooo.", "....oo.", "...oo..", "......."}, // SHAPELESS
            {".......", ".......", ".......", "poooooo", "p......", ".......", "......."}, // SMALL_TUNNEL
            {".......", "po.....", "poo....", ".ooo...", "..ooo..", "...ooo.", "....ooo"}, // MINING_TUNNEL
            {".......", ".......", ".......", ".oooooo", "poooooo", "poooooo", "......."}, // LARGE_TUNNEL
            {".......", ".......", "..ooo..", "..ooo..", "..ooo..", ".......", "......."}, // SQUARE
            {"....ooo", "...ooo.", "..ooo..", ".ooo...", "poo....", "po.....", "......."}, // ESCAPE_TUNNEL
    };

    private int hovered = -1;

    public VeinShapeWheel() {
        super(Component.translatable("key.dvvein.shape"));
    }

    private static int[] slotCentre(int i, int cx, int cy) {
        // First shape at the top, then clockwise.
        double angle = -Math.PI / 2 + i * 2 * Math.PI / VeinShape.values().length;
        return new int[]{cx + (int) Math.round(Math.cos(angle) * RADIUS), cy + (int) Math.round(Math.sin(angle) * RADIUS)};
    }

    private int hoveredSlot(int mouseX, int mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return -1;
        int n = VeinShape.values().length;
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / n; // shift so each slot's sector is centred on it
        return Math.floorMod((int) Math.floor(angle / (2 * Math.PI / n)), n);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        hovered = hoveredSlot(mouseX, mouseY);
        VeinShape[] shapes = VeinShape.values();
        for (int i = 0; i < shapes.length; i++) {
            int[] c = slotCentre(i, cx, cy);
            int x = c[0] - SLOT / 2;
            int y = c[1] - SLOT / 2;
            DVUi.panel(g, x, y, SLOT, SLOT, i == hovered);
            if (shapes[i] == VeinClient.shape()) g.renderOutline(x, y, SLOT, SLOT, DVUi.accent());
            drawDiagram(g, DIAGRAMS[i], c[0], c[1], i == hovered);
            drawName(g, Component.translatable(shapes[i].translationKey()), c, cx, i == hovered);
        }
        g.drawCenteredString(font, Component.translatable("dvvein.wheel.hint"), cx, cy + RADIUS + SLOT / 2 + 18, 0xFFA0A0A0);
    }

    private static void drawDiagram(GuiGraphics g, String[] rows, int cx, int cy, boolean lit) {
        int x0 = cx - rows[0].length() * CELL / 2;
        int y0 = cy - rows.length * CELL / 2;
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                char ch = rows[r].charAt(c);
                int color = ch == 'o' ? MINED : ch == 'p' ? PLAYER : STONE;
                if (!lit && ch == 'o') color = 0xFFC8A030; // a little dimmer until hovered
                int x = x0 + c * CELL;
                int y = y0 + r * CELL;
                g.fill(x, y, x + CELL - 1, y + CELL - 1, color);
            }
        }
    }

    /** The name sits outside the ring, away from the crosshair: beside side slots, above/below the top and bottom ones. */
    private void drawName(GuiGraphics g, Component name, int[] c, int cx, boolean lit) {
        int color = lit ? 0xFFFFD040 : 0xFFE6E6F0;
        int w = font.width(name);
        int dx = c[0] - cx;
        if (Math.abs(dx) < SLOT / 2) {
            int y = c[1] < height / 2 ? c[1] - SLOT / 2 - 11 : c[1] + SLOT / 2 + 3;
            g.drawString(font, name, c[0] - w / 2, y, color);
        } else {
            int x = dx > 0 ? c[0] + SLOT / 2 + 4 : c[0] - SLOT / 2 - 4 - w;
            g.drawString(font, name, x, c[1] - 4, color);
        }
    }

    @Override
    public void tick() {
        if (!keyDown()) pickAndClose();
    }

    private static boolean keyDown() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        InputConstants.Key key = VeinClient.SHAPE_KEY.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
    }

    private void pickAndClose() {
        if (hovered >= 0) VeinClient.setShape(VeinShape.values()[hovered]);
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        hovered = hoveredSlot((int) mouseX, (int) mouseY);
        pickAndClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
