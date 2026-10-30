package com.darkvolfi.dvsouls;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.Locale;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Soul Furnace, Soul Smoker and Soul Blast Furnace: each cooks like the game's own, twice as fast, burning souls from
 * the bar at its side ({@link SoulTank}), 2 items a soul (a server setting).
 */
public final class SoulFurnace {
    public enum Kind {
        FURNACE(RecipeType.SMELTING, RecipeBookType.FURNACE, 200, SoundEvents.FURNACE_FIRE_CRACKLE),
        SMOKER(RecipeType.SMOKING, RecipeBookType.SMOKER, 100, SoundEvents.SMOKER_SMOKE),
        BLAST_FURNACE(RecipeType.BLASTING, RecipeBookType.BLAST_FURNACE, 100, SoundEvents.BLASTFURNACE_FIRE_CRACKLE);

        private final RecipeType<? extends AbstractCookingRecipe> recipes;
        private final RecipeBookType book;
        private final int itemTime;
        private final SoundEvent sound;

        Kind(RecipeType<? extends AbstractCookingRecipe> recipes, RecipeBookType book, int itemTime, SoundEvent sound) {
            this.recipes = recipes;
            this.book = book;
            this.itemTime = itemTime;
            this.sound = sound;
        }

        public RecipeType<? extends AbstractCookingRecipe> recipes() {
            return recipes;
        }

        /** The game's time for one item (in this kind's own ticks; the soul ones run two of them per game tick). */
        public int itemTime() {
            return itemTime;
        }

        /** "soul_furnace", "soul_smoker", "soul_blast_furnace". */
        public String id() {
            return "soul_" + name().toLowerCase(Locale.ROOT);
        }

        BlockEntityType<Entity> entityType() {
            return DVSouls.FURNACE_ENTITIES.get(this).get();
        }

        MenuType<Menu> menuType() {
            return DVSouls.FURNACE_MENUS.get(this).get();
        }
    }

    private SoulFurnace() {}

    /** What pours into the bar ({@link SoulTank#amount}), for the slot under it. */
    public static boolean isFuel(ItemStack stack) {
        return SoulTank.amount(stack) > 0;
    }

    /**
     * How long soul fuel burns in a DV Backpack card, given the time of one item: enough for 2 items per heart (a server
     * setting). A full jar, or a bucket or bottle of pure soul.
     */
    public static int burnTime(ItemStack fuel, int itemTime) {
        float hearts = SoulFuel.offered(fuel);
        return hearts > 0 ? Math.max(1, Math.round(hearts * SoulsConfig.FURNACE_ITEMS_PER_HEART.get() * itemTime)) : 0;
    }

    public static class Block extends AbstractFurnaceBlock {
        private final Kind kind;

        public Block(Kind kind) {
            super(BlockBehaviour.Properties.copy(Blocks.FURNACE).lightLevel(state -> state.getValue(LIT) ? 10 : 0));
            this.kind = kind;
        }

