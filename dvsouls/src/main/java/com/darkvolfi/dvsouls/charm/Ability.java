package com.darkvolfi.dvsouls.charm;

import com.darkvolfi.dvsouls.DVSouls;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * What a creature's essence gives whoever carries it on a soul charm. Some are a status effect, kept up while the charm
 * is carried (its level is how many essences of that creature are carried, up to {@link #MAX_LEVEL}); the rest are done
 * by {@link Charms}.
 */
public enum Ability {
    SQUID(() -> EntityType.SQUID, () -> MobEffects.WATER_BREATHING),
    GLOW_SQUID(() -> EntityType.GLOW_SQUID, () -> MobEffects.NIGHT_VISION),
    DOLPHIN(() -> EntityType.DOLPHIN, () -> MobEffects.DOLPHINS_GRACE),
    /** Slow falling, except while sneaking. */
    CHICKEN(() -> EntityType.CHICKEN, null),
    RABBIT(() -> EntityType.RABBIT, () -> MobEffects.JUMP),
    HORSE(() -> EntityType.HORSE, () -> MobEffects.MOVEMENT_SPEED),
    SPIDER(() -> EntityType.SPIDER, DVSouls.CLIMBING::get),
    WOLF(() -> EntityType.WOLF, () -> MobEffects.DAMAGE_BOOST),
    TURTLE(() -> EntityType.TURTLE, () -> MobEffects.DAMAGE_RESISTANCE),
    /** Less knockback. */
    IRON_GOLEM(() -> EntityType.IRON_GOLEM, null),
    /** Creepers keep away. */
    CAT(() -> EntityType.CAT, null),
    /** Poisons whoever hits you. */
    PUFFERFISH(() -> EntityType.PUFFERFISH, null),
    MAGMA_CUBE(() -> EntityType.MAGMA_CUBE, () -> MobEffects.FIRE_RESISTANCE),
    /** Sometimes teleports you away when something hurts you. */
    ENDERMAN(() -> EntityType.ENDERMAN, null),
    /** Fire resistance, and walking on lava. */
    LAVA_HORSE(DVSouls.LAVA_HORSE::get, () -> MobEffects.FIRE_RESISTANCE),
    /** Slow falling (except while sneaking) and no fall damage. */
    FEATHERED_PIG(DVSouls.FEATHERED_PIG::get, null),
    /** Hunger goes down more slowly. */
    WOOLLY_COW(DVSouls.WOOLLY_COW::get, null),
    /** Pulls items lying near. */
    WOLF_FOX(DVSouls.WOLF_FOX::get, null);

    public static final int MAX_LEVEL = 3;

    private final Supplier<? extends EntityType<?>> creature;
    private final @Nullable Supplier<MobEffect> effect;

    Ability(Supplier<? extends EntityType<?>> creature, @Nullable Supplier<MobEffect> effect) {
        this.creature = creature;
        this.effect = effect;
    }

    public EntityType<?> creature() {
        return creature.get();
    }

    /** The status effect this keeps up, or null if {@link Charms} does it. */
    public @Nullable MobEffect effect() {
        return effect == null ? null : effect.get();
    }

    /** The ability of this creature's essence, or null if its soul can't be distilled. */
    public static @Nullable Ability of(EntityType<?> type) {
        for (Ability ability : values()) {
            if (ability.creature() == type) return ability;
        }
        return null;
    }

    /** The creatures whose souls can be distilled, for recipe viewers. */
    public static List<EntityType<?>> creatures() {
        return java.util.Arrays.stream(values()).<EntityType<?>>map(Ability::creature).toList();
    }

    public Component describe() {
        return Component.translatable("dvsouls.ability." + name().toLowerCase(Locale.ROOT));
    }
}
