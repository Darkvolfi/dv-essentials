package com.darkvolfi.dvbackpack;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A laid-out sleeping bag's colour, chosen on its backpack; sent to clients, who tint the bag with it. */
public class SleepingBagBlockEntity extends BlockEntity {
    private int color = BackpackItem.DEFAULT_BAG_COLOR;

    public SleepingBagBlockEntity(BlockPos pos, BlockState state) {
        super(DVBackpack.SLEEPING_BAG_ENTITY.get(), pos, state);
    }

    public int color() {
        return color;
    }

    public void setColor(int color) {
        if (this.color == color) return;
        this.color = color;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Color", color);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Color")) color = tag.getInt("Color");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        super.onDataPacket(connection, packet);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
    }
}
