package com.darkvolfi.dvlens.network;

import com.darkvolfi.dvlens.client.ServerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: a block entity's contents (merged stacks), bees in a hive (-1 = not a hive), a command
 * block's command (empty unless the player may see it), and furnace/brewing progress. {@code fuel} is -1 when
 * there's no fuel to show; {@code fuelMax} 0 means {@code fuel} is a burn time in ticks, otherwise a count.
 * {@code machine} holds energy, fluid tanks and Create rotation.
 */
public record BlockInfoReply(BlockPos pos, List<ItemStack> items, int bees, String command,
                             int progress, int progressMax, int fuel, int fuelMax, MachineInfo machine) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(items.size());
        for (ItemStack stack : items) {
            // Merged counts can pass 127, which writeItem's byte can't hold.
            buf.writeItem(stack.copyWithCount(1));
            buf.writeVarInt(stack.getCount());
        }
        buf.writeVarInt(bees);
        buf.writeUtf(command);
        buf.writeVarInt(progress);
        buf.writeVarInt(progressMax);
        buf.writeVarInt(fuel);
        buf.writeVarInt(fuelMax);
        machine.encode(buf);
    }

    public static BlockInfoReply decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = buf.readVarInt();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemStack stack = buf.readItem();
            stack.setCount(buf.readVarInt());
            items.add(stack);
        }
        return new BlockInfoReply(pos, items, buf.readVarInt(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), MachineInfo.decode(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ServerInfo.receive(this));
    }
}
