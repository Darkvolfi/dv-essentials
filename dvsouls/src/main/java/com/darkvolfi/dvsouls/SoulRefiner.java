package com.darkvolfi.dvsouls;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Soul Refiner: refines a creature's soul into pure soul, which belongs to none: as many souls in its bar as the
 * creature had hearts ({@link SoulTank}). It wears down an amethyst shard every 8 souls (a server setting). An empty
 * bucket or glass bottle in the slot under the bar is filled from it; a bucket can also be filled by clicking the block,
 * and pipes of other mods reach the bar from any side. Hoppers put souls in from above, amethyst, buckets and bottles
 * from the sides, and take the empty jars and filled buckets and bottles from below.
 */
public final class SoulRefiner {
    public static final int SOUL = 0, AMETHYST = 1, JARS = 2, CONTAINER = 3, SIZE = 4;
    public static final int CAPACITY = 16 * FluidType.BUCKET_VOLUME;

    private SoulRefiner() {}

    /** An empty bucket or glass bottle, to fill with pure soul. */
    static boolean isContainer(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE);
    }

    public static class Block extends BaseEntityBlock {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;

        public Block() {
            super(BlockBehaviour.Properties.copy(Blocks.POLISHED_BLACKSTONE).lightLevel(state -> state.getValue(LIT) ? 12 : 2));
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
            return new Entity(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide ? null : createTickerHelper(type, DVSouls.SOUL_REFINER_ENTITY.get(), Entity::tick);
        }

        /** A bucket fills from (or empties into) the bar, a glass bottle fills from it; anything else opens the refiner. */
        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) return InteractionResult.sidedSuccess(level.isClientSide);
            if (!(level.getBlockEntity(pos) instanceof Entity refiner)) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            if (held.is(Items.GLASS_BOTTLE)) {
                if (!level.isClientSide && refiner.tank.spend(SoulFuel.BOTTLE)) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(DVSouls.PURE_SOUL_BOTTLE.get())));
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1, 1);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) player.openMenu(refiner);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Entity refiner) {
                Containers.dropContents(level, pos, refiner.items);
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

    public static class Entity extends BlockEntity implements MenuProvider, WorldlyContainer {
        private static final int[] TOP = {SOUL}, SIDES = {AMETHYST, CONTAINER}, BOTTOM = {JARS, CONTAINER};

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
        private final SoulTank tank = new SoulTank(CAPACITY, this::setChanged);
        private final LazyOptional<IFluidHandler> tankHandler = LazyOptional.of(() -> tank);
        private int progress;
        /** Souls refined since the amethyst shard last wore away. */
        private int wear;
        final ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> progress;
                    case 1 -> SoulsConfig.REFINE_TIME.get();
                    default -> tank.getFluidAmount();
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) progress = value;
            }

            @Override
            public int getCount() {
                return 3;
            }
        };

        public Entity(BlockPos pos, BlockState state) {
            super(DVSouls.SOUL_REFINER_ENTITY.get(), pos, state);
        }

        /** A soul whose pure soul fits (or one too big for the whole bar, when it's empty: the rest is lost), an amethyst shard, and room for its jar. */
        private boolean canRefine() {
            int amount = SoulTank.amount(items.getItem(SOUL));
            ItemStack jars = items.getItem(JARS);
            return amount > 0 && (tank.getSpace() >= amount || tank.isEmpty()) && items.getItem(AMETHYST).is(Items.AMETHYST_SHARD)
                    && (jars.isEmpty() || jars.getCount() < jars.getMaxStackSize());
        }

        static void tick(Level level, BlockPos pos, BlockState state, Entity refiner) {
            boolean working = refiner.canRefine();
            if (working && ++refiner.progress >= SoulsConfig.REFINE_TIME.get()) {
                refiner.progress = 0;
                refiner.wear += Math.round(Soul.hearts(Soul.get(refiner.items.getItem(SOUL))));
                refiner.tank.pour(refiner.items, SOUL);
                refiner.items.setItem(SOUL, ItemStack.EMPTY);
                for (int per = SoulsConfig.SOULS_PER_AMETHYST.get(); refiner.wear >= per && !refiner.items.getItem(AMETHYST).isEmpty(); refiner.wear -= per) {
                    refiner.items.getItem(AMETHYST).shrink(1);
                    level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);
                }
                ItemStack jars = refiner.items.getItem(JARS);
                if (jars.isEmpty()) refiner.items.setItem(JARS, new ItemStack(DVSouls.SOUL_JAR.get()));
                else jars.grow(1);
                level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1, 1.3f);
                ((ServerLevel) level).sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                        15, 0.3, 0.2, 0.3, 0.02);
            } else if (!working) {
                refiner.progress = 0;
            }
            refiner.tank.fillInto(refiner.items, CONTAINER);
            if (state.getValue(Block.LIT) != working) level.setBlock(pos, state.setValue(Block.LIT, working), 3);
            refiner.setChanged();
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == ForgeCapabilities.FLUID_HANDLER ? tankHandler.cast() : super.getCapability(cap, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            tankHandler.invalidate();
        }

        // ---- for hoppers: souls in from above, buckets and bottles from the sides, the empty jars and filled ones out below

        @Override
        public int[] getSlotsForFace(Direction side) {
            return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDES;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return switch (slot) {
                case SOUL -> SoulFuser.isSoul(stack);
                case AMETHYST -> stack.is(Items.AMETHYST_SHARD);
                case CONTAINER -> isContainer(stack);
                default -> false;
            };
        }

        /** Amethyst stacks up; the rest go in one at a time. */
        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
            return canPlaceItem(slot, stack) && (slot == AMETHYST || items.getItem(slot).isEmpty());
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return side == Direction.DOWN && (slot == JARS || slot == CONTAINER && !isContainer(stack));
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
        public Component getDisplayName() {
            return Component.translatable("container.dvsouls.soul_refiner");
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new Menu(id, inventory, items, data);
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            for (int i = 0; i < SIZE; i++) list.set(i, items.getItem(i));
            ContainerHelper.saveAllItems(tag, list);
            tag.put("Tank", tank.writeToNBT(new CompoundTag()));
            tag.putInt("Progress", progress);
            tag.putInt("Wear", wear);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, list);
            for (int i = 0; i < SIZE; i++) items.setItem(i, list.get(i));
            tank.readFromNBT(tag.getCompound("Tank"));
            progress = tag.getInt("Progress");
            wear = tag.getInt("Wear");
        }
    }

    /** The soul over the amethyst on the left, an arrow to the bar of souls, the empty jar beside the amethyst, and the slot under the bar. */
    public static class Menu extends AbstractContainerMenu {
        private final Container refiner;
        private final ContainerData data;

        public Menu(int id, Inventory inventory) {
            this(id, inventory, new SimpleContainer(SIZE), new SimpleContainerData(3));
        }

        Menu(int id, Inventory inventory, Container refiner, ContainerData data) {
            super(DVSouls.SOUL_REFINER_MENU.get(), id);
            this.refiner = refiner;
            this.data = data;
            addSlot(new SoulMachine.FilterSlot(refiner, SOUL, 30, 17, SoulFuser::isSoul));
            addSlot(new SoulMachine.FilterSlot(refiner, AMETHYST, 30, 53, stack -> stack.is(Items.AMETHYST_SHARD)));
            addSlot(new SoulMachine.FilterSlot(refiner, JARS, 56, 53, null));
            // one bucket or bottle at a time: it is filled where it lies
            addSlot(new SoulMachine.FilterSlot(refiner, CONTAINER, 87, 53, SoulRefiner::isContainer) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
            addDataSlots(data);
        }

        public float progress() {
            return data.get(1) <= 0 ? 0 : data.get(0) / (float) data.get(1);
        }

        /** The pure soul in the bar, mB. */
        public int amount() {
            return data.get(2);
        }

        @Override
        public boolean stillValid(Player player) {
            return refiner.stillValid(player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            Slot slot = slots.get(index);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            if (index < SIZE) {
                if (!moveItemStackTo(stack, SIZE, slots.size(), true)) return ItemStack.EMPTY;
            } else if (SoulFuser.isSoul(stack)) {
                if (!moveItemStackTo(stack, SOUL, SOUL + 1, false)) return ItemStack.EMPTY;
            } else if (stack.is(Items.AMETHYST_SHARD)) {
                if (!moveItemStackTo(stack, AMETHYST, AMETHYST + 1, false)) return ItemStack.EMPTY;
            } else if (!isContainer(stack) || !moveItemStackTo(stack, CONTAINER, CONTAINER + 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            return original;
        }
    }
}
