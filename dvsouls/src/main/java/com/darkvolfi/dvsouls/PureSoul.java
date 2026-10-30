package com.darkvolfi.dvsouls;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;

import java.util.function.Consumer;

/** Pure soul: souls refined till they belong to no creature. A pale, glowing liquid, carried in buckets and bottles. */
public class PureSoul extends FluidType {
    private static final ResourceLocation STILL = new ResourceLocation(DVSouls.MOD_ID, "block/pure_soul_still");
    private static final ResourceLocation FLOWING = new ResourceLocation(DVSouls.MOD_ID, "block/pure_soul_flow");

    public PureSoul() {
        super(Properties.create()
                .descriptionId("block.dvsouls.pure_soul")
                .lightLevel(10)
                .density(800)
                .viscosity(1500)
                .canExtinguish(true)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOWING;
            }
        });
    }
}
