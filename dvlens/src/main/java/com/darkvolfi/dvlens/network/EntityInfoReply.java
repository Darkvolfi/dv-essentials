package com.darkvolfi.dvlens.network;

import com.darkvolfi.dvlens.client.ServerInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: {@code age} is AgeableMob's age (negative = ticks until grown up, positive = breeding
 * cooldown), {@code owner} the pet owner's name or empty, {@code effects} the mob's active potion effects.
 */
public record EntityInfoReply(int entityId, int age, String owner, List<MobEffectInstance> effects) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(age);
        buf.writeUtf(owner, 64);
        buf.writeVarInt(effects.size());
        for (MobEffectInstance effect : effects) {
            buf.writeResourceLocation(ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect()));
            buf.writeVarInt(effect.getAmplifier());
            buf.writeVarInt(effect.getDuration());
        }
    }

    public static EntityInfoReply decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int age = buf.readVarInt();
        String owner = buf.readUtf(64);
        int count = buf.readVarInt();
        List<MobEffectInstance> effects = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            int amplifier = buf.readVarInt();
            int duration = buf.readVarInt();
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (effect != null) effects.add(new MobEffectInstance(effect, duration, amplifier));
        }
        return new EntityInfoReply(entityId, age, owner, effects);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ServerInfo.receive(this));
    }
}
