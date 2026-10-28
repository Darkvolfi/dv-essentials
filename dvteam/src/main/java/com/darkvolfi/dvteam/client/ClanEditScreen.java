package com.darkvolfi.dvteam.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVChoicePopup;
import com.darkvolfi.dvlib.client.DVColorPicker;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvteam.data.BannerData;
import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.network.ClanActionPacket;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Founding a clan, or (for its leader) changing name, tag, colour and flag. */
public class ClanEditScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 230;

    private final Screen parent;
    private final ClanData.Clan editing;
    private final List<Holder<BannerPattern>> patterns = BannerData.choosablePatterns();

    private String name;
    private String tag;
    private int color;
    private DyeColor base;
    /** Each layer: {pattern index, dye id}. */
    private final List<int[]> layers = new ArrayList<>();

    public ClanEditScreen(Screen parent, ClanData.Clan editing) {
        super(Component.translatable(editing == null ? "dvteam.edit.create_title" : "dvteam.edit.edit_title"));
        this.parent = parent;
        this.editing = editing;
        if (editing != null) {
            name = editing.name;
            tag = editing.tag;
            color = editing.color;
            base = editing.banner.base;
            for (BannerData.Layer l : editing.banner.layers) {
                int idx = indexOf(l.pattern());
                if (idx >= 0) layers.add(new int[]{idx, l.color().getId()});
            }
        } else {
            name = "";
            tag = "";
            color = 0x4FC3F7;
            base = DyeColor.WHITE;
        }
    }

    private int indexOf(String hash) {
        for (int i = 0; i < patterns.size(); i++) if (patterns.get(i).value().getHashname().equals(hash)) return i;
        return -1;
    }

    private BannerData banner() {
        List<BannerData.Layer> list = new ArrayList<>();
        for (int[] l : layers) list.add(new BannerData.Layer(patterns.get(l[0]).value().getHashname(), DyeColor.byId(l[1])));
        return new BannerData(base, list);
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        int l = left() + 10;
        int t = top() + 24;

        EditBox nameBox = new EditBox(font, l, t + 10, 150, 18, Component.translatable("dvteam.edit.name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);

        EditBox tagBox = new EditBox(font, l, t + 42, 60, 18, Component.translatable("dvteam.edit.tag"));
        tagBox.setMaxLength(4);
        tagBox.setValue(tag);
        tagBox.setResponder(v -> tag = v.toUpperCase(Locale.ROOT));
        addRenderableWidget(tagBox);

        addRenderableWidget(new DVColorPicker(l, t + 76, 150, 92, color, c -> color = c));

        // Flag: base colour, then up to six pattern layers.
        int r = left() + 176;
        addRenderableWidget(DVButton.builder(Component.translatable("dvteam.edit.base"), b ->
                DVChoicePopup.openGrid(b, dyeOptions(), base, 8, picked -> base = picked))
                .bounds(r + 30, t + 10, 100, 18).build().menu());

        for (int i = 0; i < layers.size(); i++) {
            int idx = i;
            int y = t + 32 + i * 20;
            addRenderableWidget(DVButton.builder(layerLabel(layers.get(idx)), b -> {
                List<DVChoicePopup.Option<Integer>> options = new ArrayList<>();
                DyeColor dye = DyeColor.byId(layers.get(idx)[1]);
                for (int p = 0; p < patterns.size(); p++) {
                    options.add(new DVChoicePopup.Option<>(p, patternName(patterns.get(p), dye)));
                }
                DVChoicePopup.open(b, options, layers.get(idx)[0], picked -> {
                    layers.get(idx)[0] = picked;
                    b.setMessage(layerLabel(layers.get(idx)));
                });
            }).bounds(r + 30, y, 98, 18).build().menu());
            addRenderableWidget(DVButton.builder(Component.empty(), b ->
                    DVChoicePopup.openGrid(b, dyeOptions(), DyeColor.byId(layers.get(idx)[1]), 8, picked -> {
                        layers.get(idx)[1] = picked.getId();
                        // The pattern button's label names the colour too.
                        rebuildWidgets();
                    })).bounds(r + 130, y, 18, 18).build());
            addRenderableWidget(DVButton.builder(Component.literal("×"), b -> {
                layers.remove(idx);
                rebuildWidgets();
            }).bounds(r + 156, y, 14, 18).build());
        }
        if (layers.size() < BannerData.MAX_LAYERS) {
            addRenderableWidget(DVButton.builder(Component.translatable("dvteam.edit.add_layer"), b -> {
                layers.add(new int[]{0, DyeColor.BLACK.getId()});
                rebuildWidgets();
            }).bounds(r + 30, t + 32 + layers.size() * 20, 140, 18).build());
        }

        addRenderableWidget(DVButton.builder(editing == null ? Component.translatable("dvteam.edit.found") : CommonComponents.GUI_DONE,
                b -> save()).bounds(width / 2 - 104, top() + PANEL_H - 26, 100, 20).build());
        addRenderableWidget(DVButton.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 + 4, top() + PANEL_H - 26, 100, 20).build());
    }

    /** The 16 dye colours as swatches. */
    private static List<DVChoicePopup.Option<DyeColor>> dyeOptions() {
        List<DVChoicePopup.Option<DyeColor>> options = new ArrayList<>();
        for (DyeColor dye : DyeColor.values()) {
            int rgb = 0xFF000000 | dye.getTextColor();
            options.add(new DVChoicePopup.Option<>(dye, Component.translatable("color.minecraft." + dye.getName()),
                    (g, cx, cy) -> g.fill(cx - 6, cy - 6, cx + 6, cy + 6, rgb)));
        }
        return options;
    }

    private Component layerLabel(int[] layer) {
        Component name = patternName(patterns.get(layer[0]), DyeColor.byId(layer[1]));
        return Component.literal(font.plainSubstrByWidth(name.getString(), 72));
    }

    private void save() {
        CompoundTag fields = new CompoundTag();
        fields.putString("Name", name);
        fields.putString("Tag", tag);
        fields.putInt("Color", color);
        fields.put("Banner", banner().save());
        TeamNetwork.CHANNEL.sendToServer(ClanActionPacket.fields(
                editing == null ? ClanActionPacket.Action.CREATE : ClanActionPacket.Action.EDIT, fields));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int l = left();
        int t = top();
        DVUi.panel(g, l, t, PANEL_W, PANEL_H, false);
        g.renderOutline(l, t, PANEL_W, PANEL_H, 0xFF000000 | color);
        g.drawCenteredString(font, title, width / 2, t + 8, 0xFFFFFF);

        int x = l + 10;
        int y = t + 24;
        g.drawString(font, Component.translatable("dvteam.edit.name"), x, y, 0xCCCCCC);
        g.drawString(font, Component.translatable("dvteam.edit.tag"), x, y + 32, 0xCCCCCC);
        g.drawString(font, Component.literal("[" + tag + "] " + name), x + 66, y + 47, 0xFF000000 | color);
        g.drawString(font, Component.translatable("dvteam.edit.color"), x, y + 66, 0xCCCCCC);

        super.render(g, mouseX, mouseY, partialTick);

        // Flag preview, then the per-layer labels and dye swatches over their buttons.
        int r = l + 176;
        BannerView.draw(g, banner(), r, y + 10, 50);
        g.fill(r + 134, y + 13, r + 146, y + 25, 0xFF000000 | base.getTextColor());
        for (int i = 0; i < layers.size(); i++) {
            int[] layer = layers.get(i);
            DyeColor dye = DyeColor.byId(layer[1]);
            int ly = y + 32 + i * 20;
            g.fill(r + 133, ly + 3, r + 145, ly + 15, 0xFF000000 | dye.getTextColor());
        }
    }

    private static Component patternName(Holder<BannerPattern> pattern, DyeColor dye) {
        String id = pattern.unwrapKey().map(k -> k.location().toShortLanguageKey()).orElse("base");
        return Component.translatable("block.minecraft.banner." + id + "." + dye.getName());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
