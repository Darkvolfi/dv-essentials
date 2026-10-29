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
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
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
 * The Soul Furnace: smelts like a furnace, twice as fast, and burns only souls. A full soul jar is its fuel, good for
 * 2 items per heart (a server setting); the empty jar is left in the fuel slot.
 */
public final class SoulFurnace {
    /** A vanilla furnace's time for one item, in its own ticks (this one runs two of them per game tick). */
    private static final int ITEM_TIME = 200;

    private SoulFurnace() {}

    /** A full jar that really holds a soul (one taken from a recipe viewer's list has none). */
    public static boolean isFuel(ItemStack stack) {
        return SoulFuser.isSoul(stack);
    }

    public static class Block extends AbstractFurnaceBlock {
        public Block() {
            super(BlockBehaviour.Properties.copy(Blocks.FURNACE).lightLevel(state -> state.getValue(LIT) ? 10 : 0));
        }

        @Override
        protected void openContainer(Level level, BlockPos pos, Player player) {
            if (level.getBlockEntity(pos) instanceof Entity furnace) player.openMenu(furnace);
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Entity(pos, state);
        }

        /** Two furnace ticks for every game tick: everything goes twice as fast, the fuel too. */
        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide) return null;
            return createTickerHelper(type, DVSouls.SOUL_FURNACE_ENTITY.get(), (l, pos, s, furnace) -> {
                AbstractFurnaceBlockEntity.serverTick(l, pos, s, furnace);
                AbstractFurnaceBlockEntity.serverTick(l, pos, s, furnace);
            });
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(LIT)) return;
            double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
            if (random.nextDouble() < 0.1) level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1, 1, false);
            Direction facing = state.getValue(FACING);
            double side = random.nextDouble() * 0.6 - 0.3;
            double dx = facing.getAxis() == Direction.Axis.X ? facing.getStepX() * 0.52 : side;
            double dz = facing.getAxis() == Direction.Axis.Z ? facing.getStepZ() * 0.52 : side;
            double dy = random.nextDouble() * 6 / 16;
            level.addParticle(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 0, 0, 0);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + dx, y + dy, z + dz, 0, 0, 0);
        }
    }

    public static class Entity extends AbstractFurnaceBlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(DVSouls.SOUL_FURNACE_ENTITY.get(), pos, state, RecipeType.SMELTING);
        }

        @Override
        protected Component getDefaultName() {
            return Component.translatable("container.dvsouls.soul_furnace");
        }

        @Override
        protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
            return new Menu(id, inventory, this, dataAccess);
        }

        @Override
        protected int getBurnDuration(ItemStack fuel) {
            // Qualified: the furnace class has a static isFuel of its own (the game's fuels), which would win here.
            return SoulFurnace.isFuel(fuel) ? Math.max(1, Math.round(Soul.hearts(Soul.get(fuel)) * SoulsConfig.FURNACE_ITEMS_PER_HEART.get() * ITEM_TIME)) : 0;
        }

        /** The fuel slot (1) takes only souls; the output slot (2) nothing. */
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == 2) return false;
            return slot != 1 || SoulFurnace.isFuel(stack);
        }
    }

    public static class Menu extends AbstractFurnaceMenu {
        public Menu(int id, Inventory inventory) {
            super(DVSouls.SOUL_FURNACE_MENU.get(), RecipeType.SMELTING, RecipeBookType.FURNACE, id, inventory);
        }

        Menu(int id, Inventory inventory, Container furnace, ContainerData data) {
            super(DVSouls.SOUL_FURNACE_MENU.get(), RecipeType.SMELTING, RecipeBookType.FURNACE, id, inventory, furnace, data);
        }

        @Override
        protected boolean isFuel(ItemStack stack) {
            return SoulFurnace.isFuel(stack);
        }
    }
}
