package com.darkvolfi.dvbackpack.upgrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.Locale;

/** A fluid tank that lives on its card, like the furnace cards' slots: the fluid moves with the card. */
public final class TankUpgrade {
    /** Eight buckets. */
    public static final int CAPACITY = 8000;

    private TankUpgrade() {}

    public static FluidTank tank(ItemStack card) {
        FluidTank tank = new FluidTank(CAPACITY);
        CompoundTag tag = card.getTagElement("Tank");
        if (tag != null) tank.readFromNBT(tag);
        return tank;
    }

    public static void save(ItemStack card, FluidTank tank) {
        card.getOrCreateTag().put("Tank", tank.writeToNBT(new CompoundTag()));
    }

    public static FluidStack fluid(ItemStack card) {
        return tank(card).getFluid();
    }

    /** An amount in buckets, with one decimal: "2.5". */
    public static String buckets(int millibuckets) {
        return String.format(Locale.ROOT, "%.1f", millibuckets / 1000f);
    }
}
