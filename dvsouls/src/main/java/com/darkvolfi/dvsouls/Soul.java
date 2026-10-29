package com.darkvolfi.dvsouls;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * A creature's soul as kept on an item: its type, everything it saves (name, health, gear, owner…), and its power,
 * which is its hearts (max health / 2).
 */
public final class Soul {
    public static final String TAG = "Soul";
    private static final String TYPE = "Type";
    private static final String DATA = "Data";
    private static final String HEARTS = "Hearts";
    private static final String NAME = "Name";

    private Soul() {}

    /** Reads the creature into a soul tag. The creature itself is left as it is. */
    public static CompoundTag of(LivingEntity entity) {
        CompoundTag data = entity.saveWithoutId(new CompoundTag());
        // A new body gets a new identity and its own place.
        for (String key : new String[] {"UUID", "Pos", "Motion", "FallDistance", "Fire", "Air", "OnGround", "PortalCooldown",
                "HurtTime", "HurtByTimestamp", "DeathTime", "Leash"}) {
            data.remove(key);
        }
        CompoundTag soul = new CompoundTag();
        soul.putString(TYPE, String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
        soul.put(DATA, data);
        soul.putFloat(HEARTS, entity.getMaxHealth() / 2);
        soul.putString(NAME, Component.Serializer.toJson(entity.getName()));
        return soul;
    }

    /** The kind's own max health, as it spawns; 0 for what has none. */
    @SuppressWarnings("unchecked")
    public static float baseHealth(EntityType<?> type) {
        return DefaultAttributes.hasSupplier(type)
                ? (float) DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type).getBaseValue(Attributes.MAX_HEALTH) : 0;
    }

    /** A newborn soul of the kind: its creature as it would spawn, at full health. For recipe viewers. */
    public static CompoundTag ofType(EntityType<?> type) {
        float health = baseHealth(type);
        CompoundTag soul = new CompoundTag();
        soul.putString(TYPE, String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type)));
        soul.put(DATA, new CompoundTag());
        soul.putFloat(HEARTS, health / 2);
        soul.putString(NAME, Component.Serializer.toJson(type.getDescription()));
        return soul;
    }

    public static CompoundTag get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG) ? tag.getCompound(TAG) : null;
    }

    public static ItemStack put(ItemStack stack, CompoundTag soul) {
        stack.getOrCreateTag().put(TAG, soul);
        return stack;
    }

    public static float hearts(CompoundTag soul) {
        return soul == null ? 0 : soul.getFloat(HEARTS);
    }

    public static Optional<EntityType<?>> type(CompoundTag soul) {
        return soul == null ? Optional.empty() : EntityType.byString(soul.getString(TYPE));
    }

    public static Component name(CompoundTag soul) {
        Component name = soul == null ? null : Component.Serializer.fromJson(soul.getString(NAME));
        return name != null ? name : Component.literal("?");
    }

    /** Gives the soul a body again at {@code pos}, facing {@code yaw}; null if its creature no longer exists. */
    public static Entity spawn(CompoundTag soul, ServerLevel level, Vec3 pos, float yaw) {
        Entity entity = type(soul).map(type -> type.create(level)).orElse(null);
        if (entity == null) return null;
        entity.load(soul.getCompound(DATA));
        entity.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        entity.setYHeadRot(yaw);
        level.addFreshEntity(entity);
        return entity;
    }

    /** The same soul with its creature's max health raised to {@code maxHealth}, and at full health. */
    public static CompoundTag withMaxHealth(CompoundTag soul, float maxHealth) {
        CompoundTag copy = soul.copy();
        CompoundTag data = copy.getCompound(DATA);
        ListTag attributes = data.getList("Attributes", Tag.TAG_COMPOUND);
        String id = String.valueOf(ForgeRegistries.ATTRIBUTES.getKey(Attributes.MAX_HEALTH));
        CompoundTag entry = null;
        for (int i = 0; i < attributes.size(); i++) {
            if (id.equals(attributes.getCompound(i).getString("Name"))) entry = attributes.getCompound(i);
        }
        if (entry == null) {
            entry = new CompoundTag();
            entry.putString("Name", id);
            attributes.add(entry);
        }
        entry.putDouble("Base", maxHealth);
        data.put("Attributes", attributes);
        data.putFloat("Health", maxHealth);
        copy.putFloat(HEARTS, maxHealth / 2);
        return copy;
    }

    /** The same soul with its creature at full health, for one brought back from death. */
    public static CompoundTag healed(CompoundTag soul) {
        CompoundTag copy = soul.copy();
        copy.getCompound(DATA).putFloat("Health", hearts(soul) * 2);
        copy.getCompound(DATA).remove("ActiveEffects");
        return copy;
    }
}
