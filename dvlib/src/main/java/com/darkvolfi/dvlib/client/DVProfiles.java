package com.darkvolfi.dvlib.client;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Named snapshots of every DV mod's settings and key bindings, stored as TOML files in config/dvlib/profiles.
 * Loading one copies the values back; world settings only apply inside a singleplayer world.
 */
public final class DVProfiles {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EXT = ".toml";

    private DVProfiles() {}

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("dvlib").resolve("profiles");
    }

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir())) return names;
        try (Stream<Path> files = Files.list(dir())) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(EXT))
                    .map(n -> n.substring(0, n.length() - EXT.length())).sorted(String.CASE_INSENSITIVE_ORDER).forEach(names::add);
        } catch (IOException e) {
            LOGGER.warn("Couldn't list DV profiles", e);
        }
        return names;
    }

    /** Letters, digits, spaces, '-' and '_' only, so the name is always a safe file name. */
    public static String clean(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') sb.append(c);
        }
        return sb.toString().trim();
    }

    public static boolean save(String name) {
        CommentedConfig config = TomlFormat.newConfig();
        for (DVMods.Mod mod : DVMods.all()) {
            put(config, mod, "client", mod.clientSpec());
            put(config, mod, "server", mod.serverSpec());
            for (KeyMapping key : modKeys(mod.modId())) {
                config.set(List.of(mod.modId(), "keys", key.getName()), key.saveString());
            }
        }
        try {
            Files.createDirectories(dir());
            TomlFormat.instance().createWriter().write(config, file(name), WritingMode.REPLACE);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Couldn't save DV profile {}", name, e);
            return false;
        }
    }

    private static void put(Config config, DVMods.Mod mod, String side, ForgeConfigSpec spec) {
        for (ForgeConfigSpec.ConfigValue<?> value : mod.values(spec)) {
            Object v = value.get();
            config.set(path(mod, side, value), v instanceof Enum<?> e ? e.name() : v);
        }
    }

    /** Applies a profile. Returns the number of values and keys that changed, or -1 if the file couldn't be read. */
    public static int load(String name) {
        Config config;
        try {
            config = TomlFormat.instance().createParser().parse(file(name), FileNotFoundAction.THROW_ERROR);
        } catch (Exception e) {
            LOGGER.warn("Couldn't read DV profile {}", name, e);
            return -1;
        }
        Minecraft mc = Minecraft.getInstance();
        int changed = 0;
        boolean keysChanged = false;
        for (DVMods.Mod mod : DVMods.all()) {
            int modChanged = apply(config, mod, "client", mod.clientSpec());
            if (mod.serverSpec() != null && mc.hasSingleplayerServer()) modChanged += apply(config, mod, "server", mod.serverSpec());
            for (KeyMapping key : modKeys(mod.modId())) {
                String saved = config.get(List.of(mod.modId(), "keys", key.getName()));
                if (saved == null || saved.equals(key.saveString())) continue;
                InputConstants.Key input = InputConstants.getKey(saved);
                key.setKeyModifierAndCode(KeyModifier.NONE, input);
                mc.options.setKey(key, input);
                keysChanged = true;
                changed++;
            }
            changed += modChanged;
            if (modChanged > 0 && mod.onSave() != null) mod.onSave().run();
        }
        if (keysChanged) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
        return changed;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int apply(Config config, DVMods.Mod mod, String side, ForgeConfigSpec spec) {
        int changed = 0;
        for (ForgeConfigSpec.ConfigValue<?> value : mod.values(spec)) {
            Object raw = config.get(path(mod, side, value));
            Object v = convert(value, raw);
            if (v == null || v.equals(value.get()) || !spec.getSpec().<ForgeConfigSpec.ValueSpec>get(value.getPath()).test(v)) continue;
            ((ForgeConfigSpec.ConfigValue) value).set(v);
            changed++;
        }
        if (changed > 0) spec.save();
        return changed;
    }

    /** TOML gives back Integer/Long/Double/String; turn them into what the config value expects. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convert(ForgeConfigSpec.ConfigValue<?> value, Object raw) {
        if (raw == null) return null;
        if (value instanceof ForgeConfigSpec.IntValue) return raw instanceof Number n ? n.intValue() : null;
        if (value instanceof ForgeConfigSpec.LongValue) return raw instanceof Number n ? n.longValue() : null;
        if (value instanceof ForgeConfigSpec.DoubleValue) return raw instanceof Number n ? n.doubleValue() : null;
        if (value instanceof ForgeConfigSpec.BooleanValue) return raw instanceof Boolean ? raw : null;
        if (value.get() instanceof Enum<?> current && raw instanceof String s) {
            try {
                return Enum.valueOf((Class) current.getDeclaringClass(), s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return raw.getClass().isInstance(value.get()) ? raw : null;
    }

    /** Opens the profiles folder in the system file manager, creating it first. */
    public static void openFolder() {
        try {
            Files.createDirectories(dir());
        } catch (IOException e) {
            LOGGER.warn("Couldn't create the DV profiles folder", e);
        }
        Util.getPlatform().openFile(dir().toFile());
    }

    public static boolean delete(String name) {
        try {
            return Files.deleteIfExists(file(name).toPath());
        } catch (IOException e) {
            LOGGER.warn("Couldn't delete DV profile {}", name, e);
            return false;
        }
    }

    private static List<String> path(DVMods.Mod mod, String side, ForgeConfigSpec.ConfigValue<?> value) {
        List<String> path = new ArrayList<>(List.of(mod.modId(), side));
        path.addAll(value.getPath());
        return path;
    }

    private static List<KeyMapping> modKeys(String modId) {
        List<KeyMapping> keys = new ArrayList<>();
        for (KeyMapping key : Minecraft.getInstance().options.keyMappings) {
            if (key.getName().startsWith("key." + modId + ".")) keys.add(key);
        }
        return keys;
    }

    private static File file(String name) {
        return dir().resolve(clean(name) + EXT).toFile();
    }
}
