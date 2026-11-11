package com.darkvolfi.dvnewcrafts.custom;

import com.darkvolfi.dvnewcrafts.DVNewCrafts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The recipe editor: ten "ghost" slots (3x3 grid + result) above the player's inventory. A ghost slot only ever
 * holds a one-item copy of what the player clicked it with; nothing moves in or out of it, so it can't duplicate
 * or eat items.
 */
public class RecipeEditorMenu extends AbstractContainerMenu {
    public static final int RIGHT = 100;
    public static final int GRID_X = RIGHT + 8;
    public static final int GRID_Y = 38;
    public static final int RESULT_X = RIGHT + 102;
    public static final int RESULT_Y = GRID_Y + 18;
    public static final int INVENTORY_Y = 146;

    private final SimpleContainer ghosts = new SimpleContainer(CustomRecipe.SLOTS);
    /** The pack's recipes, client side; sent by the server on opening and after each change. */
    public List<CustomRecipe> recipes = List.of();
    /** Client side: ids of the recipes switched off in this world. */
    public List<String> disabled = List.of();
    /** Client side: which slots are in use. The server ignores unused slots when saving. */
    public RecipeKind kind = RecipeKind.SHAPED;

    public RecipeEditorMenu(int id, Inventory inventory) {
        super(DVNewCrafts.RECIPE_EDITOR.get(), id);
        for (int i = 0; i < CustomRecipe.GRID; i++) {
            addSlot(new GhostSlot(i, GRID_X + i % 3 * 18, GRID_Y + i / 3 * 18));
        }
        addSlot(new GhostSlot(CustomRecipe.RESULT, RESULT_X, RESULT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, GRID_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, GRID_X + col * 18, INVENTORY_Y + 58));
        }
    }

    /** The list follows in its own packet: Forge caps open-screen data at about 32 KB. */
    public static RecipeEditorMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        return new RecipeEditorMenu(id, inventory);
    }

    public ItemStack ghost(int index) {
        return ghosts.getItem(index);
    }

    public void setGhosts(List<ItemStack> stacks) {
        for (int i = 0; i < CustomRecipe.SLOTS; i++) {
            ghosts.setItem(i, i < stacks.size() ? stacks.get(i).copyWithCount(1) : ItemStack.EMPTY);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < CustomRecipe.SLOTS) {
            // Only a plain click does anything: it copies the carried item, or clears the slot with an empty hand.
            // Shift, number keys, drop, middle click and drags are all ignored here so no item ever changes hands.
            if (type == ClickType.PICKUP) {
                ItemStack carried = getCarried();
                ghosts.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    /** Shift-clicking the inventory has nowhere to go. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot);
    }

    /** Closes if the player stops being an operator. */
    @Override
    public boolean stillValid(Player player) {
        return player.hasPermissions(2);
    }

    private class GhostSlot extends Slot {
        GhostSlot(int index, int x, int y) {
            super(ghosts, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            int index = getContainerSlot();
            return index == CustomRecipe.RESULT || !kind.singleInput() || index == CustomRecipe.SINGLE_INPUT;
        }
    }
}
