package com.darkvolfi.dvbackpack.upgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** An upgrade card: goes into a backpack's upgrade slot and gives it a tool. */
public class UpgradeItem extends Item {
    private final UpgradeType type;

    public UpgradeItem(UpgradeType type) {
        super(new Properties().stacksTo(1));
        this.type = type;
    }

    public UpgradeType type() {
        return type;
    }

    public static @Nullable UpgradeType typeOf(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem item ? item.type : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("dvbackpack.upgrade." + type.id() + ".desc").withStyle(ChatFormatting.GRAY));
        if (type.cooking() != null) {
            List<Component> inside = CookingUpgrade.describe(stack);
            inside.forEach(line -> tooltip.add(line.copy().withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (type == UpgradeType.TANK) {
            FluidStack fluid = TankUpgrade.fluid(stack);
            if (!fluid.isEmpty()) {
                tooltip.add(Component.translatable("dvbackpack.tank.amount", fluid.getDisplayName(), TankUpgrade.buckets(fluid.getAmount()), TankUpgrade.CAPACITY / 1000)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
