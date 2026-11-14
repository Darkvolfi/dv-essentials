package com.darkvolfi.dvslots.mixin;

import com.darkvolfi.dvslots.slot.SlotData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Client side: pressing jump in mid-air asks the server to start flying with a back-slot elytra too. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Redirect(method = "aiStep", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dvslots$chestOrBack(LocalPlayer self, EquipmentSlot slot) {
        return SlotData.chestOrBack(self, slot);
    }
}
