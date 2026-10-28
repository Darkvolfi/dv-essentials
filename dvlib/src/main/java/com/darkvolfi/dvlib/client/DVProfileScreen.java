package com.darkvolfi.dvlib.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/** Save the current DV settings under a name, or load / delete a saved profile. */
public class DVProfileScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int TOP = 70;

    private final Screen parent;
    private EditBox nameBox;
    private Button saveButton;
    private String name = "";
    private Component message;
    private int scroll;
    private List<String> profiles = List.of();

    public DVProfileScreen(Screen parent) {
        super(Component.translatable("dvlib.profiles.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        profiles = DVProfiles.list();
        int visible = Math.max(1, (height - 34 - TOP) / ROW_HEIGHT);
        scroll = Mth.clamp(scroll, 0, Math.max(0, profiles.size() - visible));

        nameBox = addRenderableWidget(new EditBox(font, width / 2 - 155, 31, 228, 18, Component.translatable("dvlib.profiles.name")));
        nameBox.setHint(Component.translatable("dvlib.profiles.name").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setMaxLength(32);
        nameBox.setValue(name);
        nameBox.setResponder(text -> {
            name = text;
            updateSave();
        });
        saveButton = addRenderableWidget(Button.builder(Component.translatable("dvlib.profiles.save"), b -> saveProfile())
                .bounds(width / 2 + 77, 30, 78, 20).build());
        updateSave();

        for (int i = 0; i < visible && scroll + i < profiles.size(); i++) {
            String profile = profiles.get(scroll + i);
            int y = TOP + i * ROW_HEIGHT;
            addRenderableWidget(Button.builder(Component.translatable("dvlib.profiles.load"), b -> loadProfile(profile))
                    .bounds(width / 2 + 5, y, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("dvlib.profiles.delete"), b -> confirmDelete(profile))
                    .bounds(width / 2 + 81, y, 74, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("dvlib.profiles.folder"),
                        b -> DVProfiles.openFolder())
                .bounds(width / 2 - 155, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 + 5, height - 28, 150, 20).build());
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
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        if (message != null) g.drawCenteredString(font, message, width / 2, 55, 0xFFFFFF);
        if (profiles.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("dvlib.profiles.empty"), width / 2, TOP + 6, 0xA0A0A0);
        }
        int visible = Math.max(1, (height - 34 - TOP) / ROW_HEIGHT);
        for (int i = 0; i < visible && scroll + i < profiles.size(); i++) {
            g.drawString(font, profiles.get(scroll + i), width / 2 - 155, TOP + i * ROW_HEIGHT + 6, 0xFFFFFF);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
