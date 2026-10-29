package com.darkvolfi.dvsouls;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
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

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The Soul Fuser: two full soul jars go in, and with a soul burning as its fuel it fuses them into a hybrid's soul.
 * The hybrid's jar comes out with one jar left empty. Which two make which hybrid is fixed; the order doesn't matter.
 */
public final class SoulFuser {
    public static final int A = 0, B = 1, FUEL = 2, RESULT = 3, EMPTY = 4;
    /** Ticks of burning a fusion takes. */
    public static final int FUSE_TIME = 200;
    /** Ticks of burning per heart of the fuel's soul: a cow's 5 hearts fuse 10 pairs. */
    private static final int FUEL_PER_HEART = 400;

    private static final Map<Set<EntityType<?>>, Supplier<? extends EntityType<?>>> HYBRIDS = Map.of(
            Set.of(EntityType.SHEEP, EntityType.COW), DVSouls.WOOLLY_COW,
            Set.of(EntityType.CHICKEN, EntityType.PIG), DVSouls.FEATHERED_PIG,
            Set.of(EntityType.HORSE, EntityType.STRIDER), DVSouls.LAVA_HORSE,
            Set.of(EntityType.WOLF, EntityType.FOX), DVSouls.WOLF_FOX);

    private SoulFuser() {}

    /** Each pair of creatures and the hybrid it makes, for recipe viewers. */
    public static Map<Set<EntityType<?>>, Supplier<? extends EntityType<?>>> hybrids() {
        return HYBRIDS;
    }

    /** The hybrid the two jars' souls make, or null. */
    @Nullable
    static EntityType<?> hybrid(ItemStack a, ItemStack b) {
        EntityType<?> first = Soul.type(Soul.get(a)).orElse(null);
        EntityType<?> second = Soul.type(Soul.get(b)).orElse(null);
        if (first == null || second == null || first == second) return null;
        Supplier<? extends EntityType<?>> hybrid = HYBRIDS.get(Set.of(first, second));
        return hybrid == null ? null : hybrid.get();
    }

    /** A full jar that really holds a soul (one taken from a recipe viewer's list has none). */
    static boolean isSoul(ItemStack stack) {
        return stack.is(DVSouls.FILLED_SOUL_JAR.get()) && Soul.hearts(Soul.get(stack)) > 0;
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
            return level.isClientSide ? null : createTickerHelper(type, DVSouls.SOUL_FUSER_ENTITY.get(), Entity::tick);
        }

        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof Entity fuser) player.openMenu(fuser);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Entity fuser) {
                net.minecraft.world.Containers.dropContents(level, pos, fuser.items);
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

    public static class Entity extends BlockEntity implements MenuProvider {
        final SimpleContainer items = new SimpleContainer(5) {
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
        private final ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> progress;
                    case 1 -> fuel;
                    default -> fuelMax;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> progress = value;
                    case 1 -> fuel = value;
                    default -> fuelMax = value;
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };

        public Entity(BlockPos pos, BlockState state) {
            super(DVSouls.SOUL_FUSER_ENTITY.get(), pos, state);
        }

        /** Ready to fuse: two souls that make a hybrid, an empty result slot, and room for the jar left over. */
        private boolean canFuse() {
            ItemStack spare = items.getItem(EMPTY);
            return hybrid(items.getItem(A), items.getItem(B)) != null && items.getItem(RESULT).isEmpty()
                    && (spare.isEmpty() || spare.getCount() < spare.getMaxStackSize());
        }

        static void tick(Level level, BlockPos pos, BlockState state, Entity fuser) {
            boolean ready = fuser.canFuse();
            ItemStack fuelStack = fuser.items.getItem(FUEL);
            if (fuser.fuel <= 0 && ready && isSoul(fuelStack)) {
                fuser.fuelMax = fuser.fuel = Math.max(1, Math.round(Soul.hearts(Soul.get(fuelStack)) * FUEL_PER_HEART));
                fuser.items.setItem(FUEL, new ItemStack(DVSouls.SOUL_JAR.get()));
                level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1, 0.7f);
            }
            if (fuser.fuel > 0) {
                fuser.fuel--;
                fuser.progress = ready ? fuser.progress + 1 : 0;
                if (fuser.progress >= FUSE_TIME) {
                    fuser.progress = 0;
                    fuser.fuse((ServerLevel) level);
                }
            } else {
                fuser.progress = 0;
            }
            boolean lit = fuser.fuel > 0;
            if (state.getValue(Block.LIT) != lit) level.setBlock(pos, state.setValue(Block.LIT, lit), 3);
            fuser.setChanged();
        }

        /** A fresh creature of the hybrid's kind (never put in the world) gives the new soul. */
        private void fuse(ServerLevel level) {
            EntityType<?> type = hybrid(items.getItem(A), items.getItem(B));
            if (type == null || !(type.create(level) instanceof Mob hybrid)) return;
            hybrid.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition), MobSpawnType.CONVERSION, null, null);
            CompoundTag soul = Soul.of(hybrid);
            hybrid.discard();
            items.setItem(A, ItemStack.EMPTY);
            items.setItem(B, ItemStack.EMPTY);
            items.setItem(RESULT, Soul.put(new ItemStack(DVSouls.FILLED_SOUL_JAR.get()), soul));
            ItemStack spare = items.getItem(EMPTY);
            if (spare.isEmpty()) items.setItem(EMPTY, new ItemStack(DVSouls.SOUL_JAR.get()));
            else spare.grow(1);
            level.playSound(null, worldPosition, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.BLOCKS, 0.6f, 1.4f);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.03);
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("container.dvsouls.soul_fuser");
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new Menu(id, inventory, items, data);
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

    /** Two soul slots, the fuel below, an arrow to the hybrid's jar and the jar left empty; the inventory under it. */
    public static class Menu extends AbstractContainerMenu {
        private final Container fuser;
        private final ContainerData data;

        public Menu(int id, Inventory inventory) {
            this(id, inventory, new SimpleContainer(5), new SimpleContainerData(3));
        }

        Menu(int id, Inventory inventory, Container fuser, ContainerData data) {
            super(DVSouls.SOUL_FUSER_MENU.get(), id);
            this.fuser = fuser;
            this.data = data;
            addSlot(new SoulSlot(fuser, A, 26, 17));
            addSlot(new SoulSlot(fuser, B, 62, 17));
            addSlot(new SoulSlot(fuser, FUEL, 44, 53));
            addSlot(new OutputSlot(fuser, RESULT, 116, 35));
            addSlot(new OutputSlot(fuser, EMPTY, 144, 35));
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
            addDataSlots(data);
        }

        public float progress() {
            return data.get(0) / (float) FUSE_TIME;
        }

        public float fuel() {
            return data.get(2) <= 0 ? 0 : data.get(1) / (float) data.get(2);
        }

        @Override
        public boolean stillValid(Player player) {
            return fuser.stillValid(player);
        }

        /** Shift-click: out of the fuser into the inventory, or a soul from the inventory into the first free soul slot. */
        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            Slot slot = slots.get(index);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            if (index < 5) {
                if (!moveItemStackTo(stack, 5, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!isSoul(stack) || !moveItemStackTo(stack, A, FUEL + 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            return original;
        }
    }

    private static class SoulSlot extends Slot {
        SoulSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isSoul(stack);
        }
    }

    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
