package com.darkvolfi.dvbackpack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Holds the backpack item that was set down; its contents stay in {@link BackpackStorage}. */
public class BackpackBlockEntity extends BlockEntity {
    private ItemStack backpack = ItemStack.EMPTY;

    public BackpackBlockEntity(BlockPos pos, BlockState state) {
        super(DVBackpack.BACKPACK_ENTITY.get(), pos, state);
    }

    public ItemStack backpack() {
        return backpack;
    }

    public void setBackpack(ItemStack backpack) {
        this.backpack = backpack;
        backpackChanged();
    }

    /** After the backpack item changed (its bag colour or tanks): save, and show the change to nearby players. */
    public void backpackChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            recolourSleepingBag();
        }
    }

    private void recolourSleepingBag() {
        for (BlockPos pos : new BlockPos[]{bagHead(), bagFoot()}) {
            if (level.getBlockEntity(pos) instanceof SleepingBagBlockEntity bag) bag.setColor(BackpackItem.bagColor(backpack));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client got a new backpack item: draw the block again, its colours and tanks may have changed. */
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        super.onDataPacket(connection, packet);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
    }

    /** Empties the block entity so the backpack is handed out only once. */
    public ItemStack takeBackpack() {
        ItemStack taken = backpack;
        backpack = ItemStack.EMPTY;
        setChanged();
        return taken;
    }

    /** Where the sleeping bag goes: its head right in front of the backpack's pocket, its foot one further. */
    private BlockPos bagHead() {
        return worldPosition.relative(getBlockState().getValue(BackpackBlock.FACING));
    }

    private BlockPos bagFoot() {
        return bagHead().relative(getBlockState().getValue(BackpackBlock.FACING));
    }

    private boolean bagOut() {
        return level != null && level.getBlockState(bagHead()).is(DVBackpack.SLEEPING_BAG.get());
    }

    /** Lays the sleeping bag out in front of the backpack, or rolls it back up. */
    public void toggleSleepingBag(Player player) {
        if (level == null) return;
        if (bagOut()) {
            removeSleepingBag();
            level.playSound(null, worldPosition, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1f, 1f);
            return;
        }
        BlockPos head = bagHead();
        BlockPos foot = bagFoot();
        for (BlockPos pos : new BlockPos[]{head, foot}) {
            if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                player.displayClientMessage(Component.translatable("dvbackpack.sleeping_bag.no_room"), true);
                return;
            }
        }
        // A bed's facing points from its foot to its head, here back towards the backpack.
        Direction toBackpack = getBlockState().getValue(BackpackBlock.FACING).getOpposite();
        BlockState bag = DVBackpack.SLEEPING_BAG.get().defaultBlockState().setValue(BedBlock.FACING, toBackpack);
        level.setBlock(foot, bag.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(head, bag.setValue(BedBlock.PART, BedPart.HEAD), 3);
        recolourSleepingBag();
        setRolled(false);
        level.playSound(null, worldPosition, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 1f);
    }

    /** Rolls the sleeping bag up, if it is out (when the backpack is picked up or broken too). */
    public void removeSleepingBag() {
        if (level == null || !bagOut()) return;
        BlockPos foot = bagFoot();
        level.removeBlock(bagHead(), false);
        if (level.getBlockState(foot).is(DVBackpack.SLEEPING_BAG.get())) level.removeBlock(foot, false);
        setRolled(true);
    }

    /** Shows the rolled sleeping bag in front of the backpack, or not while it is laid out. */
    private void setRolled(boolean rolled) {
        BlockState state = level.getBlockState(worldPosition);
        if (state.is(DVBackpack.BACKPACK_BLOCK.get()) && state.getValue(BackpackBlock.ROLLED) != rolled) {
            level.setBlock(worldPosition, state.setValue(BackpackBlock.ROLLED, rolled), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Backpack", backpack.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        backpack = ItemStack.of(tag.getCompound("Backpack"));
    }
}
