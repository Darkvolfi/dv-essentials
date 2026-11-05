package com.darkvolfi.dvsouls;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Soul Refiner: refines a creature's soul into pure soul, which belongs to none, one heart at a time: each heart is
 * a soul in its bar ({@link SoulTank}), and the creature left in the jar has one heart fewer. It wears down an amethyst
 * shard every 8 souls (a server setting). An empty bucket or glass bottle in the slot under the bar is filled from it; a
 * bucket can also be filled by clicking the block, and pipes of other mods reach the bar from any side. Hoppers put souls in from above, amethyst, buckets and bottles
 * from the sides, and take the empty jars and filled buckets and bottles from below.
 */
public final class SoulRefiner {
    public static final int SOUL = 0, AMETHYST = 1, JARS = 2, CONTAINER = 3, SIZE = 4;
    public static final int CAPACITY = 16 * FluidType.BUCKET_VOLUME;
    /** The column of the amethyst slot, and above it of the shard in use on the way from the soul to the bar. */
    public static final int LENS_X = 62;

    private SoulRefiner() {}

    /** An empty bucket or glass bottle, to fill with pure soul. */
    static boolean isContainer(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE);
    }

    /**
     * The block as its model shows it: a blackstone box on a slab before a wall, an amethyst bar from the box to a tower
     * whose window shows how full the bar of souls is. The jar under the box is drawn by the client's renderer.
     * CRACK: 0 without amethyst, else 1 (whole) to 4 (about to wear away); LEVEL: the bar, 0-8; LIT: souls flowing along
     * the amethyst.
     */
    public static class Block extends BaseEntityBlock {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final BooleanProperty LIT = BlockStateProperties.LIT;
        public static final IntegerProperty CRACK = IntegerProperty.create("crack", 0, 4);
        public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 8);

        public Block() {
            super(BlockBehaviour.Properties.copy(Blocks.POLISHED_BLACKSTONE).noOcclusion()
                    .lightLevel(state -> state.getValue(LIT) ? 12 : 2));
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LIT, false)
                    .setValue(CRACK, 0).setValue(LEVEL, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
            builder.add(FACING, LIT, CRACK, LEVEL);
        }

        /** Its front, the side the jar, amethyst and window are seen from, faces whoever places it. */
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
        /**
         * The amethyst shard in use: drawn from the slot into the machine, out of reach until it wears away. It is the
         * one the model shows, cracking as it wears.
         */
        private boolean lens;
        /** Souls refined through the shard in use. */
        private int wear;
        final ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> progress;
                    case 1 -> SoulsConfig.REFINE_TIME.get();
                    case 2 -> tank.getFluidAmount();
                    case 3 -> lens ? wear : -1;
                    default -> SoulsConfig.SOULS_PER_AMETHYST.get();
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) progress = value;
            }

            @Override
            public int getCount() {
                return 5;
            }
        };

        public Entity(BlockPos pos, BlockState state) {
            super(DVSouls.SOUL_REFINER_ENTITY.get(), pos, state);
        }

        /** The pure soul the next step draws: one heart's worth, or what is left of the last one. */
        private int nextAmount() {
            float hearts = Soul.hearts(Soul.get(items.getItem(SOUL)));
            return hearts <= 0 ? 0 : Math.max(1, Math.round(Math.min(1, hearts) * SoulTank.soul()));
        }

        /** A soul whose next heart fits in the bar, an amethyst shard (in use or to draw in), and room for its jar once it is empty. */
        private boolean canRefine() {
            int amount = nextAmount();
            ItemStack jars = items.getItem(JARS);
            return amount > 0 && tank.getSpace() >= amount && (lens || items.getItem(AMETHYST).is(Items.AMETHYST_SHARD))
                    && (jars.isEmpty() || jars.getCount() < jars.getMaxStackSize());
        }

        /**
         * Draws one heart out of the jar into the bar. The creature in it is left with that many hearts fewer, so a jar
         * taken out halfway keeps what is left: a 10 heart creature taken out after 4 has 6.
         */
        private void refineOne(Level level, BlockPos pos) {
            if (!lens) {
                items.getItem(AMETHYST).shrink(1);
                lens = true;
                wear = 0;
            }
            ItemStack jar = items.getItem(SOUL);
            CompoundTag soul = Soul.get(jar);
            float left = Soul.hearts(soul) - 1;
            tank.fill(new FluidStack(DVSouls.PURE_SOUL.get(), nextAmount()), IFluidHandler.FluidAction.EXECUTE);
            if (left > 0.01f) {
                items.setItem(SOUL, Soul.put(jar.copy(), Soul.withMaxHealth(soul, left * 2)));
            } else {
                items.setItem(SOUL, ItemStack.EMPTY);
                ItemStack jars = items.getItem(JARS);
                if (jars.isEmpty()) items.setItem(JARS, new ItemStack(DVSouls.SOUL_JAR.get()));
                else jars.grow(1);
                level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1, 1.3f);
            }
            if (++wear >= SoulsConfig.SOULS_PER_AMETHYST.get()) {
                wear = 0;
                lens = false;
                level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 0.8f);
            ((ServerLevel) level).sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    4, 0.2, 0.1, 0.2, 0.02);
        }

        /** The block state that shows what is inside: how worn the amethyst is, and how full the bar. */
        private BlockState shown(BlockState state, boolean working) {
            int per = SoulsConfig.SOULS_PER_AMETHYST.get();
            int crack = lens ? 1 + Math.min(3, wear * 4 / per) : 0;
            int bar = tank.isEmpty() ? 0 : Math.max(1, Math.round(tank.getFluidAmount() * 8f / CAPACITY));
            return state.setValue(Block.LIT, working).setValue(Block.CRACK, crack).setValue(Block.LEVEL, bar);
        }

        static void tick(Level level, BlockPos pos, BlockState state, Entity refiner) {
            boolean working = refiner.canRefine();
            if (working && ++refiner.progress >= SoulsConfig.REFINE_TIME.get()) {
                refiner.progress = 0;
                refiner.refineOne(level, pos);
            } else if (!working) {
                refiner.progress = 0;
            }
            refiner.tank.fillInto(refiner.items, CONTAINER);
            BlockState shown = refiner.shown(state, working);
            if (shown != state) level.setBlock(pos, shown, 3);
            // the client draws the jar: tell it when another one is put in or it is taken out
            ItemStack jar = refiner.items.getItem(SOUL);
            if (!ItemStack.isSameItemSameTags(jar, refiner.shownJar)) {
                refiner.shownJar = jar.copy();
                level.sendBlockUpdated(pos, shown, shown, 2);
            }
            refiner.setChanged();
        }

        /** The jar the client was last told about. */
        private ItemStack shownJar = ItemStack.EMPTY;

        @Override
        public CompoundTag getUpdateTag() {
            CompoundTag tag = new CompoundTag();
            tag.put("Jar", items.getItem(SOUL).save(new CompoundTag()));
            return tag;
        }

        @Override
        public void handleUpdateTag(CompoundTag tag) {
            items.setItem(SOUL, ItemStack.of(tag.getCompound("Jar")));
        }

        @Override
        public ClientboundBlockEntityDataPacket getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
            if (packet.getTag() != null) handleUpdateTag(packet.getTag());
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
            tag.putBoolean("Lens", lens);
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
            lens = tag.getBoolean("Lens");
        }
    }

    /**
     * The soul on the left with its empty jar under it, an arrow through the amethyst in use to the bar of souls, the
     * amethyst slot under the one in use (it is drawn up from there), and the slot under the bar.
     */
    public static class Menu extends AbstractContainerMenu {
        private final Container refiner;
        private final ContainerData data;

        public Menu(int id, Inventory inventory) {
            this(id, inventory, new SimpleContainer(SIZE), new SimpleContainerData(5));
        }

        Menu(int id, Inventory inventory, Container refiner, ContainerData data) {
            super(DVSouls.SOUL_REFINER_MENU.get(), id);
            this.refiner = refiner;
            this.data = data;
            addSlot(new SoulMachine.FilterSlot(refiner, SOUL, 30, 17, SoulFuser::isSoul));
            addSlot(new SoulMachine.FilterSlot(refiner, AMETHYST, LENS_X, 53, stack -> stack.is(Items.AMETHYST_SHARD)));
            addSlot(new SoulMachine.FilterSlot(refiner, JARS, 30, 53, null));
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

        /** Souls refined through the amethyst in use, or -1 when none is drawn in. */
        public int lensWear() {
            return data.get(3);
        }

        /** Souls an amethyst shard lasts. */
        public int lensLife() {
            return data.get(4);
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
