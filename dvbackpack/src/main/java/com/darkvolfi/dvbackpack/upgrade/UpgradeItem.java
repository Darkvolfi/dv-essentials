package com.darkvolfi.dvbackpack.upgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An upgrade card: goes into a backpack's upgrade slot and gives it a tool. Other mods may add their own cards of a
 * type, overriding the methods below (a furnace card burning other fuel, faster).
 */
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

    /** The panel a card in an upgrade slot opens: its type, {@link UpgradeType#HELD} for another mod's item that holds items, or null. */
    public static @Nullable UpgradeType panelType(ItemStack stack) {
        UpgradeType type = typeOf(stack);
        if (type != null) return type;
        return isCard(stack) && stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent() ? UpgradeType.HELD : null;
    }

    /** Other mods' items tagged {@code dvbackpack:upgrades} go into the upgrade slots too; one that holds items gets a panel of them. */
    public static final TagKey<Item> OTHER_CARDS = TagKey.create(Registries.ITEM, new ResourceLocation("dvbackpack", "upgrades"));

    /** Whether this goes into an upgrade slot: a card, or another mod's item tagged for it. */
    public static boolean isCard(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem || stack.is(OTHER_CARDS);
    }

    /** The start of this card's text keys: its panel's name, and its tooltip with ".desc". */
    protected String key() {
        return "dvbackpack.upgrade." + type.id();
    }

    /** The name shown on the open panel. */
    public Component panelName() {
        return Component.translatable(key());
    }

    /** How long a cooking card burns {@code fuel}: the game's fuel times by default. */
    public int burnTime(ItemStack fuel) {
        return type.cooking() == null ? 0 : ForgeHooks.getBurnTime(fuel, type.cooking());
    }

    /** Cooking steps a cooking card takes each tick. */
    public int speed() {
        return 1;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(key() + ".desc").withStyle(ChatFormatting.GRAY));
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