        /** A bucket of pure soul empties into the bar; anything else opens the furnace. */
        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) return InteractionResult.sidedSuccess(level.isClientSide);
            return super.use(state, level, pos, player, hand, hit);
        }

        @Override
        protected void openContainer(Level level, BlockPos pos, Player player) {
            if (level.getBlockEntity(pos) instanceof Entity furnace) player.openMenu(furnace);
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Entity(kind, pos, state);
        }

        /** Pours the slot under the bar in, then two furnace ticks for every game tick: everything goes twice as fast. */
        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide) return null;
            return createTickerHelper(type, kind.entityType(), (l, pos, s, furnace) -> {
                furnace.tank.pour(furnace, 1);
                for (int i = 0; i < 2; i++) {
                    furnace.ignite(l, pos);
                    AbstractFurnaceBlockEntity.serverTick(l, pos, l.getBlockState(pos), furnace);
                }
            });
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(LIT)) return;
            double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
            if (random.nextDouble() < 0.1) level.playLocalSound(x, y, z, kind.sound, SoundSource.BLOCKS, 1, 1, false);
            Direction facing = state.getValue(FACING);
            double side = random.nextDouble() * 0.6 - 0.3;
            double dx = facing.getAxis() == Direction.Axis.X ? facing.getStepX() * 0.52 : side;
            double dz = facing.getAxis() == Direction.Axis.Z ? facing.getStepZ() * 0.52 : side;
            double dy = random.nextDouble() * 6 / 16;
            level.addParticle(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 0, 0, 0);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + dx, y + dy, z + dz, 0, 0, 0);
            // the smoker also smokes from its top
            if (kind == Kind.SMOKER) level.addParticle(ParticleTypes.SMOKE, x, y + 1.1, z, 0, 0, 0);
        }
    }

    /**
     * The furnace burns souls from its bar, not from a fuel slot: whenever it is out of fire with something to cook, it
     * takes half a soul (a server setting: items per soul) and burns for one item. The game's fuel slot (1) is the slot
     * under the bar, which pours in what it is given.
     */
    public static class Entity extends AbstractFurnaceBlockEntity {
        public static final int CAPACITY = 4 * FluidType.BUCKET_VOLUME;
        private final Kind kind;
        final SoulTank tank = new SoulTank(CAPACITY, this::setChanged);
        private final LazyOptional<IFluidHandler> tankHandler = LazyOptional.of(() -> tank);

        public Entity(Kind kind, BlockPos pos, BlockState state) {
            super(kind.entityType(), pos, state, kind.recipes);
            this.kind = kind;
        }

        /** Out of fire with an item to cook and room for it: takes the souls for one item from the bar and burns for it. */
        private void ignite(Level level, BlockPos pos) {
            if (dataAccess.get(0) > 0 || !canCook(level)) return;
            if (!tank.spend(Math.max(1, SoulTank.soul() / SoulsConfig.FURNACE_ITEMS_PER_HEART.get()))) return;
            dataAccess.set(0, kind.itemTime);
            dataAccess.set(1, kind.itemTime);
            BlockState state = level.getBlockState(pos);
            if (!state.getValue(AbstractFurnaceBlock.LIT)) level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, true), 3);
        }

        private boolean canCook(Level level) {
            if (items.get(0).isEmpty()) return false;
            AbstractCookingRecipe recipe = recipe(level, kind.recipes);
            if (recipe == null) return false;
            ItemStack result = recipe.assemble(this, level.registryAccess());
            ItemStack out = items.get(2);
            return !result.isEmpty() && (out.isEmpty()
                    || ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize());
        }

        @Nullable
        private <T extends AbstractCookingRecipe> T recipe(Level level, RecipeType<T> type) {
            return level.getRecipeManager().getRecipeFor(type, this, level).orElse(null);
        }

        /** Souls in the bar, mB. */
        int amount() {
            return tank.getFluidAmount();
        }

        /** From below: the cooked items, and the empty jar, bucket or bottle left in the slot under the bar. */
        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return side == Direction.DOWN && (slot == 2 || slot == 1 && SoulTank.isEmptied(stack));
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

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.put("Tank", tank.writeToNBT(new CompoundTag()));
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            tank.readFromNBT(tag.getCompound("Tank"));
        }

        @Override
        protected Component getDefaultName() {
            return Component.translatable("container.dvsouls." + kind.id());
        }

        @Override
        protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
            return new Menu(kind, id, inventory, this, dataAccess);
        }

        /** Nothing burns from the slot: it pours into the bar instead. */
        @Override
        protected int getBurnDuration(ItemStack fuel) {
            return 0;
        }

        /** The slot under the bar (1) takes what pours in, one at a time; the output slot (2) nothing. */
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == 2) return false;
            // Qualified: the furnace class has a static isFuel of its own (the game's fuels), which would win here.
            return slot != 1 || SoulFurnace.isFuel(stack) && items.get(1).isEmpty();
        }
    }

    /** The game's furnace menu, with the souls in the bar as one more data slot. */
    public static class Menu extends AbstractFurnaceMenu {
        private final Kind kind;
        private final DataSlot amount;

        public Menu(Kind kind, int id, Inventory inventory) {
            super(kind.menuType(), kind.recipes, kind.book, id, inventory);
            this.kind = kind;
            amount = addDataSlot(DataSlot.standalone());
        }

        Menu(Kind kind, int id, Inventory inventory, Entity furnace, ContainerData data) {
            super(kind.menuType(), kind.recipes, kind.book, id, inventory, furnace, data);
            this.kind = kind;
            amount = addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return furnace.amount();
                }

                @Override
                public void set(int value) {}
            });
        }

        public Kind kind() {
            return kind;
        }

        /** Souls in the bar, mB. */
        public int amount() {
            return amount.get();
        }

        @Override
        protected boolean isFuel(ItemStack stack) {
            return SoulFurnace.isFuel(stack);
        }
    }
}
