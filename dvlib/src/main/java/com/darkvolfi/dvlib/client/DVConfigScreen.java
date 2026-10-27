package com.darkvolfi.dvlib.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * One config screen for every DV mod: a row per option, on/off buttons for booleans,
 * text boxes for numbers, the spec comment as tooltip. Changes apply on Done.
 */
public class DVConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int TOP = 40;

    private final Screen parent;
    private final String modId;
    private final ForgeConfigSpec clientSpec;
    private final ForgeConfigSpec serverSpec;
    private final Runnable onSave;

    private final List<Row> rows = new ArrayList<>();
    private final Map<ForgeConfigSpec.ConfigValue<?>, Object> pending = new LinkedHashMap<>();
    private final Map<KeyMapping, Button> keyButtons = new LinkedHashMap<>();
    private KeyMapping selectingKey;
    private Component notice;
    private int noticeBaseY;
    private Button doneButton;
    private int scrollOffset;
    private int maxScroll;

    private record Row(Component label, int baseY, AbstractWidget widget) {}

    /**
     * Adds a "Config" button for the calling mod in the mods list. Call from the mod constructor, client side only.
     * {@code serverSpec} may be null; it is only editable inside a singleplayer world.
     */
    public static void register(String modId, ForgeConfigSpec clientSpec, ForgeConfigSpec serverSpec, Runnable onSave) {
        Function<Screen, Screen> factory = parent -> new DVConfigScreen(parent, modId, clientSpec, serverSpec, onSave);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> factory.apply(parent)));
        DVMenuScreen.add(modId, factory);
    }

    public DVConfigScreen(Screen parent, String modId, ForgeConfigSpec clientSpec, ForgeConfigSpec serverSpec, Runnable onSave) {
        super(Component.literal(ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getDisplayName()).orElse(modId)));
        this.parent = parent;
        this.modId = modId;
        this.clientSpec = clientSpec;
        this.serverSpec = serverSpec;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        rows.clear();
        notice = null;
        int y = TOP;
        y = addSpec(clientSpec, y);
        if (serverSpec != null) {
            boolean editable = serverSpec.isLoaded() && Minecraft.getInstance().hasSingleplayerServer();
            if (editable) {
                y = addSpec(serverSpec, y);
            } else {
                notice = Component.translatable("dvlib.config.server_in_world").withStyle(ChatFormatting.GRAY);
                noticeBaseY = y;
                y += ROW_HEIGHT;
            }
        }
        y = addKeys(y);

        int viewBottom = height - 34;
        maxScroll = Math.max(0, y - viewBottom);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(width / 2 - 155, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 + 5, height - 28, 150, 20).build());
        updateScroll();
        updateDone();
    }

    private int addSpec(ForgeConfigSpec spec, int y) {
        if (spec == null || !spec.isLoaded()) return y;
        for (Object entry : spec.getValues().valueMap().values()) {
            if (!(entry instanceof ForgeConfigSpec.ConfigValue<?> value)) continue;
            ForgeConfigSpec.ValueSpec valueSpec = spec.getSpec().get(value.getPath());
            String name = value.getPath().get(value.getPath().size() - 1);
            Tooltip tooltip = valueSpec.getComment() == null ? null : Tooltip.create(Component.literal(valueSpec.getComment()));
            int x = width / 2 + 5;
            AbstractWidget widget;

            if (value instanceof ForgeConfigSpec.BooleanValue bool) {
                widget = addRenderableWidget(CycleButton.onOffBuilder((Boolean) pendingOr(bool))
                        .displayOnlyValue()
                        .withTooltip(v -> tooltip)
                        .create(x, y, 150, 20, Component.empty(), (button, v) -> pending.put(bool, v)));
            } else if (value instanceof ForgeConfigSpec.EnumValue<?> enumVal) {
                widget = addRenderableWidget(createEnumButton(enumVal, name, tooltip, x, y));
            } else {
                EditBox box = new EditBox(font, x + 1, y + 1, 148, 18, Component.literal(name));
                box.setValue(String.valueOf(pendingOr(value)));
                box.setTooltip(tooltip);
                box.setResponder(text -> {
                    Object parsed = parse(value, text);
                    boolean valid = parsed != null && valueSpec.test(parsed);
                    box.setTextColor(valid ? 0xE0E0E0 : 0xFF5555);
                    if (valid) pending.put(value, parsed);
                    else pending.put(value, Invalid.INSTANCE);
                    updateDone();
                });
                widget = addRenderableWidget(box);
            }
            rows.add(new Row(label(name), y, widget));
            y += ROW_HEIGHT;
        }
        return y;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CycleButton<?> createEnumButton(ForgeConfigSpec.EnumValue enumVal, String optionName, Tooltip tooltip, int x, int y) {
        Enum<?> current = (Enum<?>) pendingOr(enumVal);
        Enum<?>[] constants = current.getDeclaringClass().getEnumConstants();
        return CycleButton.<Enum<?>>builder(val -> enumLabel(optionName, val))
                .withValues(constants)
                .withInitialValue(current)
                .displayOnlyValue()
                .withTooltip(v -> tooltip)
                .create(x, y, 150, 20, Component.empty(), (button, v) -> pending.put(enumVal, v));
    }

    private Component enumLabel(String optionName, Enum<?> val) {
        String key = modId + ".config." + optionName + "." + val.name().toLowerCase(Locale.ROOT);
        if (I18n.exists(key)) return Component.translatable(key);
        String words = val.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Component.literal(Character.toUpperCase(words.charAt(0)) + words.substring(1));
    }

    /** The mod's key bindings (named key.<modid>.*), rebound like in the vanilla Controls screen. */
    private int addKeys(int y) {
        keyButtons.clear();
        selectingKey = null;
        for (KeyMapping key : minecraft.options.keyMappings) {
            if (!key.getName().startsWith("key." + modId + ".")) continue;
            Button button = addRenderableWidget(Button.builder(Component.empty(), b -> {
                selectingKey = key;
                refreshKeyButtons();
            }).bounds(width / 2 + 5, y, 150, 20).build());
            keyButtons.put(key, button);
            rows.add(new Row(Component.translatable(key.getName()), y, button));
            y += ROW_HEIGHT;
        }
        refreshKeyButtons();
        return y;
    }

    private void refreshKeyButtons() {
        for (Map.Entry<KeyMapping, Button> e : keyButtons.entrySet()) {
            KeyMapping key = e.getKey();
            Component text = key.getTranslatedKeyMessage();
            if (key == selectingKey) {
                text = Component.literal("> ").append(text.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)).append(" <");
            } else if (conflicts(key)) {
                text = text.copy().withStyle(ChatFormatting.RED);
            }
            e.getValue().setMessage(text);
        }
    }

    private boolean conflicts(KeyMapping key) {
        if (key.isUnbound()) return false;
        for (KeyMapping other : minecraft.options.keyMappings) {
            if (other != key && key.same(other)) return true;
        }
        return false;
    }

    private void bindSelected(InputConstants.Key input) {
        selectingKey.setKeyModifierAndCode(KeyModifier.NONE, input);
        minecraft.options.setKey(selectingKey, input);
        KeyMapping.resetMapping();
        minecraft.options.save();
        selectingKey = null;
        refreshKeyButtons();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectingKey == null) return super.keyPressed(keyCode, scanCode, modifiers);
        bindSelected(keyCode == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(keyCode, scanCode));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectingKey == null) return super.mouseClicked(mouseX, mouseY, button);
        bindSelected(InputConstants.Type.MOUSE.getOrCreate(button));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta) * ROW_HEIGHT, 0, maxScroll);
            updateScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void updateScroll() {
        int viewTop = TOP - 4;
        int viewBottom = height - 34;
        for (Row row : rows) {
            int y = row.baseY - scrollOffset;
            int offset = row.widget instanceof EditBox ? 1 : 0;
            row.widget.setY(y + offset);
            row.widget.visible = y >= viewTop && y + 20 <= viewBottom;
        }
    }

    private enum Invalid { INSTANCE }

    private Object pendingOr(ForgeConfigSpec.ConfigValue<?> value) {
        Object p = pending.get(value);
        return p != null && p != Invalid.INSTANCE ? p : value.get();
    }

    private static Object parse(ForgeConfigSpec.ConfigValue<?> value, String text) {
        try {
            if (value instanceof ForgeConfigSpec.IntValue) return Integer.parseInt(text.trim());
            if (value instanceof ForgeConfigSpec.DoubleValue) return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private Component label(String name) {
        String key = modId + ".config." + name;
        if (I18n.exists(key)) return Component.translatable(key);
        // Fallback: "showModName" -> "Show mod name"
        String words = name.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return Component.literal(Character.toUpperCase(words.charAt(0)) + words.substring(1));
    }

    private void updateDone() {
        if (doneButton != null) doneButton.active = !pending.containsValue(Invalid.INSTANCE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void save() {
        for (Map.Entry<ForgeConfigSpec.ConfigValue<?>, Object> e : pending.entrySet()) {
            ((ForgeConfigSpec.ConfigValue) e.getKey()).set(e.getValue());
        }
        if (clientSpec != null && clientSpec.isLoaded()) clientSpec.save();
        if (serverSpec != null && serverSpec.isLoaded()) serverSpec.save();
        if (onSave != null) onSave.run();
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        int viewTop = TOP - 4;
        int viewBottom = height - 34;
        for (Row row : rows) {
            if (!row.widget.visible) continue;
            int y = row.baseY - scrollOffset;
            g.drawString(font, row.label, width / 2 - 155, y + 6, 0xFFFFFF);
        }
        if (notice != null) {
            int y = noticeBaseY - scrollOffset;
            if (y >= viewTop && y + 20 <= viewBottom) {
                g.drawString(font, notice, width / 2 - 155, y + 6, 0xA0A0A0);
            }
        }
        if (maxScroll > 0) {
            int trackTop = TOP;
            int trackBottom = height - 36;
            int trackHeight = Math.max(20, trackBottom - trackTop);
            int thumbHeight = Math.max(16, trackHeight * trackHeight / (trackHeight + maxScroll));
            int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
            int barX = width / 2 + 160;
            g.fill(barX, trackTop, barX + 3, trackBottom, 0x55000000);
            g.fill(barX, thumbY, barX + 3, thumbY + thumbHeight, 0xFFA0A0A0);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
