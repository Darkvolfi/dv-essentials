package com.darkvolfi.dvnewcrafts.custom;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One recipe of the world's custom data pack, as the editor shows it.
 * {@code slots} holds the 3x3 grid (0-8, row by row) and the result (9).
 * {@code valid} is false for a file the editor couldn't read (e.g. hand-written); it can only be deleted.
 */
public record CustomRecipe(String name, RecipeKind kind, NonNullList<ItemStack> slots, int count, int cookTime,
                           float experience, boolean valid) {
    public static final int GRID = 9;
    public static final int RESULT = 9;
    public static final int SLOTS = 10;
    /** The one input of cooking and stonecutting recipes: the middle of the grid. */
    public static final int SINGLE_INPUT = 4;
    public static final int MAX_NAME = 64;
    public static final int MAX_RECIPES = 256;

    public static CustomRecipe invalid(String name) {
        return new CustomRecipe(name, RecipeKind.SHAPED, NonNullList.withSize(SLOTS, ItemStack.EMPTY), 1, 0, 0, false);
    }

    public static void writeList(FriendlyByteBuf buf, List<CustomRecipe> recipes) {
        int n = Math.min(recipes.size(), MAX_RECIPES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) recipes.get(i).write(buf);
    }

    public static List<CustomRecipe> readList(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_RECIPES) throw new DecoderException("Too many recipes: " + n);
        List<CustomRecipe> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(read(buf));
        return list;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(name, MAX_NAME);
        buf.writeVarInt(kind.ordinal());
        for (ItemStack stack : slots) buf.writeItem(stack);
        buf.writeVarInt(count);
        buf.writeVarInt(cookTime);
        buf.writeFloat(experience);
        buf.writeBoolean(valid);
    }

    private static CustomRecipe read(FriendlyByteBuf buf) {
        String name = buf.readUtf(MAX_NAME);
        RecipeKind kind = RecipeKind.byId(buf.readVarInt());
        NonNullList<ItemStack> slots = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) slots.set(i, buf.readItem());
        int count = buf.readVarInt();
        int cookTime = buf.readVarInt();
        float experience = buf.readFloat();
        boolean valid = buf.readBoolean();
        return new CustomRecipe(name, kind == null ? RecipeKind.SHAPED : kind, slots, count, cookTime, experience,
                valid && kind != null);
    }
}
