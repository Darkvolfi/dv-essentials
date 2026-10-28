package com.darkvolfi.dvlens.network;

import com.darkvolfi.dvlens.LensConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client → server: "tell me about the block I'm looking at". */
public record BlockInfoRequest(BlockPos pos) {
    /** Distinct stacks sent at most; three rows in the info box. */
    private static final int MAX_STACKS = 27;
    /** Vanilla brewing time, counted down from this while brewing. */
    private static final int BREW_TIME = 400;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static BlockInfoRequest decode(FriendlyByteBuf buf) {
        return new BlockInfoRequest(buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null || !player.level().isLoaded(pos)) return;
        if (pos.distToCenterSqr(player.position()) > EntityInfoRequest.MAX_DISTANCE * EntityInfoRequest.MAX_DISTANCE) return;
        BlockEntity be = player.level().getBlockEntity(pos);
        if (be == null) return;

        boolean mayPeek = LensConfig.SHARE_CONTENTS.get()
                && (!ModList.get().isLoaded("dvteam") || TeamContents.mayPeek(player, pos));
        List<ItemStack> items = mayPeek ? contents(be) : List.of();
        int bees = be instanceof BeehiveBlockEntity hive ? hive.getOccupantCount() : -1;
        String command = be instanceof CommandBlockEntity cmd && player.canUseGameMasterBlocks()
                ? cmd.getCommandBlock().getCommand() : "";

        // Furnace and brewing progress live in private fields; their saved data has them under stable names.
        int progress = 0, progressMax = 0, fuel = -1, fuelMax = 0;
        if (be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity) {
            CompoundTag tag = be.saveWithoutMetadata();
            if (be instanceof AbstractFurnaceBlockEntity) {
                progress = tag.getInt("CookTime");
                progressMax = tag.getInt("CookTimeTotal");
                fuel = tag.getInt("BurnTime");
            } else {
                int brew = tag.getInt("BrewTime");
                progress = brew > 0 ? BREW_TIME - brew : 0;
                progressMax = BREW_TIME;
                fuel = tag.getByte("Fuel");
                fuelMax = 20;
            }
        }
        LensNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new BlockInfoReply(pos, items, bees, command, progress, progressMax, fuel, fuelMax, MachineInfo.read(be)));
    }

    /** Non-empty stacks of any item-holding block (vanilla or modded), same items merged. */
    private static List<ItemStack> contents(BlockEntity be) {
        List<ItemStack> merged = new ArrayList<>();
        be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                ItemStack same = merged.stream().filter(s -> ItemStack.isSameItemSameTags(s, stack)).findFirst().orElse(null);
                if (same != null) {
                    same.grow(stack.getCount());
                } else if (merged.size() < MAX_STACKS) {
                    merged.add(stack.copy());
                }
            }
        });
        return merged;
    }
}
