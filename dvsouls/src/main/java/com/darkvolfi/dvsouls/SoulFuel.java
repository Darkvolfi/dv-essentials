package com.darkvolfi.dvsouls;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

/**
 * What the altars take as an offering (and a DV Backpack soul card burns): a creature's soul in its jar, or pure soul,
 * which belongs to no creature (a bucket or a bottle of it). A creature's soul spent on another kind of creature may
 * corrupt it.
 */
public final class SoulFuel {
    /** Pure soul in a bottle; a bucket holds {@link FluidType#BUCKET_VOLUME}. */
    public static final int BOTTLE = 250;

    private SoulFuel() {}

    /** Hearts' worth of soul in an offering: a full jar's soul, or the pure soul in a bucket or bottle; 0 for anything else. */
    public static float offered(ItemStack stack) {
        if (SoulFuser.isSoul(stack)) return Soul.hearts(Soul.get(stack));
        if (stack.is(DVSouls.PURE_SOUL_BUCKET.get())) return SoulTank.souls(FluidType.BUCKET_VOLUME);
        if (stack.is(DVSouls.PURE_SOUL_BOTTLE.get())) return SoulTank.souls(BOTTLE);
        return 0;
    }

    /** The creature an offering's soul is of; null for pure soul, which is of none. */
    @Nullable
    public static EntityType<?> kind(ItemStack stack) {
        return SoulFuser.isSoul(stack) ? Soul.type(Soul.get(stack)).orElse(null) : null;
    }

    /** Whether spending a soul of {@code spent}'s kind on {@code creature} corrupts it: a chance (a server setting) unless pure or its own kind. */
    public static boolean corrupts(@Nullable EntityType<?> creature, @Nullable EntityType<?> spent, RandomSource random) {
        return spent != null && spent != creature && random.nextDouble() < SoulsConfig.CORRUPT_CHANCE.get();
    }
}
