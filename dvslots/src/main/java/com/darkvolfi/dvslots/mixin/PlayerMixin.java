package com.darkvolfi.dvslots.mixin;

import com.darkvolfi.dvslots.slot.SlotData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Server side: allows starting an elytra flight with the elytra in the back slot. */
@Mixin(Player.class)
abstract class PlayerMixin {
    @Redirect(method = "tryToStartFallFlying", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dvslots$chestOrBack(Player self, EquipmentSlot slot) {
        return SlotData.chestOrBack(self, slot);
    }
}
