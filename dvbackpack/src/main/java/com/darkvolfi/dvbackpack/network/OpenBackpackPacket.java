package com.darkvolfi.dvbackpack.network;

import com.darkvolfi.dvbackpack.BackpackMenu;
import com.darkvolfi.dvbackpack.WornBackpack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: open the backpack I'm wearing. */
public record OpenBackpackPacket() {

    public void encode(FriendlyByteBuf buf) {}

    public static OpenBackpackPacket decode(FriendlyByteBuf buf) {
        return new OpenBackpackPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ItemStack worn = WornBackpack.find(player);
        if (worn.isEmpty()) player.displayClientMessage(Component.translatable("dvbackpack.not_worn"), true);
        else BackpackMenu.open(player, worn, BackpackMenu.Source.worn());
    }
}
