package com.darkvolfi.dvsouls;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.darkvolfi.dvsouls.SoulMachine.EMPTY;
import static com.darkvolfi.dvsouls.SoulMachine.FUEL;
import static com.darkvolfi.dvsouls.SoulMachine.RESULT;

/**
 * The Soul Fuser: two full soul jars go in, and burning souls from its bar it fuses them into one. Two kinds that
 * make a hybrid give the hybrid's soul (which two make which is fixed; the order doesn't matter); two of the same kind
 * give the first one stronger, with both their health (up to a server setting). One jar is left empty; it goes to the
 * empty-jar slot. Hoppers put souls in from above, what fills the bar from the sides, and take from below.
 */
public final class SoulFuser {
    public static final int A = 0, B = 1;

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

    /** The first soul made stronger by the second, of the same kind; null if they differ or it can't get stronger. */
    @Nullable
    static CompoundTag empowered(ItemStack a, ItemStack b) {
        CompoundTag first = Soul.get(a), second = Soul.get(b);
        EntityType<?> type = Soul.type(first).orElse(null);
        if (type == null || type != Soul.type(second).orElse(null) || type == DVSouls.CORRUPTED.get()) return null;
        float health = Soul.hearts(first) * 2;
        float stronger = Math.min(health + Soul.hearts(second) * 2,
                Soul.baseHealth(type) * SoulsConfig.EMPOWER_MAX.get().floatValue());
        return stronger > health + 0.01f ? Soul.withMaxHealth(first, stronger) : null;
    }

    /** A full jar that really holds a soul (one taken from a recipe viewer's list has none). */
    static boolean isSoul(ItemStack stack) {
        return stack.is(DVSouls.FILLED_SOUL_JAR.get()) && Soul.hearts(Soul.get(stack)) > 0;
    }

    public static class Block extends SoulMachine.Block {
        public Block() {
            super(DVSouls.SOUL_FUSER_ENTITY);
        }
    }

    public static class Entity extends SoulMachine.Entity {
        private static final int[] TOP = {A, B}, SIDES = {FUEL};

        public Entity(BlockPos pos, BlockState state) {
            super(DVSouls.SOUL_FUSER_ENTITY.get(), pos, state);
        }

        /** Two souls that make a hybrid or a stronger one, and an empty result slot. */
        @Override
        protected boolean canWork() {
            ItemStack a = items.getItem(A), b = items.getItem(B);
            return (hybrid(a, b) != null || empowered(a, b) != null) && items.getItem(RESULT).isEmpty();
        }

        @Override
        protected int workTime() {
            return SoulsConfig.FUSE_TIME.get();
        }

        /** A hybrid's soul comes from a fresh creature of its kind (never put in the world). */
        @Override
        protected void work(ServerLevel level) {
            CompoundTag soul = empowered(items.getItem(A), items.getItem(B));
            EntityType<?> type = hybrid(items.getItem(A), items.getItem(B));
            if (type != null && type.create(level) instanceof Mob hybrid) {
                hybrid.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition), MobSpawnType.CONVERSION, null, null);
                soul = Soul.of(hybrid);
                hybrid.discard();
            }
            if (soul == null) return;
            items.setItem(A, ItemStack.EMPTY);
            items.setItem(B, ItemStack.EMPTY);
            items.setItem(RESULT, Soul.put(new ItemStack(DVSouls.FILLED_SOUL_JAR.get()), soul));
            level.playSound(null, worldPosition, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.BLOCKS, 0.6f, 1.4f);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.03);
        }

        // ---- for hoppers: souls in from above, what fills the bar from the sides, the results out from below

        @Override
        public int[] getSlotsForFace(Direction side) {
            return side == Direction.UP ? TOP : side == Direction.DOWN ? bottom() : SIDES;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == FUEL ? SoulFurnace.isFuel(stack) : slot < FUEL && isSoul(stack);
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("container.dvsouls.soul_fuser");
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new Menu(id, inventory, items, data);
        }
    }

    /** Two soul slots, the bar of souls at the side with its slot under it, an arrow to the hybrid's jar and the jar left empty; the inventory under it. */
    public static class Menu extends SoulMachine.Menu {
        public Menu(int id, Inventory inventory) {
            this(id, inventory, new SimpleContainer(SoulMachine.SIZE), new SimpleContainerData(4));
        }

        Menu(int id, Inventory inventory, Container fuser, ContainerData data) {
            super(DVSouls.SOUL_FUSER_MENU.get(), id, inventory, fuser, data);
        }

        @Override
        protected void addMachineSlots(Container fuser) {
            addSlot(new SoulMachine.FilterSlot(fuser, A, 26, 17, SoulFuser::isSoul));
            addSlot(new SoulMachine.FilterSlot(fuser, B, 62, 17, SoulFuser::isSoul));
            addSlot(new SoulMachine.FilterSlot(fuser, FUEL, 9, 53, SoulFurnace::isFuel));
            addSlot(new SoulMachine.FilterSlot(fuser, RESULT, 116, 35, null));
            addSlot(new SoulMachine.FilterSlot(fuser, EMPTY, 144, 35, null));
        }

        /** A soul goes into the first free soul slot, or else under the bar; pure soul under the bar. */
        @Override
        protected boolean moveIn(ItemStack stack) {
            if (isSoul(stack)) return move(stack, A, FUEL + 1);
            return SoulFurnace.isFuel(stack) && move(stack, FUEL, FUEL + 1);
        }
    }
}
