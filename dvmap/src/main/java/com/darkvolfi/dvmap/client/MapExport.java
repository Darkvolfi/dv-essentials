package com.darkvolfi.dvmap.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Saves the explored surface map of the current dimension as one PNG in the screenshots folder. */
public final class MapExport {
    /** At most this many regions (256 blocks each) across, around the player: 16 → 4096 × 4096 pixels. */
    private static final int MAX_REGIONS = 16;

    private MapExport() {}

    public static void export(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        MapData.saveDirtyRegions();
        String worldId = MapData.getCurrentWorldId();
        ResourceKey<Level> dim = mc.level.dimension();
        Path folder = MapData.dimensionFolder(worldId, dim);
        int playerRx = mc.player.getBlockX() >> MapData.REGION_SHIFT;
        int playerRz = mc.player.getBlockZ() >> MapData.REGION_SHIFT;
        String name = "dvmap_" + worldId + "_" + dim.location().getPath().replace('/', '_') + "_"
                + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT).format(new Date()) + ".png";
        Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(name);
        mc.player.displayClientMessage(Component.translatable("dvmap.export.started"), true);

        // Reading and writing a few hundred PNGs takes a moment: off the render thread.
        Util.ioPool().execute(() -> {
            Component result;
            try {
                result = write(folder, out, playerRx, playerRz)
                        ? Component.translatable("dvmap.export.done", Component.literal(name)
                                .withStyle(ChatFormatting.UNDERLINE)
                                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, out.toAbsolutePath().toString()))))
                        : Component.translatable("dvmap.export.empty");
            } catch (IOException | RuntimeException e) {
                result = Component.translatable("dvmap.export.failed", e.getMessage());
            }
            Component message = result;
            mc.execute(() -> mc.gui.getChat().addMessage(message));
        });
    }

    private static boolean write(Path folder, Path out, int playerRx, int playerRz) throws IOException {
        List<int[]> regions = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                files.forEach(f -> {
                    String[] parts = f.getFileName().toString().split("\\.");
                    if (parts.length != 4 || !parts[0].equals("r") || !parts[3].equals("png")) return;
                    try {
                        regions.add(new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])});
                    } catch (NumberFormatException ignored) {
                    }
                });
            }
        }
        // Too large a world: keep the regions closest to the player.
        int half = MAX_REGIONS / 2;
        regions.removeIf(r -> Math.abs(r[0] - playerRx) >= half || Math.abs(r[1] - playerRz) >= half);
        if (regions.isEmpty()) return false;

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] r : regions) {
            minX = Math.min(minX, r[0]);
            minZ = Math.min(minZ, r[1]);
            maxX = Math.max(maxX, r[0]);
            maxZ = Math.max(maxZ, r[1]);
        }
        int size = MapData.REGION_SIZE;
        try (NativeImage image = new NativeImage(NativeImage.Format.RGBA, (maxX - minX + 1) * size, (maxZ - minZ + 1) * size, true)) {
            for (int[] r : regions) {
                try (InputStream in = Files.newInputStream(folder.resolve("r." + r[0] + "." + r[1] + ".png"));
                     NativeImage tile = NativeImage.read(in)) {
                    if (tile.getWidth() != size || tile.getHeight() != size) continue;
                    tile.copyRect(image, 0, 0, (r[0] - minX) * size, (r[1] - minZ) * size, size, size, false, false);
                }
            }
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
        }
        return true;
    }
}
