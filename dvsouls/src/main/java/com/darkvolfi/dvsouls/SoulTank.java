package com.darkvolfi.dvsouls;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * The soul machines' store of pure soul, shown as the bar at their side. One soul is one heart's worth
 * ({@link SoulsConfig#PURE_SOUL_PER_HEART} mB). It fills from the slot under the bar (a full soul jar, a bucket or bottle
 * of pure soul, which leave their empty jar, bucket or bottle there), from a bucket clicked on the machine, or from pipes.
 */
public class SoulTank extends FluidTank {
    private final Runnable changed;

    public SoulTank(int capacity, Runnable changed) {
        super(capacity, fluid -> fluid.getFluid() == DVSouls.PURE_SOUL.get());
        this.changed = changed;
    }

    @Override
    protected void onContentsChanged() {
        changed.run();
    }

    /** The pure soul an item pours into the bar, mB: a jar's hearts, a bucket or a bottle; 0 for anything else. */
    public static int amount(ItemStack stack) {
        if (stack.is(DVSouls.PURE_SOUL_BUCKET.get())) return FluidType.BUCKET_VOLUME;
        if (stack.is(DVSouls.PURE_SOUL_BOTTLE.get())) return SoulFuel.BOTTLE;
        return SoulFuser.isSoul(stack) ? Math.max(1, Math.round(Soul.hearts(Soul.get(stack)) * SoulsConfig.PURE_SOUL_PER_HEART.get())) : 0;
    }

    /** What is left in the slot under the bar once poured: an empty jar, bucket or bottle. */
    public static boolean isEmptied(ItemStack stack) {
        return stack.is(DVSouls.SOUL_JAR.get()) || stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE);
    }

    /** Souls (hearts) in this much pure soul. */
    public static float souls(int amount) {
        return amount / (float) SoulsConfig.PURE_SOUL_PER_HEART.get();
    }

    /** One soul's worth, mB. */
    public static int soul() {
        return SoulsConfig.PURE_SOUL_PER_HEART.get();
    }

    /**
     * Pours the item in {@code slot} into the tank if it fits (or into an empty tank, though too big for it: the rest is
     * lost), leaving its empty container in the slot.
     */
    public void pour(Container container, int slot) {
        ItemStack stack = container.getItem(slot);
        int amount = amount(stack);
        if (amount <= 0 || getSpace() < amount && !isEmpty()) return;
        fill(new FluidStack(DVSouls.PURE_SOUL.get(), amount), IFluidHandler.FluidAction.EXECUTE);
        container.setItem(slot, stack.getCraftingRemainingItem());
    }

    /** Fills the empty bucket or glass bottle in {@code slot} from the tank, if there's enough for it. */
    public void fillInto(Container container, int slot) {
        ItemStack stack = container.getItem(slot);
        boolean bucket = stack.is(Items.BUCKET);
        if (!bucket && !stack.is(Items.GLASS_BOTTLE)) return;
        int amount = bucket ? FluidType.BUCKET_VOLUME : SoulFuel.BOTTLE;
        if (getFluidAmount() < amount) return;
        drain(amount, IFluidHandler.FluidAction.EXECUTE);
        container.setItem(slot, new ItemStack(bucket ? DVSouls.PURE_SOUL_BUCKET.get() : DVSouls.PURE_SOUL_BOTTLE.get()));
    }

    /** Takes {@code amount} if there is that much; false (and nothing taken) if not. */
    public boolean spend(int amount) {
        if (getFluidAmount() < amount) return false;
        drain(amount, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }
}
