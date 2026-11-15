package com.darkvolfi.dvskin.client;

import com.darkvolfi.dvskin.DVSkin;
import com.darkvolfi.dvskin.SkinClientConfig;
import com.darkvolfi.dvskin.SkinData;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The player's saved choice: config values plus skin.png, cape.png and elytra.png in config/dvskin. */
public final class SkinChoice {
    private static final String[] FILES = {"skin.png", "cape.png", "elytra.png"};
    private static final List<String> THEMED = List.of("souls", "map", "team", "glass", "music", "vein", "backpack", "dv");

    private SkinChoice() {}

    public static ForgeConfigSpec.ConfigValue<String> value(int slot) {
        return slot == SkinData.SKIN ? SkinClientConfig.SKIN : slot == SkinData.CAPE ? SkinClientConfig.CAPE : SkinClientConfig.ELYTRA;
    }

    public static Path file(int slot) {
        return DVSkin.folder().resolve(FILES[slot]);
    }

    public static ResourceLocation dvCape(String name) {
        return new ResourceLocation(DVSkin.MOD_ID, "textures/capes/" + name + ".png");
    }

    /** The DV capes whose texture is present: 8 themed ones, then the 16 dye colours. */
    public static List<String> dvCapes() {
        List<String> names = new ArrayList<>(THEMED);
        for (DyeColor color : DyeColor.values()) names.add("color_" + color.getName());
        names.removeIf(name -> Minecraft.getInstance().getResourceManager().getResource(dvCape(name)).isEmpty());
        return names;
    }

    /** The saved choice as it is sent to the server; slots without a usable picture are null. */
    public static SkinData load() {
        byte[][] pngs = new byte[3][];
        for (int slot = 0; slot < 3; slot++) pngs[slot] = picture(slot, value(slot).get());
        return new SkinData(SkinClientConfig.SLIM.get(), pngs[0], pngs[1], pngs[2]);
    }

    private static byte[] picture(int slot, String source) {
        if (source.isEmpty()) return null;
        if (source.startsWith("dv:")) return slot == SkinData.SKIN ? null : resource(dvCape(source.substring(3)));
        try {
            Path path = file(slot);
            return Files.isRegularFile(path) && Files.size(path) <= SkinData.MAX_PNG ? Files.readAllBytes(path) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] resource(ResourceLocation id) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().open()) {
            byte[] png = in.readAllBytes();
            return png.length <= SkinData.MAX_PNG ? png : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Saves one slot: its source, and its picture when it has its own (a normalized PNG), or removes the picture.
     * The config file itself is written by the caller once all slots are set.
     */
    public static void save(int slot, String source, byte[] png) throws IOException {
        Path path = file(slot);
        if (png != null) {
            Files.createDirectories(path.getParent());
            Files.write(path, png);
        } else {
            Files.deleteIfExists(path);
        }
        value(slot).set(source);
    }
}
