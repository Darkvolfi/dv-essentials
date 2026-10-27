package com.darkvolfi.dvteam.data;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * A clan flag, stored the way vanilla banners store theirs (pattern hash name + dye), so it renders with the
 * vanilla banner renderer and can become a real banner item. Modded patterns work too.
 */
public final class BannerData {
    public static final int MAX_LAYERS = 6;

    public record Layer(String pattern, DyeColor color) {}

    public final DyeColor base;
    public final List<Layer> layers;

    public BannerData(DyeColor base, List<Layer> layers) {
        this.base = base;
        this.layers = List.copyOf(layers.subList(0, Math.min(layers.size(), MAX_LAYERS)));
    }

    public static BannerData plain(DyeColor base) {
        return new BannerData(base, List.of());
    }

    /** Drops layers whose pattern doesn't exist (e.g. from a removed mod). */
    public BannerData validated() {
        List<Layer> ok = new ArrayList<>();
        for (Layer l : layers) if (BannerPattern.byHash(l.pattern) != null) ok.add(l);
        return new BannerData(base, ok);
    }

    /** Vanilla's "Patterns" list, usable by {@code BannerBlockEntity.createPatterns} and banner items. */
    public ListTag patternsTag() {
        ListTag list = new ListTag();
        for (Layer l : layers) {
            CompoundTag t = new CompoundTag();
            t.putString("Pattern", l.pattern);
            t.putInt("Color", l.color.getId());
            list.add(t);
        }
        return list;
    }

    public ItemStack toItem() {
        ItemStack stack = new ItemStack(BannerBlock.byColor(base).asItem());
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.put("Patterns", patternsTag());
        BlockItem.setBlockEntityData(stack, BlockEntityType.BANNER, blockEntity);
        return stack;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Base", base.getId());
        t.put("Patterns", patternsTag());
        return t;
    }

    public static BannerData load(CompoundTag t) {
        List<Layer> layers = new ArrayList<>();
        ListTag list = t.getList("Patterns", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag l = list.getCompound(i);
            layers.add(new Layer(l.getString("Pattern"), DyeColor.byId(l.getInt("Color"))));
        }
        return new BannerData(DyeColor.byId(t.getInt("Base")), layers);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(base.getId());
        buf.writeVarInt(layers.size());
        for (Layer l : layers) {
            buf.writeUtf(l.pattern, 64);
            buf.writeVarInt(l.color.getId());
        }
    }

    public static BannerData read(FriendlyByteBuf buf) {
        DyeColor base = DyeColor.byId(buf.readVarInt());
        int n = Math.min(buf.readVarInt(), MAX_LAYERS);
        List<Layer> layers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) layers.add(new Layer(buf.readUtf(64), DyeColor.byId(buf.readVarInt())));
        return new BannerData(base, layers);
    }

    /** Patterns a player can pick: every registered one except the plain base. */
    public static List<Holder<BannerPattern>> choosablePatterns() {
        List<Holder<BannerPattern>> list = new ArrayList<>();
        BuiltInRegistries.BANNER_PATTERN.holders().forEach(h -> {
            if (!h.is(BannerPatterns.BASE)) list.add(h);
        });
        return list;
    }
}
