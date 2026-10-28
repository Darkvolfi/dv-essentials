package com.darkvolfi.dvgrave;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A grave laid out like the inventory it came from: armour on the left, offhand beside it, the main
 * inventory and hotbar below, and other mods' slots in a 4×4 "extra" grid (paged when there are more).
 * The player's own inventory sits in a second panel to the right. Items can only be taken out.
 */
public class GraveMenu extends AbstractContainerMenu {
    /** Inventory slots 0..40: hotbar, main, armour (36 boots .. 39 helmet), offhand. */
    public static final int VANILLA_SLOTS = 41;
    public static final int EXTRA_COLUMNS = 4;
    public static final int EXTRA_PAGE = EXTRA_COLUMNS * 4;
    public static final int TAKE_ALL = 0;
    public static final int PREVIOUS = 1;
    public static final int NEXT = 2;

    /** Layout, in the screen's coordinates. */
    public static final int GRAVE_WIDTH = 176;
    public static final int GRAVE_HEIGHT = 192;
    public static final int EXTRA_X = 98;
    public static final int TOP = 18;
    public static final int MAIN_TOP = 108;
    public static final int HOTBAR_TOP = MAIN_TOP + 3 * 18 + 4;
    public static final int PLAYER_X = GRAVE_WIDTH + 4;
    public static final int PLAYER_HEIGHT = 102;

    private final Container grave;
    private final BlockPos pos;
    private final int xp;
    private final String owner;
    private final DataSlot page = DataSlot.standalone();
    /** Number of menu slots that show the grave. */
    private final int graveSlots;

    public GraveMenu(int id, Inventory inventory, Container grave, BlockPos pos, int xp, String owner) {
        super(DVGrave.GRAVE_MENU.get(), id);
        this.grave = grave;
        this.pos = pos;
        this.xp = xp;
        this.owner = owner;
        addDataSlot(page);

        // Helmet first, with vanilla's faint outlines in empty armour and offhand slots.
        ResourceLocation[] armour = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
                InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};
        for (int i = 0; i < 4; i++) {
            addSlot(new GraveSlot(39 - i, 8, TOP + i * 18, false)).setBackground(InventoryMenu.BLOCK_ATLAS, armour[i]);
        }
        addSlot(new GraveSlot(40, 77, TOP + 3 * 18, false)).setBackground(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
        for (int i = 0; i < 27; i++) addSlot(new GraveSlot(9 + i, 8 + (i % 9) * 18, MAIN_TOP + (i / 9) * 18, false));
        for (int i = 0; i < 9; i++) addSlot(new GraveSlot(i, 8 + i * 18, HOTBAR_TOP, false));
        for (int i = 0; i < EXTRA_PAGE; i++) {
            addSlot(new GraveSlot(VANILLA_SLOTS + i, EXTRA_X + (i % EXTRA_COLUMNS) * 18, TOP + (i / EXTRA_COLUMNS) * 18, true));
        }
        graveSlots = slots.size();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_X + 8 + col * 18, TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, PLAYER_X + 8 + col * 18, TOP + 58));
    }

    public int xp() {
        return xp;
    }

    public String owner() {
        return owner;
    }

    public int page() {
        return page.get();
    }

    /** Pages of the extra grid. */
    public int pages() {
        int extras = grave.getContainerSize() - VANILLA_SLOTS;
        return Math.max(1, (extras + EXTRA_PAGE - 1) / EXTRA_PAGE);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case TAKE_ALL -> {
                if (player instanceof ServerPlayer sp && player.level().getBlockEntity(pos) instanceof GraveBlockEntity entity) {
                    entity.takeAll(sp);
                    sp.closeContainer();
                }
            }
            case PREVIOUS -> page.set(Math.max(0, page.get() - 1));
            case NEXT -> page.set(Math.min(pages() - 1, page.get() + 1));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= graveSlots) return ItemStack.EMPTY; // nothing goes into a grave
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, graveSlots, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return grave.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp && player.level().getBlockEntity(pos) instanceof GraveBlockEntity entity) {
            entity.onMenuClosed(sp);
        }
    }

    /** A grave slot; the extra ones show a different stack per page. */
    private final class GraveSlot extends Slot {
        private final boolean paged;

        GraveSlot(int displaySlot, int x, int y, boolean paged) {
            super(grave, displaySlot, x, y);
            this.paged = paged;
        }

        private int displaySlot() {
            return getContainerSlot() + (paged ? page.get() * EXTRA_PAGE : 0);
        }

        @Override
        public ItemStack getItem() {
            return grave.getItem(displaySlot());
        }

        @Override
        public void set(ItemStack stack) {
            if (displaySlot() >= grave.getContainerSize()) return; // past the last extra (the client syncs every slot)
            grave.setItem(displaySlot(), stack);
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            return grave.removeItem(displaySlot(), amount);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isActive() {
            return displaySlot() < grave.getContainerSize();
        }
    }
}
