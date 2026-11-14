package com.darkvolfi.dvslots.mixin;

import com.darkvolfi.dvslots.slot.SlotData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps an elytra flight going (and wears it down) when the elytra is in the back slot.
 * All three redirects use require = 0: if another mod already redirected the same call, the game still
 * starts (Mixin logs a warning) and only back-slot flying is lost, instead of a crash at launch.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Redirect(method = "updateFallFlying", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dvslots$chestOrBack(LivingEntity self, EquipmentSlot slot) {
        return SlotData.chestOrBack(self, slot);
    }
}
