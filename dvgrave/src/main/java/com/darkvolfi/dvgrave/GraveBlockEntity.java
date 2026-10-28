package com.darkvolfi.dvgrave;

import com.darkvolfi.dvgrave.compat.TeamCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a grave holds: the stacks with where each one was, the experience, whose it is and when it was made.
 * It is not a {@link Container} itself, so hoppers can't drain it; menus get {@link #container()}.
 */
public class GraveBlockEntity extends BlockEntity implements MenuProvider {
    private UUID owner;
    private String ownerName = "";
    private final List<GraveItem> items = new ArrayList<>();
    private int xp;
    /** Game time when the grave was made. */
    private long created;

    public GraveBlockEntity(BlockPos pos, BlockState state) {
        super(DVGrave.GRAVE_ENTITY.get(), pos, state);
    }

    public void fill(ServerPlayer player, List<GraveItem> stacks, int experience) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        items.addAll(stacks);
        xp = experience;
        created = level.getGameTime();
        setChanged();
    }

    /** Items that turned up after the grave was made (mods dropping them late). */
    public void add(GraveItem item) {
        items.add(item);
        setChanged();
    }

    public String ownerName() {
        return ownerName;
    }

    public int xp() {
        return xp;
    }

    /** A fresh inventory-shaped view of the contents for a menu. */
    public Container container() {
        return new GraveLayout();
    }

    public boolean isOwner(Player player) {
        return player.getUUID().equals(owner);
    }

    /** Minutes until anyone may open it; -1 when it stays private. */
    public int minutesLeft() {
        int minutes = GraveConfig.PRIVATE_MINUTES.get();
        if (minutes < 0) return -1;
        long left = minutes * 1200L - (level.getGameTime() - created);
        return (int) Math.max(0, (left + 1199) / 1200);
    }

    public boolean mayOpen(ServerPlayer player) {
        if (isOwner(player) || minutesLeft() == 0) return true;
        return GraveConfig.CLAN_ACCESS.get() && ModList.get().isLoaded("dvteam")
                && TeamCompat.sameClan(player.server, owner, player.getUUID());
    }

    /** "Take all": everything back where it was, the experience too; then the grave goes away. */
    public void takeAll(ServerPlayer player) {
        List<GraveItem> left = new ArrayList<>();
        for (GraveItem item : items) if (!item.stack().isEmpty()) left.add(item);
        items.clear();
        GraveRestore.restore(player, left);
        giveXp(player);
        removeIfEmpty();
    }

    private void giveXp(ServerPlayer player) {
        if (xp <= 0) return;
        player.giveExperiencePoints(xp);
        xp = 0;
        setChanged();
    }

    /** After a menu closes: an emptied grave hands over its experience and disappears. */
    public void onMenuClosed(ServerPlayer player) {
        if (items.stream().allMatch(i -> i.stack().isEmpty())) {
            giveXp(player);
            removeIfEmpty();
        }
    }

    private void removeIfEmpty() {
        if (xp > 0 || items.stream().anyMatch(i -> !i.stack().isEmpty())) return;
        items.clear();
        level.removeBlock(worldPosition, false);
    }

    /** Drops everything on the ground (the block is being broken in creative or replaced). */
    public void spill() {
        if (!(level instanceof ServerLevel server)) return;
        for (GraveItem item : items) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), item.stack());
        }
        items.clear();
        if (xp > 0) ExperienceOrb.award(server, Vec3.atCenterOf(worldPosition), xp);
        xp = 0;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("dvgrave.title", ownerName);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new GraveMenu(id, inventory, container(), worldPosition, xp, ownerName);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        ListTag list = new ListTag();
        for (GraveItem item : items) if (!item.stack().isEmpty()) list.add(item.save());
        tag.put("Items", list);
        tag.putInt("Xp", xp);
        tag.putLong("Created", created);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        items.clear();
        for (Tag t : tag.getList("Items", Tag.TAG_COMPOUND)) items.add(GraveItem.load((CompoundTag) t));
        xp = tag.getInt("Xp");
        created = tag.getLong("Created");
    }

    /** Clients only need the name for the stone. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("OwnerName", ownerName);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        ownerName = tag.getString("OwnerName");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * The grave's stacks laid out like a player inventory: display slots 0..40 are the inventory slots the
     * stacks were in (hotbar, main, armour, offhand), and everything else (other mods' slots, stacks whose slot
     * was taken) follows from {@link GraveMenu#VANILLA_SLOTS} on. Built fresh for each menu.
     */
    private final class GraveLayout implements Container {
        /** Display slot → index in {@link #items}, or -1. */
        private final int[] map;

        GraveLayout() {
            List<Integer> extras = new ArrayList<>();
            int[] vanilla = new int[GraveMenu.VANILLA_SLOTS];
            java.util.Arrays.fill(vanilla, -1);
            for (int i = 0; i < items.size(); i++) {
                GraveItem item = items.get(i);
                if (item.stack().isEmpty()) continue;
                boolean inInventory = item.slot().equals(GraveItem.INVENTORY) && item.index() >= 0 && item.index() < vanilla.length;
                if (inInventory && vanilla[item.index()] < 0) vanilla[item.index()] = i;
                else extras.add(i);
            }
            map = new int[vanilla.length + extras.size()];
            System.arraycopy(vanilla, 0, map, 0, vanilla.length);
            for (int k = 0; k < extras.size(); k++) map[vanilla.length + k] = extras.get(k);
        }

        private int item(int slot) {
            int i = slot >= 0 && slot < map.length ? map[slot] : -1;
            return i < items.size() ? i : -1;
        }

        @Override
        public int getContainerSize() {
            return map.length;
        }

        @Override
        public boolean isEmpty() {
            return items.stream().allMatch(i -> i.stack().isEmpty());
        }

        @Override
        public ItemStack getItem(int slot) {
            int i = item(slot);
            return i < 0 ? ItemStack.EMPTY : items.get(i).stack();
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            int i = item(slot);
            if (i < 0) return ItemStack.EMPTY;
            ItemStack taken = items.get(i).stack().split(amount);
            setChanged();
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            int i = item(slot);
            if (i < 0) return ItemStack.EMPTY;
            ItemStack taken = items.get(i).stack();
            items.set(i, items.get(i).withStack(ItemStack.EMPTY));
            return taken;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            int i = item(slot);
            if (i >= 0) items.set(i, items.get(i).withStack(stack));
            setChanged();
        }

        @Override
        public void setChanged() {
            GraveBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return !isRemoved() && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64;
        }

        @Override
        public void clearContent() {
            items.clear();
        }
    }
}
