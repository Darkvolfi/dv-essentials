package com.darkvolfi.dvlib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Pick one of several options from a menu instead of clicking through them one by one.
 * As a list each row shows its icon and name; as a grid only icons show and the name is the tooltip.
 */
public final class DVChoicePopup<T> extends DVPopup {
    /** Draws an option's icon centred on (cx, cy) in a 16×16 area. */
    public interface Icon {
        void draw(GuiGraphics g, int cx, int cy);
    }

    public record Option<T>(T value, Component name, Icon icon) {
        public Option(T value, Component name) {
            this(value, name, null);
        }
    }

    private static final int PAD = 3;
    private static final int ROW = 14;
    private static final int ICON_ROW = 18;
    private static final int TILE = 20;

    private final List<Option<T>> options;
    private final T current;
    private final int columns;
    private final Consumer<T> onPick;
    private final int minWidth;
    private int cellW, cellH, visibleRows, scroll;

    private DVChoicePopup(AbstractWidget anchor, List<Option<T>> options, T current, int columns, Consumer<T> onPick) {
        super(anchor);
        this.options = options;
        this.current = current;
        this.columns = Math.max(1, Math.min(columns, options.size()));
        this.onPick = onPick;
        this.minWidth = anchor.getWidth();
    }

    /** A list menu under {@code anchor}: icon (when given) and name on each row. */
    public static <T> void open(AbstractWidget anchor, List<Option<T>> options, T current, Consumer<T> onPick) {
        Minecraft.getInstance().setScreen(new DVChoicePopup<>(anchor, options, current, 1, onPick));
    }

    /** A grid of icons under {@code anchor}; names show on hover. */
    public static <T> void openGrid(AbstractWidget anchor, List<Option<T>> options, T current, int columns, Consumer<T> onPick) {
        Minecraft.getInstance().setScreen(new DVChoicePopup<>(anchor, options, current, Math.max(2, columns), onPick));
    }

    private boolean grid() {
        return columns > 1;
    }

    private int rows() {
        return (options.size() + columns - 1) / columns;
    }

    @Override
    protected int[] panelSize() {
        boolean icons = options.stream().anyMatch(o -> o.icon() != null);
        if (grid()) {
            cellW = cellH = TILE;
        } else {
            cellH = icons ? ICON_ROW : ROW;
            int text = 0;
            for (Option<T> o : options) text = Math.max(text, font.width(o.name()));
            cellW = Math.max(minWidth - 2 * PAD, text + 8 + (icons ? 18 : 0));
        }
        visibleRows = Math.max(1, Math.min(rows(), (height - 8 - 2 * PAD) / cellH));
        // Start scrolled so the current choice is visible.
        int index = indexOf(current);
        scroll = index < 0 ? 0 : Mth.clamp(index / columns - visibleRows / 2, 0, rows() - visibleRows);
        return new int[]{columns * cellW + 2 * PAD, visibleRows * cellH + 2 * PAD};
    }

    private int indexOf(T value) {
        for (int i = 0; i < options.size(); i++) if (Objects.equals(options.get(i).value(), value)) return i;
        return -1;
    }

    private int hovered(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - panelX - PAD) / cellW);
        int row = (int) Math.floor((mouseY - panelY - PAD) / cellH);
        if (col < 0 || col >= columns || row < 0 || row >= visibleRows) return -1;
        int i = (row + scroll) * columns + col;
        return i < options.size() ? i : -1;
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int hover = hovered(mouseX, mouseY);
        int first = scroll * columns;
        int last = Math.min(options.size(), first + visibleRows * columns);
        for (int i = first; i < last; i++) {
            Option<T> o = options.get(i);
            int x = panelX + PAD + (i % columns) * cellW;
            int y = panelY + PAD + (i / columns - scroll) * cellH;
            boolean selected = Objects.equals(o.value(), current);
            if (i == hover) g.fill(x, y, x + cellW, y + cellH, 0x30FFFFFF);
            if (selected) g.renderOutline(x, y, cellW, cellH, DVUi.accent());
            if (grid()) {
                if (o.icon() != null) o.icon().draw(g, x + cellW / 2, y + cellH / 2);
            } else {
                int textX = x + 4;
                if (o.icon() != null) {
                    o.icon().draw(g, x + 10, y + cellH / 2);
                    textX = x + 22;
                }
                g.drawString(font, o.name(), textX, y + (cellH - 8) / 2, selected ? DVUi.accent() : 0xFFE6E6F0);
            }
        }
        if (rows() > visibleRows) {
            int trackH = visibleRows * cellH;
            int thumbH = Math.max(8, trackH * visibleRows / rows());
            int thumbY = panelY + PAD + (trackH - thumbH) * scroll / (rows() - visibleRows);
            g.fill(panelX + panelW - 2, thumbY, panelX + panelW - 1, thumbY + thumbH, DVUi.accent());
        }
        if (grid() && hover >= 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 100);
            g.renderTooltip(font, options.get(hover).name(), mouseX, mouseY);
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (insidePanel(mouseX, mouseY)) {
            int i = hovered(mouseX, mouseY);
            if (i >= 0) {
                onClose();
                onPick.accept(options.get(i).value());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, rows() - visibleRows));
        return true;
    }
}
