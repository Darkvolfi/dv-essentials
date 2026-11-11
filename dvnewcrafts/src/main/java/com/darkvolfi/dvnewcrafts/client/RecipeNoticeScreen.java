package com.darkvolfi.dvnewcrafts.client;

import com.darkvolfi.dvnewcrafts.network.NewCraftsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * What the Config button and the DV menu open. The editor itself is a server menu, so this asks the server to open
 * it and says "opening..." meanwhile, or explains why it can't (no world, not an operator, server without the mod).
 */
public class RecipeNoticeScreen extends Screen {
    private final Screen parent;
    private final Component message;
    private MultiLineLabel label = MultiLineLabel.EMPTY;

    private RecipeNoticeScreen(Screen parent, Component message) {
        super(Component.translatable("dvnewcrafts.recipes.title"));
        this.parent = parent;
        this.message = message;
    }

    public static Screen open(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        String problem = null;
        if (mc.player == null || mc.getConnection() == null) {
            problem = "dvnewcrafts.recipes.no_world";
        } else if (!mc.player.hasPermissions(NewCraftsNetwork.PERMISSION)) {
            problem = "dvnewcrafts.recipes.no_permission";
        } else if (!NewCraftsNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) {
            problem = "dvnewcrafts.recipes.no_server";
        }
        if (problem == null) NewCraftsNetwork.CHANNEL.sendToServer(new NewCraftsNetwork.OpenEditor());
        return new RecipeNoticeScreen(parent, Component.translatable(problem == null ? "dvnewcrafts.recipes.opening" : problem));
    }

    @Override
    protected void init() {
        label = MultiLineLabel.create(font, message, width - 50);
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(width / 2 - 100, height / 2 + 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFFFFF);
        label.renderCentered(g, width / 2, height / 2 - 15);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** Lets a singleplayer world keep running so the server can open the editor. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
