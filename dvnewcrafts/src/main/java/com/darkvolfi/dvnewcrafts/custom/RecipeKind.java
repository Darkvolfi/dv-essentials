package com.darkvolfi.dvnewcrafts.custom;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** The vanilla recipe types the in-game editor can write. */
public enum RecipeKind {
    SHAPED("minecraft:crafting_shaped", 0),
    SHAPELESS("minecraft:crafting_shapeless", 0),
    SMELTING("minecraft:smelting", 200),
    SMOKING("minecraft:smoking", 100),
    BLASTING("minecraft:blasting", 100),
    CAMPFIRE("minecraft:campfire_cooking", 600),
    STONECUTTING("minecraft:stonecutting", 0);

    public final String type;
    /** The game's own cooking time in ticks; 0 for recipes that don't cook. */
    public final int defaultCookTime;

    RecipeKind(String type, int defaultCookTime) {
        this.type = type;
        this.defaultCookTime = defaultCookTime;
    }

    public boolean cooking() {
        return defaultCookTime > 0;
    }

    /** Cooking and stonecutting take one input item, which the editor keeps in the middle slot. */
    public boolean singleInput() {
        return this != SHAPED && this != SHAPELESS;
    }

    /** In 1.20.1 a cooking recipe's result is just an item id, so it always gives one. */
    public boolean hasCount() {
        return !cooking();
    }

    public Component label() {
        return Component.translatable("dvnewcrafts.recipes.kind." + name().toLowerCase(Locale.ROOT));
    }

    public static RecipeKind byType(String type) {
        for (RecipeKind kind : values()) {
            if (kind.type.equals(type)) return kind;
        }
        return null;
    }

    public static RecipeKind byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }
}
