package com.darkvolfi.dvslots.menu;

import com.darkvolfi.dvslots.DVSlots;
import com.darkvolfi.dvslots.slot.SlotData;
import com.darkvolfi.dvslots.slot.SlotType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;

/**
 * DV slots on top in labelled columns of two (room for four), player inventory and hotbar
 * below, in the usual 176px wide layout.
 */
public class SlotsMenu extends AbstractContainerMenu {
    public static final int DV_SLOTS_X = 8;
    public static final int DV_SLOTS_Y = 18;
    public static final int COLUMN_WIDTH = 82;
    public static final int ROW_HEIGHT = 22;
    public static final int INVENTORY_Y = 84;

    public static int slotX(SlotType type) {
        return DV_SLOTS_X + type.ordinal() / 2 * COLUMN_WIDTH;
    }

    public static int slotY(SlotType type) {
        return DV_SLOTS_Y + type.ordinal() % 2 * ROW_HEIGHT;
    }

    private final int dvSlotCount = SlotType.values().length;

    public SlotsMenu(int id, Inventory inventory) {
        super(DVSlots.SLOTS_MENU.get(), id);
        SlotData data = SlotData.get(inventory.player);
        for (SlotType type : SlotType.values()) {
            addSlot(new SlotItemHandler(data.items(), type.ordinal(), slotX(type), slotY(type)));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    public static void open(ServerPlayer player) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inventory, p) -> new SlotsMenu(id, inventory), Component.translatable("container.dvslots")));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inventoryStart = dvSlotCount;
        int hotbarStart = inventoryStart + 27;
        int end = hotbarStart + 9;

        boolean moved;
        if (index < dvSlotCount) {
            moved = moveItemStackTo(stack, inventoryStart, end, true);
        } else if (moveItemStackTo(stack, 0, dvSlotCount, false)) {
            moved = true;
        } else if (index < hotbarStart) {
            moved = moveItemStackTo(stack, hotbarStart, end, false);
        } else {
            moved = moveItemStackTo(stack, inventoryStart, hotbarStart, false);
        }
        if (!moved) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
