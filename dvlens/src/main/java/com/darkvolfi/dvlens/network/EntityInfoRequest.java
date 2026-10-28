package com.darkvolfi.dvlens.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Client → server: "tell me about the entity I'm looking at". */
public record EntityInfoRequest(int entityId) {
    /** Farther than this the player can't be looking at it with the lens anyway. */
    static final double MAX_DISTANCE = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static EntityInfoRequest decode(FriendlyByteBuf buf) {
        return new EntityInfoRequest(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null) return;
        Entity entity = player.level().getEntity(entityId);
        if (entity == null || entity.distanceToSqr(player) > MAX_DISTANCE * MAX_DISTANCE) return;

        int age = entity instanceof AgeableMob mob ? mob.getAge() : 0;
        String owner = "";
        if (entity instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            UUID id = pet.getOwnerUUID();
            owner = player.server.getProfileCache() == null ? ""
                    : player.server.getProfileCache().get(id).map(p -> p.getName()).orElse("");
        }
        List<MobEffectInstance> effects = entity instanceof LivingEntity living
                ? new ArrayList<>(living.getActiveEffects()) : List.of();
        LensNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EntityInfoReply(entityId, age, owner, effects));
    }
}
