package com.darkvolfi.dvmusic.client;

import com.darkvolfi.dvmusic.DVMusic;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

/**
 * A resource pack that is really the dvmusic folder: dvmusic:sounds/custom/&lt;id&gt;.ogg reads the matching file.
 * Files are looked up when a song starts, so songs added later play after a refresh, without reloading resources.
 */
public class CustomMusicPack implements PackResources {
    public static final String ID = "dvmusic_folder";
    private static final String PREFIX = "sounds/";
    private static final String SUFFIX = ".ogg";

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES || !location.getNamespace().equals(DVMusic.MOD_ID)) return null;
        String path = location.getPath();
        if (!path.startsWith(PREFIX + "custom/") || !path.endsWith(SUFFIX)) return null;
        Path file = TrackLibrary.file(path.substring(PREFIX.length(), path.length() - SUFFIX.length()));
        return file == null ? null : IoSupplier.create(file);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        // Nothing to list: songs are found by name.
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(DVMusic.MOD_ID) : Set.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(Component.translatable("dvmusic.pack"), 15);
        }
        return null;
    }

    @Override
    public String packId() {
        return ID;
    }

    @Override
    public boolean isBuiltin() {
        return true;
    }

    @Override
    public void close() {}
}
