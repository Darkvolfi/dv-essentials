package com.darkvolfi.dvbackpack;

import com.darkvolfi.dvbackpack.upgrade.CookingUpgrade;
import com.darkvolfi.dvbackpack.upgrade.FilterUpgrade;
import com.darkvolfi.dvbackpack.upgrade.TankUpgrade;
import com.darkvolfi.dvbackpack.upgrade.UpgradeItem;
import com.darkvolfi.dvbackpack.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A backpack's contents, at most six rows at a time (bigger backpacks scroll), over the player's inventory;
 * its upgrade cards in a column on the right; and, on the left, the panel of the tool card that is open
 * (a crafting grid, or a furnace's input, fuel and output).
 * <p>
 * Only the server knows the whole backpack: its slots look at {@code scroll * 9 + i} in the storage, and the
 * client is sent just what is in view. The tool slots exist for every upgrade slot and are active only while
 * that card's panel is open, so swapping cards never needs a new menu.
 */
public class BackpackMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9;
    public static final int MAX_VISIBLE_ROWS = 6;
    public static final int TOP = 18;
    public static final int SORT = 0;
    public static final int DEPOSIT = 1;
    public static final int TAKE = 2;
    /** Switches the open card's filter between "only these" and "everything except these". */
    public static final int FILTER_MODE = 3;
    /** Turns the open pickup card's magnet on or off. */
    public static final int MAGNET = 4;
    /** Fills or empties the held bucket (or other fluid container) with the open tank card. */
    public static final int TANK = 5;
    /** On the ground: lays out or rolls up the sleeping bag. Worn: lays it out and goes to sleep in it. */
    public static final int SLEEPING_BAG = 6;
    /** Takes the worn backpack off, into the inventory. */
    public static final int TAKE_OFF = 7;
    /** Button ids from here open or close the tool panel of upgrade slot (id minus this). */
    public static final int TOOL = 10;
    /** Button ids from here take the card in upgrade slot (id minus this) out into the inventory. */
    public static final int REMOVE = 30;
    /** Button ids from here up scroll: the id minus this is the top row. */
    public static final int SCROLL = 100;

    /**
     * Geometry shared with the screen. Each upgrade slot is a tab on the right edge of the backpack; a tool card's
     * tab opens into a panel (the card's slot in its header, the tool below) that grows right and down from it.
     */
    public static final int TAB_SPACING = 24;
    public static final int TAB_WIDTH = 26;
    public static final int TAB_HEIGHT = 22;
    public static final int PANEL_HEIGHT = 86;
    public static final int CRAFTING_WIDTH = 124;
    public static final int COOKING_WIDTH = 124;
    /** At most this many of another mod's item's own slots show in its panel. */
    public static final int HELD_SLOTS = 9;
    /** Slots per upgrade: the card, a crafting result and grid, a furnace's three slots, a filter's nine and a held item's. */
    private static final int GROUP = 23 + HELD_SLOTS;
    /** Where a group's held slots start. */
    private static final int HELD_START = 23;
    /** How far the tabs below an open panel move down to make room for it. */
    public static final int SHIFT = PANEL_HEIGHT + 2 - TAB_SPACING;

    /** Where the backpack was opened from, which decides when the menu must close. */
    public record Source(Kind kind, int slot, BlockPos pos) {
        public enum Kind { HAND, WORN, BLOCK }

        public static Source hand(int inventorySlot) {
            return new Source(Kind.HAND, inventorySlot, BlockPos.ZERO);
        }

        public static Source worn() {
            return new Source(Kind.WORN, -1, BlockPos.ZERO);
        }

        public static Source block(BlockPos pos) {
            return new Source(Kind.BLOCK, -1, pos);
        }

        void write(FriendlyByteBuf buf) {
            buf.writeEnum(kind);
            buf.writeVarInt(slot);
            buf.writeBlockPos(pos);
        }

        static Source read(FriendlyByteBuf buf) {
            return new Source(buf.readEnum(Kind.class), buf.readVarInt(), buf.readBlockPos());
        }
    }

    /** The storage; null on the client. */
    private final @Nullable ItemStackHandler items;
    private final @Nullable ItemStackHandler upgrades;
    private final Player player;
    private final ItemStack backpack;
    private final Source source;
    private final int rows;
    private final int visibleRows;
    private final int backpackSlots;
    private final int upgradeCount;
    private final int inventoryY;
    private final int width;
    private final DataSlot scroll = DataSlot.standalone();
    /** The upgrade slot whose tool panel is open, or -1. */
    private final DataSlot tool = DataSlot.standalone();
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer craftResult = new ResultContainer();
    private final List<CookingValue> cookingValues = new ArrayList<>();

    /** Index of the first upgrade group's slots. */
    private final int upgradeStart;

    /** Client side, from the open packet. */
    public BackpackMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, null, null, buf.readVarInt(), buf.readVarInt(), Source.read(buf), buf.readItem());
    }

    private BackpackMenu(int id, Inventory inventory, @Nullable ItemStackHandler items, @Nullable ItemStackHandler upgrades,
                         int size, int upgradeCount, Source source, ItemStack backpack) {
        super(DVBackpack.BACKPACK_MENU.get(), id);
        this.items = items;
        this.upgrades = upgrades;
        this.player = inventory.player;
        this.backpack = backpack;
        this.source = source;
        this.rows = size / COLUMNS;
        this.visibleRows = Math.min(rows, MAX_VISIBLE_ROWS);
        this.backpackSlots = visibleRows * COLUMNS;
        this.upgradeCount = upgradeCount;
        this.inventoryY = TOP + visibleRows * 18 + 13;
        this.width = rows > MAX_VISIBLE_ROWS ? 192 : 176;
        addDataSlot(scroll);
        addDataSlot(tool);
        tool.set(-1);
        for (String key : new String[]{"burn", "burnMax", "cook", "cookTotal"}) cookingValues.add((CookingValue) addDataSlot(new CookingValue(key)));

        Container view = new SimpleContainer(backpackSlots);
        for (int i = 0; i < backpackSlots; i++) {
            addSlot(new BackpackSlot(view, i, 8 + (i % COLUMNS) * 18, TOP + (i / COLUMNS) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, inventoryY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, inventoryY + 58));

        // Per upgrade slot, a group of GROUP slots placed on that slot's tab and its panel. The crafting slots of
        // every group share one grid; only the open group's slots are active.
        upgradeStart = slots.size();
        Container upgradeView = new SimpleContainer(upgradeCount);
        Container cookingView = new SimpleContainer(upgradeCount * 3);
        Container filterView = new SimpleContainer(upgradeCount * FilterUpgrade.SIZE);
        Container heldView = new SimpleContainer(upgradeCount * HELD_SLOTS);
        for (int i = 0; i < upgradeCount; i++) {
            int index = i;
            int x0 = tabX();
            int y0 = tabY(i);
            addSlot(new UpgradeSlot(upgradeView, i, x0 + 5, y0 + 3));
            addSlot(new ResultSlot(player, craftSlots, craftResult, 0, x0 + 98, y0 + 44) {
                @Override
                public boolean isActive() {
                    return tool.get() == index && toolType() == UpgradeType.CRAFTING;
                }
            });
            for (int k = 0; k < 9; k++) {
                addSlot(new Slot(craftSlots, k, x0 + 8 + (k % 3) * 18, y0 + 26 + (k / 3) * 18) {
                    @Override
                    public boolean isActive() {
                        return tool.get() == index && toolType() == UpgradeType.CRAFTING;
                    }
                });
            }
            addSlot(new CookingSlot(cookingView, i, CookingUpgrade.INPUT, x0 + 8, y0 + 26));
            addSlot(new CookingSlot(cookingView, i, CookingUpgrade.FUEL, x0 + 8, y0 + 62));
            addSlot(new CookingSlot(cookingView, i, CookingUpgrade.OUTPUT, x0 + 66, y0 + 44));
            for (int k = 0; k < FilterUpgrade.SIZE; k++) {
                addSlot(new FilterSlot(filterView, i, k, x0 + 8 + (k % 3) * 18, y0 + 26 + (k / 3) * 18));
            }
            for (int k = 0; k < HELD_SLOTS; k++) {
                addSlot(new HeldSlot(heldView, i, k, x0 + 8 + (k % 3) * 18, y0 + 26 + (k / 3) * 18));
            }
        }
    }

    /** Left edge of the upgrade tabs and panels: one pixel into the backpack panel, so they join it. */
    public int tabX() {
        return width - 1;
    }

    public static int tabY(int index) {
        return 4 + index * TAB_SPACING;
    }

    public static int panelWidth(UpgradeType type) {
        return type == UpgradeType.CRAFTING ? CRAFTING_WIDTH : COOKING_WIDTH;
    }

    /** Whether upgrade tab {@code index} has moved down, below a panel opened above it. */
    public boolean shifted(int index) {
        int open = tool.get();
        return open >= 0 && open < index && toolType() != null;
    }

    /** Where upgrade tab {@code index} is shown now. */
    public int shownTabY(int index) {
        return tabY(index) + (shifted(index) ? SHIFT : 0);
    }

    private int groupStart(int index) {
        return upgradeStart + index * GROUP;
    }

    public static void open(ServerPlayer player, ItemStack backpack, Source source) {
        BackpackStorage.Contents contents = BackpackStorage.get(player.server).contents(backpack);
        int size = contents.items().getSlots();
        int upgradeCount = contents.upgrades().getSlots();
        ItemStack copy = backpack.copy();
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> new BackpackMenu(id, inventory,
                contents.items(), contents.upgrades(), size, upgradeCount, source, copy), backpack.getHoverName()), buf -> {
            buf.writeVarInt(size);
            buf.writeVarInt(upgradeCount);
            source.write(buf);
            buf.writeItem(copy);
        });
    }

    /** The backpack this menu shows (on the client, for the tab icon). */
    public ItemStack backpack() {
        return backpack;
    }

    public int rows() {
        return rows;
    }

    public int visibleRows() {
        return visibleRows;
    }

    public int inventoryY() {
        return inventoryY;
    }

    /** The main panel's width: wider when it has a scroll bar. */
    public int width() {
        return width;
    }

    public int scroll() {
        return scroll.get();
    }

    public int maxScroll() {
        return rows - visibleRows;
    }

    public int upgradeCount() {
        return upgradeCount;
    }

    public ItemStack upgrade(int index) {
        return slots.get(groupStart(index)).getItem();
    }

    public Source source() {
        return source;
    }

    public int openTool() {
        return tool.get();
    }

    /** The type of the card whose panel is open, if that card is still there. */
    public @Nullable UpgradeType toolType() {
        int i = tool.get();
        return i < 0 || i >= upgradeCount ? null : UpgradeItem.panelType(upgrade(i));
    }

    /** Fire and progress of the open furnace card: "burn", "burnMax", "cook", "cookTotal". */
    public int cookingValue(String key) {
        for (CookingValue value : cookingValues) {
            if (value.key.equals(key)) return value.get();
        }
        return 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (items == null) return false;
        if (id >= SCROLL) {
            int row = id - SCROLL;
            if (row > maxScroll()) return false;
            scroll.set(row);
        } else if (id >= REMOVE) {
            // Takes the card out into the inventory (a furnace card keeps what is cooking on it).
            int index = id - REMOVE;
            if (index >= upgradeCount) return false;
            ItemStack card = upgrades.getStackInSlot(index);
            if (card.isEmpty()) return false;
            upgrades.setStackInSlot(index, ItemStack.EMPTY);
            if (tool.get() == index) tool.set(-1);
            if (!player.getInventory().add(card)) player.drop(card, false);
        } else if (id >= TOOL) {
            int index = id - TOOL;
            if (index >= upgradeCount) return false;
            tool.set(tool.get() == index ? -1 : index);
        } else {
            switch (id) {
                case SORT -> sort();
                case DEPOSIT -> deposit(player);
                case TAKE -> take(player);
                case TANK -> {
                    if (toolType() != UpgradeType.TANK || getCarried().isEmpty()) return false;
                    ItemStack card = upgrades.getStackInSlot(tool.get());
                    FluidTank tank = TankUpgrade.tank(card);
                    IItemHandler inventory = new PlayerMainInvWrapper(player.getInventory());
                    FluidActionResult result = FluidUtil.tryEmptyContainerAndStow(getCarried(), tank, inventory, Integer.MAX_VALUE, player, true);
                    if (!result.isSuccess()) result = FluidUtil.tryFillContainerAndStow(getCarried(), tank, inventory, Integer.MAX_VALUE, player, true);
                    if (!result.isSuccess()) return false;
                    setCarried(result.getResult());
                    TankUpgrade.save(card, tank);
                    upgrades.setStackInSlot(tool.get(), card);
                }
                case SLEEPING_BAG -> {
                    if (source.kind() == Source.Kind.WORN && player instanceof ServerPlayer sp) {
                        SleepOnTheGround.sleep(sp, BackpackItem.bagColor(backpack));
                    } else if (source.kind() == Source.Kind.BLOCK && player.level().getBlockEntity(source.pos()) instanceof BackpackBlockEntity entity) {
                        entity.toggleSleepingBag(player);
                    } else {
                        return false;
                    }
                }
                case TAKE_OFF -> {
                    if (source.kind() != Source.Kind.WORN) return false;
                    ItemStack worn = WornBackpack.takeOff(player);
                    if (worn.isEmpty()) return false;
                    if (!player.getInventory().add(worn)) player.drop(worn, false);
                    player.closeContainer();
                }
                case FILTER_MODE, MAGNET -> {
                    UpgradeType type = toolType();
                    if (type == null || !type.filter()) return false;
                    ItemStack card = upgrades.getStackInSlot(tool.get());
                    if (id == FILTER_MODE) FilterUpgrade.toggleOnly(card);
                    else FilterUpgrade.toggleMagnet(card);
                    upgrades.setStackInSlot(tool.get(), card); // marks the storage for saving and the card for syncing
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    /** Merges stacks of the same item and puts them in order: by item id, then fullest first. */
    private void sort() {
        List<ItemStack> merged = new ArrayList<>();
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i).copy();
            for (ItemStack m : merged) {
                if (stack.isEmpty()) break;
                if (ItemStack.isSameItemSameTags(m, stack) && m.getCount() < m.getMaxStackSize()) {
                    int moved = Math.min(stack.getCount(), m.getMaxStackSize() - m.getCount());
                    m.grow(moved);
                    stack.shrink(moved);
                }
            }
            if (!stack.isEmpty()) merged.add(stack);
        }
        merged.sort(Comparator.comparing((ItemStack s) -> String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem())))
                .thenComparing(ItemStack::getCount, Comparator.reverseOrder()));
        for (int i = 0; i < items.getSlots(); i++) items.setStackInSlot(i, i < merged.size() ? merged.get(i) : ItemStack.EMPTY);
    }

    /** Everything in the main inventory (not the hotbar) goes into the backpack, as far as it fits. */
    private void deposit(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.getItem() instanceof BackpackItem) continue;
            inventory.setItem(i, ItemHandlerHelper.insertItemStacked(items, stack, false));
        }
    }

    /** Everything in the backpack goes into the inventory, as far as it fits. */
    private void take(Player player) {
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack rest = stack.copy();
            player.getInventory().add(rest);
            if (rest.getCount() != stack.getCount()) items.setStackInSlot(i, rest);
        }
    }

    /** The crafting grid changed: work out the result, like a crafting table does. */
    @Override
    public void slotsChanged(Container container) {
        if (container != craftSlots || !(player instanceof ServerPlayer sp)) return;
        Level level = sp.level();
        ItemStack result = ItemStack.EMPTY;
        CraftingRecipe recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftSlots, level).orElse(null);
        if (recipe != null && craftResult.setRecipeUsed(level, sp, recipe)) {
            ItemStack made = recipe.assemble(craftSlots, level.registryAccess());
            if (made.isItemEnabled(level.enabledFeatures())) result = made;
        }
        craftResult.setItem(0, result);
        broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, craftSlots);
    }

    /** Keeps the backpack item's copy of the tanks current, so the client draws them on its sides. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (upgrades == null) return;
        List<FluidStack> tanks = new ArrayList<>();
        for (int i = 0; i < upgradeCount; i++) {
            ItemStack card = upgrades.getStackInSlot(i);
            if (UpgradeItem.typeOf(card) == UpgradeType.TANK) tanks.add(TankUpgrade.fluid(card));
        }
        List<FluidStack> shown = BackpackItem.tanks(backpack);
        boolean same = shown.size() == tanks.size();
        for (int i = 0; same && i < tanks.size(); i++) {
            same = tanks.get(i).isFluidStackIdentical(shown.get(i));
        }
        if (!same) editBackpack(stack -> BackpackItem.setTanks(stack, tanks));
    }

    /** Sets the colour of this backpack's sleeping bag (sent by the client's colour picker). */
    public void setBagColor(int rgb) {
        if (upgrades != null) editBackpack(stack -> BackpackItem.setBagColor(stack, rgb));
    }

    /**
     * Changes the real backpack item, wherever this menu found it, and this menu's copy. Only things that are
     * drawn live on the item; the contents stay in the storage.
     */
    private void editBackpack(Consumer<ItemStack> edit) {
        switch (source.kind()) {
            case HAND -> {
                ItemStack stack = player.getInventory().getItem(source.slot());
                if (BackpackStorage.same(stack, backpack)) edit.accept(stack);
            }
            case WORN -> {
                ItemStack stack = WornBackpack.find(player);
                if (BackpackStorage.same(stack, backpack)) {
                    edit.accept(stack);
                    WornBackpack.changed(player);
                }
            }
            case BLOCK -> {
                if (player.level().getBlockEntity(source.pos()) instanceof BackpackBlockEntity entity
                        && BackpackStorage.same(entity.backpack(), backpack)) {
                    edit.accept(entity.backpack());
                    entity.backpackChanged();
                }
            }
        }
        edit.accept(backpack);
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // The backpack being held open can't be moved: not by clicking it, nor with the number keys.
        if (source.kind() == Source.Kind.HAND) {
            if (type == ClickType.SWAP && button == source.slot()) return;
            if (slotId >= backpackSlots && slotId < backpackSlots + 36 && slots.get(slotId).getContainerSlot() == source.slot()) return;
        }
        // A filter slot holds a picture of an item, not the item: clicking with an item lists it, with nothing clears it.
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof FilterSlot filter) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) filter.set(getCarried().copyWithCount(1));
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inventoryStart = backpackSlots;
        int inventoryEnd = backpackSlots + 36;

        if (slot instanceof ResultSlot) {
            // Crafting with shift: straight into the inventory, again and again while it fits (the caller loops).
            stack.getItem().onCraftedBy(stack, player.level(), player);
            if (!moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, original);
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
            return original;
        }

        boolean moved;
        if (index < inventoryStart || index >= inventoryEnd) {
            // From the backpack, an upgrade slot or a tool panel: into the inventory.
            moved = moveItemStackTo(stack, inventoryStart, inventoryEnd, true);
            if (moved && slot instanceof CookingSlot cooking) cooking.onTake(player, original);
        } else if (UpgradeItem.isCard(stack) && moveIntoUpgradeSlot(stack)) {
            moved = true;
        } else if (toolType() == UpgradeType.HELD && moveIntoHeld(stack)) {
            moved = true;
        } else if (toolType() != null && toolType().filter()) {
            // With a filter open, shift-click lists the item (the item itself stays where it is).
            if (upgrades != null) {
                ItemStack card = upgrades.getStackInSlot(tool.get());
                if (FilterUpgrade.add(card, stack)) upgrades.setStackInSlot(tool.get(), card);
            }
            return ItemStack.EMPTY;
        } else if (toolType() != null && toolType().cooking() != null && moveIntoFurnace(stack)) {
            moved = true;
        } else if (items == null) {
            // The client only guesses with what it can see; the server's answer follows.
            moved = moveItemStackTo(stack, 0, backpackSlots, false);
        } else {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(items, stack.copy(), false);
            moved = rest.getCount() != stack.getCount();
            stack.setCount(rest.getCount());
        }
        if (!moved) return ItemStack.EMPTY; // nothing fit, which also stops the shift-click loop
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    /** An upgrade card goes to the first free upgrade slot. */
    private boolean moveIntoUpgradeSlot(ItemStack stack) {
        for (int i = 0; i < upgradeCount; i++) {
            int slot = groupStart(i);
            if (!slots.get(slot).hasItem() && moveItemStackTo(stack, slot, slot + 1, false)) return true;
        }
        return false;
    }

    /** Into the open held item's slots that take it. */
    private boolean moveIntoHeld(ItemStack stack) {
        int first = groupStart(tool.get()) + HELD_START;
        return moveItemStackTo(stack, first, first + HELD_SLOTS, false);
    }

    /** Fuel goes to the fuel slot, anything the card can cook to the input. */
    private boolean moveIntoFurnace(ItemStack stack) {
        int first = groupStart(tool.get()) + 11;
        ItemStack card = upgrade(tool.get());
        if (CookingUpgrade.canCook(card, stack, player.level()) && moveItemStackTo(stack, first + CookingUpgrade.INPUT, first + CookingUpgrade.INPUT + 1, false)) {
            return true;
        }
        return CookingUpgrade.isFuel(card, stack) && moveItemStackTo(stack, first + CookingUpgrade.FUEL, first + CookingUpgrade.FUEL + 1, false);
    }

    @Override
    public boolean stillValid(Player player) {
        if (items == null) return true;
        return switch (source.kind()) {
            case HAND -> BackpackStorage.same(player.getInventory().getItem(source.slot()), backpack);
            case WORN -> BackpackStorage.same(WornBackpack.find(player), backpack);
            case BLOCK -> player.level().getBlockEntity(source.pos()) instanceof BackpackBlockEntity entity
                    && BackpackStorage.same(entity.backpack(), backpack)
                    && player.distanceToSqr(source.pos().getCenter()) < 64;
        };
    }

    /** On the server a window onto the storage; on the client a plain slot holding what the server sent. */
    private final class BackpackSlot extends Slot {
        BackpackSlot(Container view, int index, int x, int y) {
            super(view, index, x, y);
        }

        private int storageSlot() {
            return scroll.get() * COLUMNS + getContainerSlot();
        }

        @Override
        public ItemStack getItem() {
            return items == null ? super.getItem() : items.getStackInSlot(storageSlot());
        }

        @Override
        public void set(ItemStack stack) {
            if (items == null) super.set(stack);
            else items.setStackInSlot(storageSlot(), stack);
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            if (items == null) return super.remove(amount);
            return items.extractItem(storageSlot(), amount, false);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !(stack.getItem() instanceof BackpackItem);
        }
    }

    /** An upgrade card slot; on the server it is the storage's upgrade slot. */
    private final class UpgradeSlot extends Slot {
        UpgradeSlot(Container view, int index, int x, int y) {
            super(view, index, x, y);
        }

        @Override
        public ItemStack getItem() {
            return upgrades == null ? super.getItem() : upgrades.getStackInSlot(getContainerSlot());
        }

        @Override
        public void set(ItemStack stack) {
            if (upgrades == null) super.set(stack);
            else upgrades.setStackInSlot(getContainerSlot(), stack);
            closeIfEmpty();
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            if (upgrades == null) return super.remove(amount);
            ItemStack taken = upgrades.extractItem(getContainerSlot(), amount, false);
            closeIfEmpty();
            return taken;
        }

        /** Taking the card out of an open panel closes the panel. */
        private void closeIfEmpty() {
            if (upgrades != null && tool.get() == getContainerSlot() && getItem().isEmpty()) tool.set(-1);
        }

        /**
         * Only an empty slot is a real slot: a card is drawn by the screen, on its tab or in its panel's header.
         * While a panel is open the empty tabs are put away, so only the cards' tabs are left.
         */
        @Override
        public boolean isActive() {
            int index = getContainerSlot();
            return toolType() == null && getItem().isEmpty();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return UpgradeItem.isCard(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** One of a furnace card's three slots; on the server it reads and writes the card's NBT. */
    private final class CookingSlot extends Slot {
        private final int card;
        private final int part;

        CookingSlot(Container view, int card, int part, int x, int y) {
            super(view, card * 3 + part, x, y);
            this.card = card;
            this.part = part;
        }

        private ItemStack cardStack() {
            ItemStack stack = upgrades.getStackInSlot(card);
            return CookingUpgrade.isCooking(stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public boolean isActive() {
            return tool.get() == card && toolType() != null && toolType().cooking() != null;
        }

        @Override
        public ItemStack getItem() {
            if (upgrades == null) return super.getItem();
            ItemStack c = cardStack();
            return c.isEmpty() ? ItemStack.EMPTY : CookingUpgrade.get(c, part);
        }

        @Override
        public void set(ItemStack stack) {
            if (upgrades == null) {
                super.set(stack);
            } else {
                ItemStack c = cardStack();
                if (c.isEmpty()) return;
                CookingUpgrade.set(c, part, stack);
                upgrades.setStackInSlot(card, c); // marks the storage for saving
            }
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            if (upgrades == null) return super.remove(amount);
            ItemStack stack = getItem().copy();
            ItemStack taken = stack.split(amount);
            set(stack);
            return taken;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (part == CookingUpgrade.OUTPUT) return false;
            ItemStack c = upgrades == null ? upgrade(card) : cardStack();
            return part == CookingUpgrade.INPUT || CookingUpgrade.isFuel(c, stack);
        }

        /** Taking the output pays out the experience the cooking earned. */
        @Override
        public void onTake(Player player, ItemStack stack) {
            if (part == CookingUpgrade.OUTPUT && upgrades != null && player.level() instanceof ServerLevel level) {
                ItemStack c = cardStack();
                if (!c.isEmpty()) {
                    float xp = CookingUpgrade.takeExperience(c);
                    int whole = Mth.floor(xp);
                    if (level.random.nextFloat() < xp - whole) whole++;
                    if (whole > 0) ExperienceOrb.award(level, player.position(), whole);
                    upgrades.setStackInSlot(card, c);
                }
            }
            super.onTake(player, stack);
        }
    }

    /** One of an automatic card's filter places; on the server it reads and writes the card's NBT. */
    private final class FilterSlot extends Slot {
        private final int card;
        private final int part;

        FilterSlot(Container view, int card, int part, int x, int y) {
            super(view, card * FilterUpgrade.SIZE + part, x, y);
            this.card = card;
            this.part = part;
        }

        @Override
        public boolean isActive() {
            return tool.get() == card && toolType() != null && toolType().filter();
        }

        @Override
        public ItemStack getItem() {
            if (upgrades == null) return super.getItem();
            ItemStack c = upgrades.getStackInSlot(card);
            return UpgradeItem.typeOf(c) != null && UpgradeItem.typeOf(c).filter() ? FilterUpgrade.get(c, part) : ItemStack.EMPTY;
        }

        @Override
        public void set(ItemStack stack) {
            if (upgrades == null) {
                super.set(stack);
                return;
            }
            ItemStack c = upgrades.getStackInSlot(card);
            if (UpgradeItem.typeOf(c) == null || !UpgradeItem.typeOf(c).filter()) return;
            FilterUpgrade.set(c, part, stack);
            upgrades.setStackInSlot(card, c);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /**
     * One of the slots of another mod's item in an upgrade slot (see {@link UpgradeType#HELD}), through its item
     * handler; on the server it changes the item in the upgrade slot. Only as many as the item has are active.
     */
    private final class HeldSlot extends Slot {
        private final int card;
        private final int part;

        HeldSlot(Container view, int card, int part, int x, int y) {
            super(view, card * HELD_SLOTS + part, x, y);
            this.card = card;
            this.part = part;
        }

        private @Nullable IItemHandler handler() {
            ItemStack c = upgrades == null ? upgrade(card) : upgrades.getStackInSlot(card);
            IItemHandler handler = c.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            return handler != null && part < handler.getSlots() ? handler : null;
        }

        @Override
        public boolean isActive() {
            return tool.get() == card && toolType() == UpgradeType.HELD && handler() != null;
        }

        @Override
        public ItemStack getItem() {
            if (upgrades == null) return super.getItem();
            IItemHandler handler = handler();
            return handler == null ? ItemStack.EMPTY : handler.getStackInSlot(part);
        }

        @Override
        public void set(ItemStack stack) {
            if (upgrades == null) {
                super.set(stack);
            } else if (handler() instanceof IItemHandlerModifiable handler) {
                handler.setStackInSlot(part, stack);
                upgrades.setStackInSlot(card, upgrades.getStackInSlot(card)); // marks the storage for saving
            }
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            if (upgrades == null) return super.remove(amount);
            ItemStack stack = getItem().copy();
            ItemStack taken = stack.split(amount);
            set(stack);
            return taken;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            IItemHandler handler = handler();
            return handler != null && handler.isItemValid(part, stack);
        }

        @Override
        public int getMaxStackSize() {
            IItemHandler handler = handler();
            return handler == null ? 0 : handler.getSlotLimit(part);
        }
    }

    /** A furnace value of the open card, sent to the client for the flame and the arrow. */
    private final class CookingValue extends DataSlot {
        private final String key;
        private int clientValue;

        CookingValue(String key) {
            this.key = key;
        }

        @Override
        public int get() {
            if (upgrades == null) return clientValue;
            int i = tool.get();
            if (i < 0 || i >= upgradeCount) return 0;
            ItemStack card = upgrades.getStackInSlot(i);
            return CookingUpgrade.isCooking(card) ? Math.min(Short.MAX_VALUE, CookingUpgrade.value(card, key)) : 0;
        }

        @Override
        public void set(int value) {
            clientValue = value;
        }
    }
}
