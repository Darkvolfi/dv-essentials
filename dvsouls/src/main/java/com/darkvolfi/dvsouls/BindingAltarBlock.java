package com.darkvolfi.dvsouls;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Binds a creature to a player: lay its soul jar on the altar, then offer a soul with at least half as many hearts
 * (the share is a server setting). The offering is spent and the creature stands up on the altar as the player's pet;
 * its emptied jar goes back to the player. A creature that can be tamed the usual way (a wolf, a horse) is tamed instead.
 */
public class BindingAltarBlock extends SoulAltarBlock {
    @Nullable
    @Override
    protected Component refuse(ItemStack held, Player player) {
        return SoulFuser.isSoul(held) ? null : Component.translatable("dvsouls.binding.empty");
    }

    @Override
    protected float needed(ItemStack laid) {
        return Soul.hearts(Soul.get(laid)) * SoulsConfig.BIND_COST.get().floatValue();
    }

    @Override
    protected String offerKey() {
        return "dvsouls.binding.offer";
    }

    @Override
    protected boolean complete(ServerLevel level, Vec3 top, Player player, ItemStack laid) {
        Entity creature = Soul.spawn(Soul.get(laid), level, top, player.getYRot() + 180);
        if (creature == null) return false;
        if (creature instanceof TamableAnimal tamable) tamable.tame(player);
        else if (creature instanceof AbstractHorse horse) horse.tameWithName(player);
        else if (creature instanceof Mob mob) Pets.bind(mob, player);
        player.displayClientMessage(Component.translatable("dvsouls.binding.bound", creature.getName()), true);
        ItemStack jar = new ItemStack(DVSouls.SOUL_JAR.get());
        if (!player.getInventory().add(jar)) player.drop(jar, false);
        return true;
    }
}
