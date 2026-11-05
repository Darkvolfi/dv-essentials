package com.darkvolfi.dvsouls;

import com.darkvolfi.dvsouls.charm.Ability;
import com.darkvolfi.dvsouls.charm.EssenceItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static com.darkvolfi.dvsouls.SoulMachine.EMPTY;
import static com.darkvolfi.dvsouls.SoulMachine.FUEL;
import static com.darkvolfi.dvsouls.SoulMachine.RESULT;

/**
 * The Soul Distiller: burning souls from its bar, it distils a soul and an amethyst shard into that creature's
 * essence, for a soul charm. Only creatures with an {@link Ability} have one. The soul's jar is left empty. Hoppers put
 * the soul in from above, amethyst and what fills the bar from the sides, and take from below.
 */
public final class SoulDistiller {
    public static final int SOUL = 0, AMETHYST = 1;

    private SoulDistiller() {}

    /** An empty test tube, for the essence to drip into. */
    public static boolean isTube(ItemStack stack) {
        return stack.is(DVSouls.SOUL_TUBE.get());
    }

    /** A soul that has an essence. */
    public static boolean canDistil(ItemStack stack) {
        return SoulFuser.isSoul(stack) && Soul.type(Soul.get(stack)).map(Ability::of).isPresent();
    }

    /** AMETHYST: a shard in its slot, standing in the model between the funnel and the tube. */
    public static class Block extends SoulMachine.Block {
        public static final BooleanProperty AMETHYST_IN = BooleanProperty.create("amethyst");

        public Block() {
            super(DVSouls.SOUL_DISTILLER_ENTITY);
            registerDefaultState(defaultBlockState().setValue(AMETHYST_IN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(AMETHYST_IN);
        }

        /** While it works, drops fall from the funnel's tip onto the essence below. */
        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            super.animateTick(state, level, pos, random);
            if (!state.getValue(LIT) || random.nextInt(3) != 0) return;
            Vec3 tip = modelPoint(state, 10, 10.3, 8);
            level.addParticle(ParticleTypes.FALLING_OBSIDIAN_TEAR, pos.getX() + tip.x, pos.getY() + tip.y, pos.getZ() + tip.z, 0, 0, 0);
        }
    }

    public static class Entity extends SoulMachine.Entity {
        private static final int[] TOP = {SOUL}, SIDES = {AMETHYST, FUEL, RESULT};

        public Entity(BlockPos pos, BlockState state) {
            super(DVSouls.SOUL_DISTILLER_ENTITY.get(), pos, state);
        }

        @Override
        protected BlockState shown(BlockState state) {
            return state.setValue(Block.AMETHYST_IN, !items.getItem(AMETHYST).isEmpty());
        }

        private @Nullable ItemStack essence() {
            ItemStack soul = items.getItem(SOUL);
            if (!canDistil(soul)) return null;
            EntityType<?> type = Soul.type(Soul.get(soul)).orElseThrow();
            return EssenceItem.of(new ItemStack(DVSouls.SOUL_ESSENCE.get()), type);
        }

        /** A soul with an essence, an amethyst shard, and an empty test tube standing under the funnel for it. */
        @Override
        protected boolean canWork() {
            return essence() != null && items.getItem(AMETHYST).is(Items.AMETHYST_SHARD) && isTube(items.getItem(RESULT));
        }

        @Override
        protected int workTime() {
            return SoulsConfig.DISTIL_TIME.get();
        }

        @Override
        protected void work(ServerLevel level) {
            ItemStack essence = essence();
            if (essence == null) return;
            // the tube under the funnel fills: it is the essence now
            items.setItem(RESULT, essence);
            items.setItem(SOUL, ItemStack.EMPTY);
            items.getItem(AMETHYST).shrink(1);
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, 1);
            level.sendParticles(ParticleTypes.SOUL, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.03);
        }

        // ---- for hoppers: the soul in from above, amethyst, empty tubes and what fills the bar from the sides, the results out
        // from below

        @Override
        public int[] getSlotsForFace(Direction side) {
            return side == Direction.UP ? TOP : side == Direction.DOWN ? bottom() : SIDES;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return switch (slot) {
                case SOUL -> canDistil(stack);
                case AMETHYST -> stack.is(Items.AMETHYST_SHARD);
                case FUEL -> SoulFurnace.isFuel(stack);
                case RESULT -> isTube(stack);
                default -> false;
            };
        }

        /** Amethyst stacks up; the jars and tubes go in one at a time. */
        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
            return slot == AMETHYST ? canPlaceItem(slot, stack) : super.canPlaceItemThroughFace(slot, stack, side);
        }

        /** An empty tube waiting under the funnel stays; a filled one comes out. */
        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return !(slot == RESULT && isTube(stack)) && super.canTakeItemThroughFace(slot, stack, side);
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("container.dvsouls.soul_distiller");
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new Menu(id, inventory, items, data);
        }
    }

    /** The soul over the amethyst, the bar of souls at the side with its slot under it, and an arrow to the essence and the jar left empty. */
    public static class Menu extends SoulMachine.Menu {
        public Menu(int id, Inventory inventory) {
            this(id, inventory, new SimpleContainer(SoulMachine.SIZE), new SimpleContainerData(4));
        }

        Menu(int id, Inventory inventory, Container distiller, ContainerData data) {
            super(DVSouls.SOUL_DISTILLER_MENU.get(), id, inventory, distiller, data);
        }

        @Override
        protected void addMachineSlots(Container distiller) {
            addSlot(new SoulMachine.FilterSlot(distiller, SOUL, 44, 17, SoulDistiller::canDistil));
            addSlot(new SoulMachine.FilterSlot(distiller, AMETHYST, 44, 35, stack -> stack.is(Items.AMETHYST_SHARD)));
            addSlot(new SoulMachine.FilterSlot(distiller, FUEL, 9, 53, SoulFurnace::isFuel));
            // one empty tube at a time: it is filled where it stands
            addSlot(new SoulMachine.FilterSlot(distiller, RESULT, 116, 35, SoulDistiller::isTube) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
            addSlot(new SoulMachine.FilterSlot(distiller, EMPTY, 144, 35, null));
        }

        /** Amethyst to its slot, an empty tube under the funnel; a soul with an essence to be distilled, anything else that
         * fills the bar under it. */
        @Override
        protected boolean moveIn(ItemStack stack) {
            if (stack.is(Items.AMETHYST_SHARD)) return move(stack, AMETHYST, AMETHYST + 1);
            if (isTube(stack)) return move(stack, RESULT, RESULT + 1);
            if (canDistil(stack) && move(stack, SOUL, SOUL + 1)) return true;
            return SoulFurnace.isFuel(stack) && move(stack, FUEL, FUEL + 1);
        }
    }
}
