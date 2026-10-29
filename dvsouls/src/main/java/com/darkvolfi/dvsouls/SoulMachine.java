package com.darkvolfi.dvsouls;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What the Soul Fuser and the Soul Distiller share: five slots (two inputs, a soul burning as fuel, the result and the
 * jars left empty), the fire, and a job that takes a while and leaves one jar empty. Hoppers take from below.
 */
public final class SoulMachine {
    public static final int FUEL = 2, RESULT = 3, EMPTY = 4, SIZE = 5;

    private SoulMachine() {}

    public abstract static class Block extends BaseEntityBlock {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;
        private final Supplier<? extends BlockEntityType<? extends Entity>> type;

        protected Block(Supplier<? extends BlockEntityType<? extends Entity>> type) {
            super(BlockBehaviour.Properties.copy(Blocks.POLISHED_BLACKSTONE).lightLevel(state -> state.getValue(LIT) ? 12 : 2));
            this.type = type;
            registerDefaultState(defaultBlockState().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return type.get().create(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide ? null : createTickerHelper(type, this.type.get(), Entity::tick);
        }

        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof Entity machine) player.openMenu(machine);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Entity machine) {
                net.minecraft.world.Containers.dropContents(level, pos, machine.items);
            }
            super.onRemove(state, level, pos, newState, moved);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(LIT)) return;
            level.addParticle(ParticleTypes.SOUL, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
                    0, 0.02, 0);
        }
    }

    public abstract static class Entity extends BlockEntity implements MenuProvider, WorldlyContainer {
        private static final int[] BOTTOM = {RESULT, EMPTY};

        final SimpleContainer items = new SimpleContainer(SIZE) {
            @Override
            public void setChanged() {
                super.setChanged();
                Entity.this.setChanged();
            }

            @Override
            public boolean stillValid(Player player) {
                return Container.stillValidBlockEntity(Entity.this, player);
            }
        };
        private int progress;
        private int fuel;
        private int fuelMax;
        final ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> progress;
                    case 1 -> fuel;
                    case 2 -> fuelMax;
                    default -> workTime();
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> progress = value;
                    case 1 -> fuel = value;
                    case 2 -> fuelMax = value;
                    default -> { }
                }
            }

            @Override
            public int getCount() {
                return 4;
            }
        };

        protected Entity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        /** The inputs are there for a job, and the result has room. */
        protected abstract boolean canWork();

        /** Does the job: takes the inputs and fills the result slot. The jar left empty is put away after. */
        protected abstract void work(ServerLevel level);

        protected abstract int workTime();

        /** Ready for a job, with room for the jar it leaves empty. */
        private boolean ready() {
            ItemStack spare = items.getItem(EMPTY);
            return canWork() && (spare.isEmpty() || spare.getCount() < spare.getMaxStackSize());
        }

        /** One more empty jar: into the empty-jar slot, or else back where the fuel was. */
        private void putAwayJar(int otherwise) {
            ItemStack spare = items.getItem(EMPTY);
            if (spare.isEmpty()) items.setItem(EMPTY, new ItemStack(DVSouls.SOUL_JAR.get()));
            else if (spare.getCount() < spare.getMaxStackSize()) spare.grow(1);
            else items.setItem(otherwise, new ItemStack(DVSouls.SOUL_JAR.get()));
        }

        static void tick(Level level, BlockPos pos, BlockState state, Entity machine) {
            boolean ready = machine.ready();
            ItemStack fuelStack = machine.items.getItem(FUEL);
            if (machine.fuel <= 0 && ready && SoulFuser.isSoul(fuelStack)) {
                machine.fuelMax = machine.fuel = Math.max(1, Math.round(Soul.hearts(Soul.get(fuelStack)) * SoulsConfig.FUSER_FUEL_PER_HEART.get()));
                machine.items.setItem(FUEL, ItemStack.EMPTY);
                machine.putAwayJar(FUEL);
                level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1, 0.7f);
            }
            if (machine.fuel > 0) {
                machine.fuel--;
                machine.progress = ready ? machine.progress + 1 : 0;
                // ready again: burning new fuel this tick may have taken the last room for an empty jar
                if (machine.progress >= machine.workTime() && machine.ready()) {
                    machine.progress = 0;
                    machine.work((ServerLevel) level);
                    machine.putAwayJar(EMPTY);
                }
            } else {
                machine.progress = 0;
            }
            boolean lit = machine.fuel > 0;
            if (state.getValue(Block.LIT) != lit) level.setBlock(pos, state.setValue(Block.LIT, lit), 3);
            machine.setChanged();
        }

        // ---- for hoppers: each machine says what goes in where; the results come out from below

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
            return canPlaceItem(slot, stack) && items.getItem(slot).isEmpty();
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return side == Direction.DOWN && slot >= RESULT;
        }

        protected static int[] bottom() {
            return BOTTOM;
        }

        @Override
        public int getContainerSize() {
            return items.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return items.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return items.getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return items.removeItem(slot, count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return items.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items.setItem(slot, stack);
        }

        @Override
        public boolean stillValid(Player player) {
            return items.stillValid(player);
        }

        @Override
        public void clearContent() {
            items.clearContent();
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
            for (int i = 0; i < list.size(); i++) list.set(i, items.getItem(i));
            ContainerHelper.saveAllItems(tag, list);
            tag.putInt("Progress", progress);
            tag.putInt("Fuel", fuel);
            tag.putInt("FuelMax", fuelMax);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, list);
            for (int i = 0; i < list.size(); i++) items.setItem(i, list.get(i));
            progress = tag.getInt("Progress");
            fuel = tag.getInt("Fuel");
            fuelMax = tag.getInt("FuelMax");
        }
    }

    /** The machine's five slots and the player's inventory under them. */
    public abstract static class Menu extends AbstractContainerMenu {
        private final Container machine;
        private final ContainerData data;

        protected Menu(MenuType<?> type, int id, Inventory inventory, Container machine, ContainerData data) {
            super(type, id);
            this.machine = machine;
            this.data = data;
            addMachineSlots(machine);
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
            addDataSlots(data);
        }

        /** Adds the machine's slots, in slot order. */
        protected abstract void addMachineSlots(Container machine);

        /** Shift-click from the inventory: moves the stack into the machine, returning false if it doesn't belong there. */
        protected abstract boolean moveIn(ItemStack stack);

        public float progress() {
            return data.get(3) <= 0 ? 0 : data.get(0) / (float) data.get(3);
        }

        public float fuel() {
            return data.get(2) <= 0 ? 0 : data.get(1) / (float) data.get(2);
        }

        @Override
        public boolean stillValid(Player player) {
            return machine.stillValid(player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            Slot slot = slots.get(index);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            if (index < SIZE) {
                if (!moveItemStackTo(stack, SIZE, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveIn(stack)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            return original;
        }

        /** moveItemStackTo for subclasses. */
        protected boolean move(ItemStack stack, int from, int to) {
            return moveItemStackTo(stack, from, to, false);
        }
    }

    /** A slot that takes only what passes its check; with no check, nothing can be put in (an output). */
    public static class FilterSlot extends Slot {
        private final @Nullable Predicate<ItemStack> accepts;

        public FilterSlot(Container container, int index, int x, int y, @Nullable Predicate<ItemStack> accepts) {
            super(container, index, x, y);
            this.accepts = accepts;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return accepts != null && accepts.test(stack);
        }
    }
}
