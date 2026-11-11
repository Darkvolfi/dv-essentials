package com.darkvolfi.dvnewcrafts.custom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads and writes the recipes of the world's {@code dvnewcrafts_custom} data pack. The files are plain vanilla
 * recipes, so they keep working without this mod. Server side only.
 */
public final class CustomRecipes {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String PACK = "dvnewcrafts_custom";
    /** FolderRepositorySource names world data packs "file/<folder>". */
    private static final String PACK_ID = "file/" + PACK;
    private static final long MAX_FILE_SIZE = 64 * 1024;
    public static final int MAX_COOK_TIME = 72000;
    public static final float MAX_EXPERIENCE = 1000;

    public static final String SAVED = "dvnewcrafts.recipes.saved";
    public static final String DELETED = "dvnewcrafts.recipes.deleted";
    public static final String ERROR_NAME = "dvnewcrafts.recipes.error.name";
    public static final String ERROR_RESULT = "dvnewcrafts.recipes.error.result";
    public static final String ERROR_INPUT = "dvnewcrafts.recipes.error.input";
    public static final String ERROR_FULL = "dvnewcrafts.recipes.error.full";
    public static final String ERROR_FILE = "dvnewcrafts.recipes.error.file";

    private CustomRecipes() {}

    /** Lower case letters, digits and underscores only, so the name is a valid recipe id and file name. */
    public static String cleanName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') sb.append(c);
            else if (c == ' ' || c == '-') sb.append('_');
            if (sb.length() >= CustomRecipe.MAX_NAME) break;
        }
        return sb.toString();
    }

    private static boolean validName(String name) {
        return !name.isEmpty() && name.equals(cleanName(name));
    }

    private static Path packDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK).toAbsolutePath().normalize();
    }

    private static Path recipeDir(MinecraftServer server) {
        return packDir(server).resolve("data").resolve(PACK).resolve("recipes");
    }

    /** The recipe's file, or null if the name could point anywhere but straight into the recipes folder. */
    private static Path file(MinecraftServer server, String name) {
        if (!validName(name)) return null;
        Path dir = recipeDir(server);
        Path file = dir.resolve(name + ".json").normalize();
        return dir.equals(file.getParent()) ? file : null;
    }

    public static List<CustomRecipe> list(MinecraftServer server) {
        List<CustomRecipe> recipes = new ArrayList<>();
        Path dir = recipeDir(server);
        if (!Files.isDirectory(dir)) return recipes;
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(".json"))
                    .map(f -> f.substring(0, f.length() - 5))
                    .filter(CustomRecipes::validName)
                    .sorted()
                    .limit(CustomRecipe.MAX_RECIPES)
                    .forEach(name -> recipes.add(read(server, name)));
        } catch (IOException e) {
            LOGGER.warn("Couldn't list custom recipes in {}", dir, e);
        }
        return recipes;
    }

    /** The recipe as the editor shows it; marked invalid when the file can't be read back. */
    public static CustomRecipe read(MinecraftServer server, String name) {
        Path file = file(server, name);
        try {
            if (file == null || !Files.isRegularFile(file) || Files.size(file) > MAX_FILE_SIZE) return CustomRecipe.invalid(name);
            return parse(name, JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            return CustomRecipe.invalid(name);
        }
    }

    /** Writes the recipe (replacing one with the same name) and reloads. Returns the message key for the editor. */
    public static String save(MinecraftServer server, CustomRecipe recipe) {
        Path file = file(server, recipe.name());
        if (file == null) return ERROR_NAME;
        JsonObject json = new JsonObject();
        String error = toJson(recipe, json);
        if (error != null) return error;
        try {
            if (!Files.exists(file) && list(server).size() >= CustomRecipe.MAX_RECIPES) return ERROR_FULL;
            Files.createDirectories(file.getParent());
            Path meta = packDir(server).resolve("pack.mcmeta");
            if (!Files.exists(meta)) Files.writeString(meta, GSON.toJson(packMeta()), StandardCharsets.UTF_8);
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Couldn't write custom recipe {}", file, e);
            return ERROR_FILE;
        }
        reload(server);
        return SAVED;
    }

    public static String delete(MinecraftServer server, String name) {
        Path file = file(server, name);
        if (file == null) return ERROR_NAME;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.warn("Couldn't delete custom recipe {}", file, e);
            return ERROR_FILE;
        }
        reload(server);
        return DELETED;
    }

    /**
     * What /reload does, plus enabling our pack: a pack folder that is new since the world loaded is only found
     * after the repository rescans, and isn't selected until we add it. Reloading sends the recipes to every player.
     */
    private static void reload(MinecraftServer server) {
        PackRepository repository = server.getPackRepository();
        repository.reload();
        List<String> selected = new ArrayList<>(repository.getSelectedIds());
        if (!selected.contains(PACK_ID) && repository.isAvailable(PACK_ID)) selected.add(PACK_ID);
        server.reloadResources(selected).exceptionally(e -> {
            LOGGER.warn("Couldn't reload data packs after editing custom recipes", e);
            return null;
        });
    }

    private static JsonObject packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 15);
        pack.addProperty("description", "Recipes made in-game with DV New Crafts");
        JsonObject meta = new JsonObject();
        meta.add("pack", pack);
        return meta;
    }

    // --- JSON ---

    private static String id(ItemStack stack) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private static JsonObject itemJson(ItemStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("item", id(stack));
        return json;
    }

    /** Fills {@code json} with the recipe in vanilla form. Returns an error message key, or null. */
    private static String toJson(CustomRecipe recipe, JsonObject json) {
        List<ItemStack> slots = recipe.slots();
        ItemStack result = slots.get(CustomRecipe.RESULT);
        if (result.isEmpty()) return ERROR_RESULT;
        RecipeKind kind = recipe.kind();
        json.addProperty("type", kind.type);

        if (kind == RecipeKind.SHAPED) {
            // Trim empty rows and columns so the shape works anywhere in the grid, like the game's own recipes.
            int minRow = 3, maxRow = -1, minCol = 3, maxCol = -1;
            for (int i = 0; i < CustomRecipe.GRID; i++) {
                if (slots.get(i).isEmpty()) continue;
                minRow = Math.min(minRow, i / 3);
                maxRow = Math.max(maxRow, i / 3);
                minCol = Math.min(minCol, i % 3);
                maxCol = Math.max(maxCol, i % 3);
            }
            if (maxRow < 0) return ERROR_INPUT;
            Map<Item, Character> keys = new LinkedHashMap<>();
            JsonArray pattern = new JsonArray();
            for (int row = minRow; row <= maxRow; row++) {
                StringBuilder line = new StringBuilder();
                for (int col = minCol; col <= maxCol; col++) {
                    ItemStack stack = slots.get(row * 3 + col);
                    if (stack.isEmpty()) {
                        line.append(' ');
                        continue;
                    }
                    Character key = keys.get(stack.getItem());
                    if (key == null) {
                        key = (char) ('A' + keys.size());
                        keys.put(stack.getItem(), key);
                    }
                    line.append(key);
                }
                pattern.add(line.toString());
            }
            JsonObject key = new JsonObject();
            keys.forEach((item, letter) -> key.add(String.valueOf(letter), itemJson(new ItemStack(item))));
            json.add("pattern", pattern);
            json.add("key", key);
            json.add("result", resultJson(result, recipe.count()));
        } else if (kind == RecipeKind.SHAPELESS) {
            JsonArray ingredients = new JsonArray();
            for (int i = 0; i < CustomRecipe.GRID; i++) {
                if (!slots.get(i).isEmpty()) ingredients.add(itemJson(slots.get(i)));
            }
            if (ingredients.isEmpty()) return ERROR_INPUT;
            json.add("ingredients", ingredients);
            json.add("result", resultJson(result, recipe.count()));
        } else {
            ItemStack input = slots.get(CustomRecipe.SINGLE_INPUT);
            if (input.isEmpty()) return ERROR_INPUT;
            json.add("ingredient", itemJson(input));
            json.addProperty("result", id(result));
            if (kind == RecipeKind.STONECUTTING) {
                json.addProperty("count", recipe.count());
            } else {
                json.addProperty("experience", recipe.experience());
                json.addProperty("cookingtime", recipe.cookTime());
            }
        }
        return null;
    }

    private static JsonObject resultJson(ItemStack result, int count) {
        JsonObject json = itemJson(result);
        json.addProperty("count", count);
        return json;
    }

    /** Reads back what {@link #toJson} wrote; tags and alternatives show as their first item. */
    private static CustomRecipe parse(String name, JsonObject json) {
        RecipeKind kind = RecipeKind.byType(GsonHelper.getAsString(json, "type"));
        if (kind == null) return CustomRecipe.invalid(name);
        NonNullList<ItemStack> slots = NonNullList.withSize(CustomRecipe.SLOTS, ItemStack.EMPTY);
        int count = 1;
        int cookTime = kind.defaultCookTime;
        float experience = 0;

        if (kind == RecipeKind.SHAPED) {
            JsonArray pattern = GsonHelper.getAsJsonArray(json, "pattern");
            JsonObject key = GsonHelper.getAsJsonObject(json, "key");
            for (int row = 0; row < Math.min(3, pattern.size()); row++) {
                String line = pattern.get(row).getAsString();
                for (int col = 0; col < Math.min(3, line.length()); col++) {
                    char c = line.charAt(col);
                    if (c != ' ') slots.set(row * 3 + col, firstItem(key.get(String.valueOf(c))));
                }
            }
        } else if (kind == RecipeKind.SHAPELESS) {
            JsonArray ingredients = GsonHelper.getAsJsonArray(json, "ingredients");
            for (int i = 0; i < Math.min(CustomRecipe.GRID, ingredients.size()); i++) {
                slots.set(i, firstItem(ingredients.get(i)));
            }
        } else {
            slots.set(CustomRecipe.SINGLE_INPUT, firstItem(json.get("ingredient")));
            cookTime = GsonHelper.getAsInt(json, "cookingtime", kind.defaultCookTime);
            experience = GsonHelper.getAsFloat(json, "experience", 0);
        }

        ItemStack result = resultStack(json.get("result"));
        if (kind == RecipeKind.STONECUTTING) count = GsonHelper.getAsInt(json, "count", 1);
        else if (kind.hasCount()) count = Math.max(1, result.getCount());
        slots.set(CustomRecipe.RESULT, result.isEmpty() ? ItemStack.EMPTY : result.copyWithCount(1));
        return new CustomRecipe(name, kind, slots, Math.max(1, Math.min(64, count)), cookTime, experience, true);
    }

    private static ItemStack firstItem(JsonElement json) {
        if (json == null) return ItemStack.EMPTY;
        ItemStack[] items = Ingredient.fromJson(json).getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0].copyWithCount(1);
    }

    private static ItemStack resultStack(JsonElement json) {
        if (json == null) return ItemStack.EMPTY;
        if (json.isJsonObject()) return ShapedRecipe.itemStackFromJson(json.getAsJsonObject());
        ResourceLocation id = ResourceLocation.tryParse(json.getAsString());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
