package com.darkvolfi.dvslots.network;

import com.darkvolfi.dvslots.slot.SlotData;
import com.darkvolfi.dvslots.slot.SlotType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the DV slot contents of one player (yourself or someone you can see). */
public record SyncSlotsPacket(int entityId, List<ItemStack> stacks) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(stacks.size());
        for (ItemStack stack : stacks) buf.writeItem(stack);
    }

    public static SyncSlotsPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int size = buf.readVarInt();
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) stacks.add(buf.readItem());
        return new SyncSlotsPacket(id, stacks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(this));
    }

    private static final class ClientHandler {
        static void apply(SyncSlotsPacket packet) {
            if (Minecraft.getInstance().level == null) return;
            if (!(Minecraft.getInstance().level.getEntity(packet.entityId) instanceof Player player)) return;
            SlotData data = SlotData.get(player);
            if (data == null) return;
            SlotType[] types = SlotType.values();
            for (int i = 0; i < Math.min(types.length, packet.stacks.size()); i++) {
                data.set(types[i], packet.stacks.get(i));
            }
            data.consumeDirty(); // the client never sends these back
        }
    }
}
