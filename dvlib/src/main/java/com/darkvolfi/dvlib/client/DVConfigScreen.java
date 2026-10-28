package com.darkvolfi.dvlib.client;

import com.darkvolfi.dvlib.DVLib;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * One config screen for every DV mod, in DV's panel look: a row per option with a switch for on/off, a slider for
 * small number ranges, a text box for the rest, and the spec comment as tooltip. Server options and key bindings
 * get their own headed sections. Changes apply with the check mark; a search box hides rows that don't match.
 */
public class DVConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int PAD = 12;
    private static final int CONTROL_WIDTH = 120;
    /** Number ranges up to this wide get a slider; wider ones (e.g. 1 to 100000) a text box. */
    private static final int SLIDER_MAX_SPAN = 200;

    private final Screen parent;
    private final String modId;
    private final ForgeConfigSpec clientSpec;
    private final ForgeConfigSpec serverSpec;
    private final Runnable onSave;

    private final List<Row> rows = new ArrayList<>();
    private final Map<ForgeConfigSpec.ConfigValue<?>, Object> pending = new LinkedHashMap<>();
    private final Map<KeyMapping, DVButton> keyButtons = new LinkedHashMap<>();
    private KeyMapping selectingKey;
    private DVIconButton doneButton;
    private String query;
    private int scrollOffset;
    private int maxScroll;

    /** An option row, or a section header when {@code widget} is null. */
    private static final class Row {
        final Component label;
        final AbstractWidget widget;
        final String[] icon;
        int baseY;
        boolean shown;

        Row(Component label, AbstractWidget widget) {
            this(label, widget, null);
        }

        Row(Component label, AbstractWidget widget, String[] icon) {
            this.label = label;
            this.widget = widget;
            this.icon = icon;
        }

        boolean header() {
            return widget == null;
        }
    }

    /**
     * Adds a "Config" button for the calling mod in the mods list. Call from the mod constructor, client side only.
     * {@code serverSpec} may be null; it is only editable inside a singleplayer world.
     */
    public static void register(String modId, ForgeConfigSpec clientSpec, ForgeConfigSpec serverSpec, Runnable onSave) {
        Function<Screen, Screen> factory = parent -> new DVConfigScreen(parent, modId, clientSpec, serverSpec, onSave);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> factory.apply(parent)));
        DVMods.add(new DVMods.Mod(modId, clientSpec, serverSpec, onSave));
    }

    public DVConfigScreen(Screen parent, String modId, ForgeConfigSpec clientSpec, ForgeConfigSpec serverSpec, Runnable onSave) {
        this(parent, modId, clientSpec, serverSpec, onSave, "");
    }

    /** Opens with {@code query} already typed into the search box. */
    public DVConfigScreen(Screen parent, String modId, ForgeConfigSpec clientSpec, ForgeConfigSpec serverSpec, Runnable onSave,
                          String query) {
        super(Component.literal(ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getDisplayName()).orElse(modId)));
        this.parent = parent;
        this.modId = modId;
        this.clientSpec = clientSpec;
        this.serverSpec = serverSpec;
        this.onSave = onSave;
        this.query = query;
    }

    private int panelWidth() {
        return Math.min(width - 20, 340);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int top() {
        return 8;
    }

    private int bottom() {
        return height - 8;
    }

    /** Where option rows start and end (scrolled between these). */
    private int viewTop() {
        return top() + 48;
    }

    private int viewBottom() {
        return bottom() - 6;
    }

    private int controlX() {
        return left() + panelWidth() - PAD - 6 - CONTROL_WIDTH;
    }

    @Override
    protected void init() {
        rows.clear();
        int l = left();
        int w = panelWidth();

        doneButton = addRenderableWidget(new DVIconButton(l + w - 38, top() + 6, 14, DVUi.CHECK, CommonComponents.GUI_DONE, this::save));
        addRenderableWidget(new DVIconButton(l + w - 20, top() + 6, 14, DVUi.CLOSE, CommonComponents.GUI_CANCEL, this::onClose));

        EditBox search = addRenderableWidget(new EditBox(font, l + PAD + 14, top() + 26, w - 2 * PAD - 14, 16, Component.translatable("dvlib.search")));
        search.setHint(Component.translatable("dvlib.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            scrollOffset = 0;
            layout();
        });

        addSpec(clientSpec);
        if (serverSpec != null) {
            rows.add(new Row(Component.translatable("dvlib.config.server"), null, DVUi.SERVER));
            if (serverSpec.isLoaded() && Minecraft.getInstance().hasSingleplayerServer()) {
                addSpec(serverSpec);
            } else {
                rows.add(new Row(Component.translatable("dvlib.config.server_in_world").withStyle(ChatFormatting.GRAY), null));
            }
        }
        addKeys();
        layout();
        updateDone();
    }

    /** Stacks the rows matching the search from the top and recomputes the scroll range. Headers show only unfiltered. */
    private void layout() {
        int y = viewTop();
        for (Row row : rows) {
            row.shown = row.header() ? query.isEmpty() : query.isEmpty() || DVMods.contains(row.label.getString(), query);
            if (!row.shown) continue;
            row.baseY = y;
            y += ROW_HEIGHT;
        }
        maxScroll = Math.max(0, y - viewBottom());
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        updateScroll();
    }

    private void addSpec(ForgeConfigSpec spec) {
        if (spec == null || !spec.isLoaded()) return;
        for (Object entry : spec.getValues().valueMap().values()) {
            if (!(entry instanceof ForgeConfigSpec.ConfigValue<?> value)) continue;
            ForgeConfigSpec.ValueSpec valueSpec = spec.getSpec().get(value.getPath());
            String name = DVMods.optionName(value);
            Component label = Component.literal(DVMods.optionLabel(modId, name));
            Tooltip tooltip = valueSpec.getComment() == null ? null : Tooltip.create(Component.literal(valueSpec.getComment()));
            AbstractWidget widget;

            if (value instanceof ForgeConfigSpec.BooleanValue bool) {
                widget = new DVSwitch(left() + panelWidth() - PAD - 6 - DVSwitch.WIDTH, 0, label, (Boolean) pendingOr(bool), v -> pending.put(bool, v));
            } else if (value instanceof ForgeConfigSpec.EnumValue<?> enumVal) {
                widget = createEnumButton(enumVal, name);
            } else if (valueSpec.getDefault() instanceof String def && DVColorPopup.parseHex(def) >= 0
                    && pendingOr(value) instanceof String current) {
                // A "#RRGGBB" text option is a colour: pick it instead of typing it.
                int rgb = DVColorPopup.parseHex(current);
                widget = new DVColorButton(controlX(), 0, CONTROL_WIDTH, 16, rgb < 0 ? DVColorPopup.parseHex(def) : rgb,
                        c -> pending.put(value, DVColorPopup.hex(c)));
            } else if (value instanceof ForgeConfigSpec.IntValue intVal && smallRange(valueSpec)) {
                ForgeConfigSpec.Range<Integer> range = valueSpec.getRange();
                widget = new DVSlider(controlX(), 0, CONTROL_WIDTH, range.getMin(), range.getMax(), (Integer) pendingOr(intVal),
                        v -> pending.put(intVal, v));
            } else {
                EditBox box = new EditBox(font, controlX() + 1, 0, CONTROL_WIDTH - 2, 16, label);
                box.setMaxLength(1024);
                Object current = pendingOr(value);
                box.setValue(current instanceof List<?> list
                        ? String.join(", ", list.stream().map(String::valueOf).toList()) : String.valueOf(current));
                box.setResponder(text -> {
                    Object parsed = parse(value, text);
                    boolean valid = parsed != null && valueSpec.test(parsed);
                    box.setTextColor(valid ? 0xE0E0E0 : 0xFF5555);
                    if (valid) pending.put(value, parsed);
                    else pending.put(value, Invalid.INSTANCE);
                    updateDone();
                });
                widget = box;
            }
            widget.setTooltip(tooltip);
            rows.add(new Row(label, addRenderableWidget(widget)));
        }
    }

    private static boolean smallRange(ForgeConfigSpec.ValueSpec spec) {
        ForgeConfigSpec.Range<Integer> range = spec.getRange();
        return range != null && range.getMax() - range.getMin() <= SLIDER_MAX_SPAN;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private DVButton createEnumButton(ForgeConfigSpec.EnumValue enumVal, String optionName) {
        Enum<?> current = (Enum<?>) pendingOr(enumVal);
        Enum<?>[] constants = current.getDeclaringClass().getEnumConstants();
        List<DVChoicePopup.Option<Enum<?>>> options = new ArrayList<>();
        for (Enum<?> c : constants) options.add(new DVChoicePopup.Option<>(c, enumLabel(optionName, c)));
        return new DVButton(controlX(), 0, CONTROL_WIDTH, 16, enumLabel(optionName, current), b ->
                DVChoicePopup.open(b, options, (Enum<?>) pendingOr(enumVal), picked -> {
                    pending.put(enumVal, picked);
                    b.setMessage(enumLabel(optionName, picked));
                })).menu();
    }

    private Component enumLabel(String optionName, Enum<?> val) {
        String key = modId + ".config." + optionName + "." + val.name().toLowerCase(Locale.ROOT);
        if (I18n.exists(key)) return Component.translatable(key);
        String words = val.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Component.literal(Character.toUpperCase(words.charAt(0)) + words.substring(1));
    }

    /** The mod's key bindings (named key.<modid>.*), rebound like in the vanilla Controls screen. */
    private void addKeys() {
        keyButtons.clear();
        selectingKey = null;
        boolean first = true;
        for (KeyMapping key : minecraft.options.keyMappings) {
            if (!key.getName().startsWith("key." + modId + ".")) continue;
            if (first) {
                rows.add(new Row(Component.translatable("dvlib.config.keys"), null, DVUi.KEY));
                first = false;
            }
            DVButton button = addRenderableWidget(new DVButton(controlX(), 0, CONTROL_WIDTH, 16, Component.empty(), b -> {
                selectingKey = key;
                refreshKeyButtons();
            }));
            keyButtons.put(key, button);
            rows.add(new Row(Component.translatable(key.getName()), button));
        }
        refreshKeyButtons();
    }

    private void refreshKeyButtons() {
        for (Map.Entry<KeyMapping, DVButton> e : keyButtons.entrySet()) {
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

    /** Places each control vertically centred in its row; rows outside the view are hidden. */
    private void updateScroll() {
        for (Row row : rows) {
            if (row.header()) continue;
            int y = row.baseY - scrollOffset;
            row.widget.setY(y + (ROW_HEIGHT - row.widget.getHeight()) / 2);
            row.widget.visible = row.shown && y >= viewTop() && y + ROW_HEIGHT <= viewBottom();
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
            // Lists of strings are edited as comma-separated text.
            if (value.get() instanceof List<?>) {
                return Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
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
        int l = left();
        int w = panelWidth();
        DVUi.panel(g, l, top(), w, bottom() - top(), false);
        g.blit(new ResourceLocation(DVLib.MOD_ID, "textures/gui/mods/" + modId + ".png"), l + PAD - 2, top() + 5, 16, 16, 0, 0, 32, 32, 32, 32);
        g.drawString(font, title, l + PAD + 18, top() + 9, 0xFFE6E6F0);
        DVUi.icon(g, DVUi.SEARCH, l + PAD + 5, top() + 34, 1, 0xFF8A80A8);

        g.enableScissor(l + 1, viewTop(), l + w - 1, viewBottom());
        boolean any = false;
        for (Row row : rows) {
            if (!row.shown) continue;
            int y = row.baseY - scrollOffset;
            if (!row.header()) any = true;
            if (y + ROW_HEIGHT < viewTop() || y > viewBottom()) continue;
            if (row.header()) {
                int textX = l + PAD;
                if (row.icon != null) {
                    DVUi.icon(g, row.icon, l + PAD + 3, y + 11, 1, DVUi.accent());
                    textX += 12;
                    g.hLine(l + PAD, l + w - PAD, y + ROW_HEIGHT - 3, 0xFF3A3548);
                }
                g.drawString(font, row.label, textX, y + 7, row.icon != null ? DVUi.accent() : 0xFFA0A0A0);
                continue;
            }
            if (mouseY >= y && mouseY < y + ROW_HEIGHT && mouseX >= l && mouseX < l + w) {
                g.fill(l + 4, y + 1, l + w - 4, y + ROW_HEIGHT - 1, 0x22FFFFFF);
            }
            g.drawString(font, row.label, l + PAD, y + 7, 0xFFE6E6F0);
        }
        if (!any && !query.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("dvlib.search.none"), l + w / 2, viewTop() + 7, 0xA0A0A0);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackTop = viewTop();
            int trackHeight = viewBottom() - trackTop;
            int thumbHeight = Math.max(16, trackHeight * trackHeight / (trackHeight + maxScroll));
            int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
            int barX = l + w - 6;
            g.fill(barX, trackTop, barX + 2, viewBottom(), 0x55000000);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, DVUi.accent());
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
