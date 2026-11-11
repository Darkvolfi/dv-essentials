package com.darkvolfi.dvnewcrafts.network;

import com.darkvolfi.dvnewcrafts.DVNewCrafts;
import com.darkvolfi.dvnewcrafts.custom.CustomRecipe;
import com.darkvolfi.dvnewcrafts.custom.CustomRecipes;
import com.darkvolfi.dvnewcrafts.custom.RecipeEditorMenu;
import com.darkvolfi.dvnewcrafts.custom.RecipeKind;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.function.Supplier;

/**
 * The recipe editor's packets. Every packet from a client is checked for operator rights on the server, and the
 * server builds the recipe JSON itself from the editor's slots and a few numbers; it never takes JSON or paths.
 */
public final class NewCraftsNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DVNewCrafts.MOD_ID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    public static final int PERMISSION = 2;

    private NewCraftsNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(OpenEditor.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((p, buf) -> {}).decoder(buf -> new OpenEditor()).consumerMainThread(OpenEditor::handle).add();
        CHANNEL.messageBuilder(Select.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Select::encode).decoder(Select::decode).consumerMainThread(Select::handle).add();
        CHANNEL.messageBuilder(Save.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Save::encode).decoder(Save::decode).consumerMainThread(Save::handle).add();
        CHANNEL.messageBuilder(Delete.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Delete::encode).decoder(Delete::decode).consumerMainThread(Delete::handle).add();
        CHANNEL.messageBuilder(RecipeList.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RecipeList::encode).decoder(RecipeList::decode).consumerMainThread(RecipeList::handle).add();
    }

    /** The sender if they may edit recipes, otherwise null. */
    private static ServerPlayer operator(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        return player != null && player.hasPermissions(PERMISSION) ? player : null;
    }

    /** The sender's open editor, if they may still use it. */
    private static RecipeEditorMenu editor(ServerPlayer player) {
        return player != null && player.containerMenu instanceof RecipeEditorMenu menu ? menu : null;
    }

    private static void sendList(ServerPlayer player, String message, String name) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RecipeList(CustomRecipes.list(player.server), message, name));
    }

    /** Opens the editor, then sends the recipe list (packets arrive in order, so the screen is there by then). */
    public record OpenEditor() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = operator(ctx);
            if (player == null) return;
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (id, inventory, p) -> new RecipeEditorMenu(id, inventory),
                    Component.translatable("dvnewcrafts.recipes.title")));
            sendList(player, "", "");
        }
    }

    /** Fills the editor's slots from a saved recipe, or clears them when the name is empty. */
    public record Select(String name) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(name, CustomRecipe.MAX_NAME);
        }

        static Select decode(FriendlyByteBuf buf) {
            return new Select(buf.readUtf(CustomRecipe.MAX_NAME));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = operator(ctx);
            RecipeEditorMenu menu = editor(player);
            if (menu == null) return;
            menu.setGhosts(name.isEmpty() ? List.of() : CustomRecipes.read(player.server, name).slots());
        }
    }

    /** Saves the editor's slots under {@code name}; the numbers are checked and clamped here. */
    public record Save(String name, int kind, int count, int cookTime, float experience) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(name, CustomRecipe.MAX_NAME);
            buf.writeVarInt(kind);
            buf.writeVarInt(count);
            buf.writeVarInt(cookTime);
            buf.writeFloat(experience);
        }

        static Save decode(FriendlyByteBuf buf) {
            return new Save(buf.readUtf(CustomRecipe.MAX_NAME), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readFloat());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = operator(ctx);
            RecipeEditorMenu menu = editor(player);
            RecipeKind recipeKind = RecipeKind.byId(kind);
            if (menu == null || recipeKind == null) return;
            String clean = CustomRecipes.cleanName(name);
            if (clean.isEmpty() || !clean.equals(name)) {
                sendList(player, CustomRecipes.ERROR_NAME, name);
                return;
            }
            NonNullList<ItemStack> slots = NonNullList.withSize(CustomRecipe.SLOTS, ItemStack.EMPTY);
            for (int i = 0; i < CustomRecipe.SLOTS; i++) slots.set(i, menu.ghost(i).copy());
            float xp = Float.isFinite(experience) ? Math.max(0, Math.min(CustomRecipes.MAX_EXPERIENCE, experience)) : 0;
            CustomRecipe recipe = new CustomRecipe(clean, recipeKind, slots, Math.max(1, Math.min(64, count)),
                    Math.max(1, Math.min(CustomRecipes.MAX_COOK_TIME, cookTime)), xp, true);
            sendList(player, CustomRecipes.save(player.server, recipe), clean);
        }
    }

    public record Delete(String name) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(name, CustomRecipe.MAX_NAME);
        }

        static Delete decode(FriendlyByteBuf buf) {
            return new Delete(buf.readUtf(CustomRecipe.MAX_NAME));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = operator(ctx);
            if (editor(player) == null) return;
            sendList(player, CustomRecipes.delete(player.server, name), name);
        }
    }

    /** The pack's recipes after a change, with a message key (and its argument) for the editor to show. */
    public record RecipeList(List<CustomRecipe> recipes, String message, String name) {
        void encode(FriendlyByteBuf buf) {
            CustomRecipe.writeList(buf, recipes);
            buf.writeUtf(message, 128);
            buf.writeUtf(name, CustomRecipe.MAX_NAME);
        }

        static RecipeList decode(FriendlyByteBuf buf) {
            return new RecipeList(CustomRecipe.readList(buf), buf.readUtf(128), buf.readUtf(CustomRecipe.MAX_NAME));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.darkvolfi.dvnewcrafts.client.RecipeEditorScreen.onList(recipes, message, name));
        }
    }
}
