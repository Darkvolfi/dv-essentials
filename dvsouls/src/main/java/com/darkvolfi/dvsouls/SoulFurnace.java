package com.darkvolfi.dvsouls;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
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
 * The Soul Furnace, Soul Smoker and Soul Blast Furnace: each cooks like the game's own, twice as fast, and burns only
 * souls. A full soul jar is their fuel, good for 2 items per heart (a server setting); the empty jar is left in the fuel
 * slot.
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

    /** A full jar that really holds a soul (one taken from a recipe viewer's list has none). */
    public static boolean isFuel(ItemStack stack) {
        return SoulFuser.isSoul(stack);
    }

    /** How long a soul burns, given the time of one item: enough for 2 items per heart (a server setting). */
    public static int burnTime(ItemStack fuel, int itemTime) {
        return isFuel(fuel) ? Math.max(1, Math.round(Soul.hearts(Soul.get(fuel)) * SoulsConfig.FURNACE_ITEMS_PER_HEART.get() * itemTime)) : 0;
    }

    public static class Block extends AbstractFurnaceBlock {
        private final Kind kind;

        public Block(Kind kind) {
            super(BlockBehaviour.Properties.copy(Blocks.FURNACE).lightLevel(state -> state.getValue(LIT) ? 10 : 0));
            this.kind = kind;
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

        /** Two furnace ticks for every game tick: everything goes twice as fast, the fuel too. */
        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide) return null;
            return createTickerHelper(type, kind.entityType(), (l, pos, s, furnace) -> {
                AbstractFurnaceBlockEntity.serverTick(l, pos, s, furnace);
                AbstractFurnaceBlockEntity.serverTick(l, pos, s, furnace);
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

    public static class Entity extends AbstractFurnaceBlockEntity {
        private final Kind kind;

        public Entity(Kind kind, BlockPos pos, BlockState state) {
            super(kind.entityType(), pos, state, kind.recipes);
            this.kind = kind;
        }

        @Override
        protected Component getDefaultName() {
            return Component.translatable("container.dvsouls." + kind.id());
        }

        @Override
        protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
            return new Menu(kind, id, inventory, this, dataAccess);
        }

        @Override
        protected int getBurnDuration(ItemStack fuel) {
            return burnTime(fuel, kind.itemTime);
        }

        /** The fuel slot (1) takes only souls; the output slot (2) nothing. */
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == 2) return false;
            // Qualified: the furnace class has a static isFuel of its own (the game's fuels), which would win here.
            return slot != 1 || SoulFurnace.isFuel(stack);
        }
    }

    public static class Menu extends AbstractFurnaceMenu {
        private final Kind kind;

        public Menu(Kind kind, int id, Inventory inventory) {
            super(kind.menuType(), kind.recipes, kind.book, id, inventory);
            this.kind = kind;
        }

        Menu(Kind kind, int id, Inventory inventory, Container furnace, ContainerData data) {
            super(kind.menuType(), kind.recipes, kind.book, id, inventory, furnace, data);
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }

        @Override
        protected boolean isFuel(ItemStack stack) {
            return SoulFurnace.isFuel(stack);
        }
    }
}
