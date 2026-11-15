package com.darkvolfi.dvskin.client;

import com.darkvolfi.dvlib.client.DVButton;
import com.darkvolfi.dvlib.client.DVChoicePopup;
import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvlib.client.DVIconButton;
import com.darkvolfi.dvlib.client.DVMods;
import com.darkvolfi.dvlib.client.DVSwitch;
import com.darkvolfi.dvlib.client.DVUi;
import com.darkvolfi.dvskin.DVSkin;
import com.darkvolfi.dvskin.SkinClientConfig;
import com.darkvolfi.dvskin.SkinData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Picking the look: a model on the left (drag to turn it) and Skin / Cape / Elytra tabs on the right, each with a
 * source, its field, and Apply. Pictures are fetched and checked while picking; Apply saves them and sends them on.
 */
public class SkinScreen extends Screen {
    private static final int PAD = 8;
    private static final int MAX_WIDTH = 400;
    private static final int MAX_HEIGHT = 236;
    private static final String[][] KINDS = {
            {"none", "file", "mojang", "elyby", "url"},
            {"none", "dv", "file", "url"},
            {"none", "dv", "file", "url"}};
    private static final String[] SLOT_NAMES = {"skin", "cape", "elytra"};
    private static final ResourceLocation VANILLA_ELYTRA = new ResourceLocation("textures/entity/elytra.png");
    private static final ResourceLocation[] PREVIEW = {
            new ResourceLocation(DVSkin.MOD_ID, "preview/skin"),
            new ResourceLocation(DVSkin.MOD_ID, "preview/cape"),
            new ResourceLocation(DVSkin.MOD_ID, "preview/elytra")};
    /** The picture each preview texture currently shows (compared by identity). */
    private static final byte[][] PREVIEW_PNG = new byte[3][];

    /** The tab last looked at, kept while the game runs. */
    private static int tab = SkinData.SKIN;

    /** What a slot will be once applied: its source, and its own picture (null for none or a DV cape). */
    private record Draft(String source, byte[] png) {}

    private final Screen parent;
    private final Draft[] drafts = new Draft[3];
    private final String[] kind = new String[3];
    private final String[] text = new String[3];
    private boolean slim;
    private float yaw = 25;
    private boolean dragging;
    private int loading = -1;
    private boolean choosingFile;
    private Component status;
    private boolean statusError;

    public SkinScreen(Screen parent) {
        super(Component.translatable("dvskin.title"));
        this.parent = parent;
        for (int slot = 0; slot < 3; slot++) {
            String source = SkinChoice.value(slot).get();
            byte[] png = null;
            if (ownPicture(source)) {
                try {
                    Path file = SkinChoice.file(slot);
                    if (Files.isRegularFile(file)) png = Files.readAllBytes(file);
                } catch (IOException ignored) {
                    // shown as no picture
                }
            }
            drafts[slot] = png == null && ownPicture(source) ? new Draft("", null) : new Draft(source, png);
            int colon = source.indexOf(':');
            kind[slot] = colon < 0 ? "none" : source.substring(0, colon);
            text[slot] = colon < 0 || kind[slot].equals("dv") ? "" : source.substring(colon + 1);
        }
        slim = SkinClientConfig.SLIM.get();
    }

    /** Sources whose picture is a file in config/dvskin (everything but none and DV capes). */
    private static boolean ownPicture(String source) {
        return !source.isEmpty() && !source.startsWith("dv:");
    }

    private static boolean fetched(String kind) {
        return kind.equals("mojang") || kind.equals("elyby") || kind.equals("url");
    }

    private int panelWidth() {
        return Math.min(MAX_WIDTH, width - 20);
    }

