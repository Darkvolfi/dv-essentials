package com.darkvolfi.dvlib.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Every DV mod that registered a config screen, with its config specs. Shared by the menu, search and profiles. */
public final class DVMods {
    public record Mod(String modId, ForgeConfigSpec clientSpec, ForgeConfigSpec serverSpec, Runnable onSave) {
        public String displayName() {
            return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getDisplayName()).orElse(modId);
        }

        /** Config values of the given spec, if it is loaded. */
        public List<ForgeConfigSpec.ConfigValue<?>> values(ForgeConfigSpec spec) {
            List<ForgeConfigSpec.ConfigValue<?>> list = new ArrayList<>();
            if (spec == null || !spec.isLoaded()) return list;
            for (Object entry : spec.getValues().valueMap().values()) {
                if (entry instanceof ForgeConfigSpec.ConfigValue<?> value) list.add(value);
            }
            return list;
        }

        /** True if the mod's name or any of its option labels contains the query. */
        public boolean matches(String query) {
            if (query.isEmpty() || contains(displayName(), query)) return true;
            for (ForgeConfigSpec spec : new ForgeConfigSpec[] {clientSpec, serverSpec}) {
                for (ForgeConfigSpec.ConfigValue<?> value : values(spec)) {
                    if (contains(optionLabel(modId, optionName(value)), query)) return true;
                }
            }
            return false;
        }
    }

    /** Every DV mod, installed or not, in menu order. Each has a logo in assets/dvlib/textures/gui/mods. */
    public static final List<String> KNOWN = List.of("dvzoom", "dvfood", "dvlens", "dvvein", "dvslots", "dvdark", "dvmap", "dvteam", "dvgrave", "dvmusic", "dvbackpack", "dvnewcrafts", "dvsouls");

    /** Download pages, once the mods are published; until then the menu says "coming soon". */
    private static final Map<String, String> PAGES = Map.of();

    private static final Map<String, Mod> MODS = new TreeMap<>();
    /** Mods whose tile opens their own screen (e.g. a music player) instead of the settings. */
    private static final Map<String, Function<Screen, Screen>> SCREENS = new HashMap<>();

    /** The registered mod, or null when it isn't installed (or has no settings). */
    public static Mod get(String modId) {
        return MODS.get(modId);
    }

    public static String page(String modId) {
        return PAGES.get(modId);
    }

    private DVMods() {}

    /** Makes the mod's tile in the DV menu open {@code screen} (given the menu as its parent) instead of the settings. */
    public static void registerScreen(String modId, Function<Screen, Screen> screen) {
        SCREENS.put(modId, screen);
    }

    public static Function<Screen, Screen> screen(String modId) {
        return SCREENS.get(modId);
    }

    static void add(Mod mod) {
        MODS.put(mod.modId(), mod);
    }

    public static Collection<Mod> all() {
        return MODS.values();
    }

    static String optionName(ForgeConfigSpec.ConfigValue<?> value) {
        return value.getPath().get(value.getPath().size() - 1);
    }

    static String optionLabel(String modId, String name) {
        String key = modId + ".config." + name;
        if (I18n.exists(key)) return I18n.get(key);
        // Fallback: "showModName" -> "Show mod name"
        String words = name.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static boolean contains(String text, String query) {
        return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
