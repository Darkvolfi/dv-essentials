package com.darkvolfi.dvlib.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/** Save the current DV settings under a name, or load / delete a saved profile. A DV panel with icon buttons. */
public class DVProfileScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int PAD = 12;
    private static final int ICON = 14;
    private static final int PANEL_W = 260;

    private final Screen parent;
    private DVIconButton saveButton;
    private String name = "";
    private Component message;
    private int scroll;
    private List<String> profiles = List.of();

    public DVProfileScreen(Screen parent) {
        super(Component.translatable("dvlib.profiles.title"));
        this.parent = parent;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int rowsTop() {
        return 8 + 62;
    }

    private int visibleRows() {
        return Math.max(1, (height - 8 - 6 - rowsTop()) / ROW_HEIGHT);
    }

    @Override
    protected void init() {
        profiles = DVProfiles.list();
        scroll = Mth.clamp(scroll, 0, Math.max(0, profiles.size() - visibleRows()));
        int l = left();
        int r = l + PANEL_W;

        addRenderableWidget(new DVIconButton(r - 20, 14, ICON, DVUi.CLOSE, CommonComponents.GUI_DONE, this::onClose));
        addRenderableWidget(new DVIconButton(r - 38, 14, ICON, DVUi.FOLDER, Component.translatable("dvlib.profiles.folder"), DVProfiles::openFolder));

        EditBox nameBox = addRenderableWidget(new EditBox(font, l + PAD, 34, PANEL_W - 2 * PAD - ICON - 4, 16, Component.translatable("dvlib.profiles.name")));
        nameBox.setHint(Component.translatable("dvlib.profiles.name").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setMaxLength(32);
        nameBox.setValue(name);
        nameBox.setResponder(text -> {
            name = text;
            updateSave();
        });
        saveButton = addRenderableWidget(new DVIconButton(r - PAD - ICON, 35, ICON, DVUi.PROFILES, Component.translatable("dvlib.profiles.save"), this::saveProfile));
        updateSave();

        for (int i = 0; i < visibleRows() && scroll + i < profiles.size(); i++) {
            String profile = profiles.get(scroll + i);
            int y = rowsTop() + i * ROW_HEIGHT + (ROW_HEIGHT - ICON) / 2;
            addRenderableWidget(new DVIconButton(r - PAD - ICON, y, ICON, DVUi.CLOSE, Component.translatable("dvlib.profiles.delete"), () -> confirmDelete(profile)));
            addRenderableWidget(new DVIconButton(r - PAD - 2 * ICON - 2, y, ICON, DVUi.LOAD, Component.translatable("dvlib.profiles.load"), () -> loadProfile(profile)));
        }
    }

    private void updateSave() {
        if (saveButton != null) saveButton.active = !DVProfiles.clean(name).isEmpty();
    }

    private void saveProfile() {
        String clean = DVProfiles.clean(name);
        message = DVProfiles.save(clean)
                ? Component.translatable("dvlib.profiles.saved", clean).withStyle(ChatFormatting.GREEN)
                : Component.translatable("dvlib.profiles.error").withStyle(ChatFormatting.RED);
        rebuildWidgets();
    }

    private void loadProfile(String profile) {
        int changed = DVProfiles.load(profile);
        message = changed < 0
                ? Component.translatable("dvlib.profiles.error").withStyle(ChatFormatting.RED)
                : Component.translatable("dvlib.profiles.loaded", profile, changed).withStyle(ChatFormatting.GREEN);
        name = profile;
        rebuildWidgets();
    }

    private void confirmDelete(String profile) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                DVProfiles.delete(profile);
                message = Component.translatable("dvlib.profiles.deleted", profile).withStyle(ChatFormatting.GRAY);
            }
            minecraft.setScreen(this);
        }, Component.translatable("dvlib.profiles.delete_title"), Component.literal(profile)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll -= (int) Math.signum(delta);
        rebuildWidgets();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int l = left();
        DVUi.panel(g, l, 8, PANEL_W, height - 16, false);
        g.drawString(font, title, l + PAD, 17, 0xFFE6E6F0);
        if (message != null) g.drawCenteredString(font, message, l + PANEL_W / 2, 54, 0xFFFFFF);
        g.hLine(l + PAD, l + PANEL_W - PAD, rowsTop() - 3, DVUi.rim());
        if (profiles.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("dvlib.profiles.empty"), l + PANEL_W / 2, rowsTop() + 6, 0xA0A0A0);
        }
        for (int i = 0; i < visibleRows() && scroll + i < profiles.size(); i++) {
            int y = rowsTop() + i * ROW_HEIGHT;
            if (mouseY >= y && mouseY < y + ROW_HEIGHT && mouseX >= l && mouseX < l + PANEL_W) {
                g.fill(l + 4, y, l + PANEL_W - 4, y + ROW_HEIGHT, 0x22FFFFFF);
            }
            g.drawString(font, profiles.get(scroll + i), l + PAD, y + 6, 0xFFE6E6F0);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