    private int panelHeight() {
        return Math.min(MAX_HEIGHT, height - 20);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    private int previewWidth() {
        return Math.min(120, panelWidth() / 3);
    }

    private int rightX() {
        return left() + PAD + previewWidth() + PAD;
    }

    private int rightWidth() {
        return left() + panelWidth() - PAD - rightX();
    }

    @Override
    protected void init() {
        int l = left();
        int t = top();
        int w = panelWidth();
        int rx = rightX();
        int rw = rightWidth();

        addRenderableWidget(new DVIconButton(l + w - 20, t + 6, 14, DVUi.CLOSE, CommonComponents.GUI_DONE, this::onClose));
        DVMods.Mod mod = DVMods.get(DVSkin.MOD_ID);
        if (mod != null) {
            addRenderableWidget(new DVIconButton(l + w - 38, t + 6, 14, DVUi.SERVER, Component.translatable("dvskin.server_settings"),
                    () -> minecraft.setScreen(new DVConfigScreen(this, DVSkin.MOD_ID, mod.clientSpec(), mod.serverSpec(), mod.onSave()))));
        }

        int tabWidth = (rw - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int slot = i;
            addRenderableWidget(new DVButton(rx + i * (tabWidth + 4), t + 24, tabWidth, 16,
                    Component.translatable("dvskin.tab." + SLOT_NAMES[i]), b -> {
                tab = slot;
                rebuildWidgets();
            }));
        }

        int slot = tab;
        addRenderableWidget(new DVButton(rx, t + 46, rw, 16, Component.translatable("dvskin.source", kindName(slot, kind[slot])), b -> {
            List<DVChoicePopup.Option<String>> options = new ArrayList<>();
            for (String k : KINDS[slot]) options.add(new DVChoicePopup.Option<>(k, kindName(slot, k)));
            DVChoicePopup.open(b, options, kind[slot], k -> setKind(slot, k));
        }).menu());

        int y = t + 68;
        switch (kind[slot]) {
            case "file" -> addRenderableWidget(new DVButton(rx, y, 110, 16, Component.translatable("dvskin.choose_file"), b -> chooseFile(slot)));
            case "dv" -> addRenderableWidget(new DVButton(rx, y, rw, 16, capeName(drafts[slot].source()), b -> pickDvCape(b, slot)).menu());
            case "mojang", "elyby", "url" -> {
                EditBox box = addRenderableWidget(new EditBox(font, rx + 1, y + 1, rw - 66, 14, Component.translatable("dvskin.kind." + kind[slot])));
                box.setMaxLength(512);
                box.setValue(text[slot]);
                box.setResponder(v -> text[slot] = v);
                addRenderableWidget(new DVButton(rx + rw - 60, y, 60, 16, Component.translatable("dvskin.load"), b -> load(slot, false)));
            }
            default -> {
            }
        }

        // With the account's own skin the arm model comes with it, so the switch would do nothing
        if (slot == SkinData.SKIN && !kind[slot].equals("none")) {
            addRenderableWidget(new DVSwitch(rx, t + 112, Component.translatable("dvskin.slim"), slim, v -> slim = v));
        }

        DVButton apply = addRenderableWidget(new DVButton(rx + rw - 80, t + panelHeight() - 26, 80, 18,
                Component.translatable("dvskin.apply"), b -> apply()));
        apply.active = loading < 0;
    }

    private Component kindName(int slot, String k) {
        return Component.translatable(k.equals("none") ? "dvskin.kind.none." + SLOT_NAMES[slot] : "dvskin.kind." + k);
    }

    private static Component capeName(String source) {
        if (!source.startsWith("dv:")) return Component.translatable("dvskin.cape.pick");
        String name = source.substring(3);
        if (name.startsWith("color_")) {
            return Component.translatable("dvskin.cape.color", Component.translatable("color.minecraft." + name.substring(6)));
        }
        return Component.translatable("dvskin.cape." + name);
    }

    private void setKind(int slot, String k) {
        kind[slot] = k;
        if (k.equals("none")) {
            drafts[slot] = new Draft("", null);
        } else if (k.equals("dv") && !drafts[slot].source().startsWith("dv:")) {
            List<String> capes = SkinChoice.dvCapes();
            if (!capes.isEmpty()) drafts[slot] = new Draft("dv:" + capes.get(0), null);
        } else if (k.equals("file") && drafts[slot].source().startsWith("file:")) {
            text[slot] = drafts[slot].source().substring(5);
        }
        status = null;
        rebuildWidgets();
    }

    private void pickDvCape(DVButton anchor, int slot) {
        List<DVChoicePopup.Option<String>> options = new ArrayList<>();
        for (String name : SkinChoice.dvCapes()) {
            ResourceLocation texture = SkinChoice.dvCape(name);
            // The cape's front face (10×16 at 1,1 in a 64×32 cape texture)
            options.add(new DVChoicePopup.Option<>(name, capeName("dv:" + name),
                    (g, cx, cy) -> g.blit(texture, cx - 5, cy - 8, 10, 16, 1, 1, 10, 16, 64, 32)));
        }
        if (options.isEmpty()) {
            showError(Component.translatable("dvskin.error.no_dv_capes"));
            return;
        }
        String current = drafts[slot].source().startsWith("dv:") ? drafts[slot].source().substring(3) : null;
        DVChoicePopup.openGrid(anchor, options, current, 8, name -> {
            drafts[slot] = new Draft("dv:" + name, null);
            rebuildWidgets();
        });
    }

    private void chooseFile(int slot) {
        if (choosingFile) return;
        choosingFile = true;
        String title = Component.translatable("dvskin.choose_file").getString();
        // The system dialog blocks until closed, so it runs beside the game instead of freezing it
        Thread thread = new Thread(() -> {
            String path = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                path = TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, "PNG", false);
            } catch (Throwable e) {
                // No dialog on this system: files can still be dropped on the window
            }
            String chosen = path;
            minecraft.execute(() -> {
                choosingFile = false;
                if (chosen != null) {
                    text[slot] = chosen;
                    load(slot, false);
                }
            });
        }, "DV Skin file dialog");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths.isEmpty()) return;
        kind[tab] = "file";
        text[tab] = paths.get(0).toString();
        load(tab, false);
    }

    /** Fetches the slot's picture (from its file, name or link) into the draft; then applies when asked. */
    private void load(int slot, boolean thenApply) {
        if (loading >= 0) return;
        String k = kind[slot];
        String input = text[slot].trim();
        loading = slot;
        showStatus(Component.translatable("dvskin.status.loading"), false);
        rebuildWidgets();
        SkinDownloader.fetch(k, input).whenComplete((result, error) -> minecraft.execute(() -> {
            loading = -1;
            if (error != null) {
                showError(Component.translatable("dvskin.error." + SkinDownloader.cause(error).key));
            } else {
                try {
                    drafts[slot] = new Draft(k + ":" + input, SkinImages.normalize(result.png(), slot));
                    if (slot == SkinData.SKIN && result.slim() != null) slim = result.slim();
                    showStatus(Component.translatable("dvskin.status.loaded"), false);
                    if (thenApply) {
                        rebuildWidgets();
                        apply();
                        return;
                    }
                } catch (SkinException e) {
                    showError(Component.translatable("dvskin.error." + e.key));
                }
            }
            rebuildWidgets();
        }));
    }

    /** True when the slot's draft is what its source and field now say. */
    private boolean upToDate(int slot) {
        String source = drafts[slot].source();
        return switch (kind[slot]) {
            case "none" -> source.isEmpty();
            case "dv" -> source.startsWith("dv:");
            default -> source.equals(kind[slot] + ":" + text[slot].trim()) && drafts[slot].png() != null;
        };
    }

    private void apply() {
        if (loading >= 0) return;
        for (int slot = 0; slot < 3; slot++) {
            if (upToDate(slot)) continue;
            if (kind[slot].equals("dv")) {
                showError(Component.translatable("dvskin.error.no_dv_capes"));
                return;
            }
            tab = slot;
            if (kind[slot].equals("file") && text[slot].isBlank()) {
                showError(Component.translatable("dvskin.no_file"));
                rebuildWidgets();
                return;
            }
            // A name or link typed but not loaded yet: load it first, then come back here
            load(slot, true);
            return;
        }
        try {
            for (int slot = 0; slot < 3; slot++) {
                Draft d = drafts[slot];
                SkinChoice.save(slot, d.source(), ownPicture(d.source()) ? d.png() : null);
            }
            SkinClientConfig.SLIM.set(slim);
            SkinClientConfig.SPEC.save();
        } catch (IOException | RuntimeException e) {
            showError(Component.translatable("dvskin.error.save"));
            return;
        }
        SkinClient.applyOwn();
        showStatus(Component.translatable(minecraft.getConnection() == null ? "dvskin.status.saved" : "dvskin.status.applied"), false);
    }

    private void showStatus(Component message, boolean error) {
        status = message;
        statusError = error;
    }

    public void showError(Component message) {
        showStatus(message, true);
    }

    // --- preview ---

    private UUID ownId() {
        return minecraft.player != null ? minecraft.player.getUUID() : minecraft.getUser().getProfileId();
    }

    private ResourceLocation previewTexture(int slot, byte[] png) {
        if (png != PREVIEW_PNG[slot]) {
            try {
                minecraft.getTextureManager().register(PREVIEW[slot], new DynamicTexture(SkinImages.read(png, slot)));
                PREVIEW_PNG[slot] = png;
            } catch (SkinException e) {
                return null;
            }
        }
        return PREVIEW[slot];
    }

    private ResourceLocation skinTexture() {
        Draft d = drafts[SkinData.SKIN];
        if (d.png() != null && !d.source().isEmpty()) {
            ResourceLocation id = previewTexture(SkinData.SKIN, d.png());
            if (id != null) return id;
        }
        UUID id = ownId();
        if (minecraft.player != null && !SkinTextures.has(id, SkinData.SKIN)) return minecraft.player.getSkinTextureLocation();
        return id == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.getDefaultSkin(id);
    }

    private boolean slimArms() {
        Draft d = drafts[SkinData.SKIN];
        if (d.png() != null && !d.source().isEmpty()) return slim;
        UUID id = ownId();
        if (minecraft.player != null && !SkinTextures.has(id, SkinData.SKIN)) return "slim".equals(minecraft.player.getModelName());
        return id != null && "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
    }

    /** A slot's chosen texture; null when it is "none". */
    private ResourceLocation chosen(int slot) {
        Draft d = drafts[slot];
        if (d.source().startsWith("dv:")) return SkinChoice.dvCape(d.source().substring(3));
        if (d.png() != null && !d.source().isEmpty()) return previewTexture(slot, d.png());
        return null;
    }

    private ResourceLocation capeTexture() {
        ResourceLocation cape = chosen(SkinData.CAPE);
        if (cape != null || !drafts[SkinData.CAPE].source().isEmpty()) return cape;
        // None: a real Mojang cape stays
        if (minecraft.player != null && !SkinTextures.has(ownId(), SkinData.CAPE)) return minecraft.player.getCloakTextureLocation();
        return null;
    }

    private ResourceLocation wingsTexture() {
        ResourceLocation wings = chosen(SkinData.ELYTRA);
        if (wings != null) return wings;
        ResourceLocation cape = capeTexture();
        return cape != null ? cape : VANILLA_ELYTRA; // vanilla: the elytra follows the cape
    }

    private boolean inPreview(double mouseX, double mouseY) {
        int x = left() + PAD;
        int y = top() + 24;
        return mouseX >= x && mouseX < x + previewWidth() && mouseY >= y && mouseY < top() + panelHeight() - PAD;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && inPreview(mouseX, mouseY)) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            yaw += (float) dragX * 2;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && getFocused() instanceof EditBox) {
            load(tab, false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int l = left();
        int t = top();
        int w = panelWidth();
        int h = panelHeight();
        int rx = rightX();
        int rw = rightWidth();
        DVUi.panel(g, l, t, w, h, false);
        g.drawString(font, title, l + PAD, t + 9, 0xFFE6E6F0);

        // Preview
        int px = l + PAD;
        int pw = previewWidth();
        int top = t + 24;
        int bottom = t + h - PAD;
        g.fill(px, top, px + pw, bottom, 0x40000000);
        float scale = Math.min((bottom - top - 24) / 2.1f, pw / 1.3f);
        SkinPreview.render(g, px + pw / 2, bottom - 16, scale, yaw, skinTexture(), slimArms(), capeTexture(),
                tab == SkinData.ELYTRA ? wingsTexture() : null);
        g.drawCenteredString(font, Component.translatable("dvskin.drag"), px + pw / 2, bottom - 11, 0xFF6A6478);

        // Selected tab
        int tabWidth = (rw - 8) / 3;
        int tx = rx + tab * (tabWidth + 4);
        g.fill(tx + 2, t + 41, tx + tabWidth - 2, t + 43, DVUi.accent());

        int y = t + 70;
        int slot = tab;
        switch (kind[slot]) {
            case "none" -> wrapped(g, Component.translatable("dvskin.none." + SLOT_NAMES[slot]), rx, y, rw, 0xFFB0A8C0);
            case "file" -> {
                String source = drafts[slot].source();
                Component file = source.startsWith("file:") ? Component.literal(Path.of(source.substring(5)).getFileName().toString())
                        : Component.translatable("dvskin.no_file");
                g.drawString(font, font.plainSubstrByWidth(file.getString(), rw - 116), rx + 116, y + 2, 0xFFB0A8C0);
                wrapped(g, Component.translatable("dvskin.hint.file"), rx, y + 22, rw, 0xFF6A6478);
            }
            case "dv" -> wrapped(g, Component.translatable("dvskin.hint.dv"), rx, y + 20, rw, 0xFF6A6478);
            default -> wrapped(g, Component.translatable("dvskin.hint." + kind[slot]), rx, y + 20, rw, 0xFF6A6478);
        }
        if (slot == SkinData.SKIN && !kind[slot].equals("none")) {
            g.drawString(font, Component.translatable("dvskin.slim"), rx + DVSwitch.WIDTH + 6, t + 114, 0xFFE6E6F0);
        }

        // Status, and a note when the server can't share the look
        if (status != null) {
            wrapped(g, status, rx, t + h - 50, rw, statusError ? 0xFFFF8080 : 0xFF80E080);
        }
        if (minecraft.getConnection() != null && !SkinClient.serverHasMod()) {
            wrapped(g, Component.translatable("dvskin.local_only"), rx, t + h - 26, rw - 86, 0xFF6A6478);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void wrapped(GuiGraphics g, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            g.drawString(font, line, x, y, color);
            y += 10;
        }
    }

    @Override
    public void onClose() {
        for (int slot = 0; slot < 3; slot++) {
            if (PREVIEW_PNG[slot] != null) minecraft.getTextureManager().release(PREVIEW[slot]);
            PREVIEW_PNG[slot] = null;
        }
        minecraft.setScreen(parent);
    }
}
