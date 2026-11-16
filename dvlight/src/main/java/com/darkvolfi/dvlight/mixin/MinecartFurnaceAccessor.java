package com.darkvolfi.dvlight.mixin;

import net.minecraft.world.entity.vehicle.MinecartFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Whether a furnace minecart burns (synced to the client), which is protected. */
@Mixin(MinecartFurnace.class)
public interface MinecartFurnaceAccessor {
    @Invoker("hasFuel")
    boolean dvlight$hasFuel();
}
