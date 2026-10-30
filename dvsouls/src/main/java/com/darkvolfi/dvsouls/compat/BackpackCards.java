package com.darkvolfi.dvsouls.compat;

import com.darkvolfi.dvbackpack.upgrade.UpgradeItem;
import com.darkvolfi.dvbackpack.upgrade.UpgradeType;
import com.darkvolfi.dvsouls.SoulFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

/**
 * DV Backpack: upgrade cards of the soul furnaces. Each cooks in the backpack like the backpack's own furnace, smoker or
 * blast furnace card, but burns souls and goes twice as fast. Only registered with DV Backpack.
 */
public final class BackpackCards {
    private BackpackCards() {}

    public static void register(DeferredRegister<Item> items, Map<SoulFurnace.Kind, RegistryObject<Item>> cards) {
        for (SoulFurnace.Kind kind : SoulFurnace.Kind.values()) {
            cards.put(kind, items.register(kind.id() + "_upgrade", () -> new SoulCookingCard(kind)));
        }
    }

    private static final class SoulCookingCard extends UpgradeItem {
        private final SoulFurnace.Kind kind;

        SoulCookingCard(SoulFurnace.Kind kind) {
            super(switch (kind) {
                case FURNACE -> UpgradeType.FURNACE;
                case SMOKER -> UpgradeType.SMOKER;
                case BLAST_FURNACE -> UpgradeType.BLAST_FURNACE;
            });
            this.kind = kind;
        }

        @Override
        protected String key() {
            return "dvsouls.upgrade." + kind.id();
        }

        @Override
        public int burnTime(ItemStack fuel) {
            return SoulFurnace.burnTime(fuel, kind.itemTime());
        }

        @Override
        public int speed() {
            return 2;
        }
    }
}
