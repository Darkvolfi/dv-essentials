package com.darkvolfi.dvlens.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Machine readings that work for any mod: Forge Energy (FE/RF) and fluid tanks through Forge's shared
 * capabilities, plus Create's rotation speed and network stress read from its saved data, so DV Lens needs no
 * Create dependency (if Create renames those keys the lines just disappear).
 * {@code energyMax} 0 = no energy storage; {@code rpm} NaN = not a Create kinetic block; {@code capacity} 0 =
 * stress unknown.
 */
public record MachineInfo(int energy, int energyMax, List<Tank> tanks, float rpm, float stress, float capacity) {
    private static final int MAX_TANKS = 4;

    public record Tank(FluidStack fluid, int capacity) {}

    static MachineInfo read(BlockEntity be) {
        int[] energy = {0, 0};
        be.getCapability(ForgeCapabilities.ENERGY).ifPresent(storage -> {
            energy[0] = storage.getEnergyStored();
            energy[1] = storage.getMaxEnergyStored();
        });
        List<Tank> tanks = new ArrayList<>();
        be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(handler -> {
            for (int i = 0; i < handler.getTanks() && tanks.size() < MAX_TANKS; i++) {
                if (handler.getTankCapacity(i) > 0) tanks.add(new Tank(handler.getFluidInTank(i).copy(), handler.getTankCapacity(i)));
            }
        });

        float rpm = Float.NaN, stress = 0, capacity = 0;
        ResourceLocation type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType());
        if (type != null && type.getNamespace().equals("create")) {
            CompoundTag tag = be.saveWithoutMetadata();
            if (tag.contains("Speed", Tag.TAG_FLOAT)) {
                rpm = tag.getFloat("Speed");
                CompoundTag network = tag.getCompound("Network");
                stress = network.getFloat("Stress");
                capacity = network.getFloat("Capacity");
            }
        }
        return new MachineInfo(energy[0], energy[1], tanks, rpm, stress, capacity);
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(energy);
        buf.writeVarInt(energyMax);
        buf.writeVarInt(tanks.size());
        for (Tank tank : tanks) {
            buf.writeFluidStack(tank.fluid());
            buf.writeVarInt(tank.capacity());
        }
        buf.writeFloat(rpm);
        buf.writeFloat(stress);
        buf.writeFloat(capacity);
    }

    static MachineInfo decode(FriendlyByteBuf buf) {
        int energy = buf.readVarInt();
        int energyMax = buf.readVarInt();
        int count = buf.readVarInt();
        List<Tank> tanks = new ArrayList<>();
        for (int i = 0; i < count; i++) tanks.add(new Tank(buf.readFluidStack(), buf.readVarInt()));
        return new MachineInfo(energy, energyMax, tanks, buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}
