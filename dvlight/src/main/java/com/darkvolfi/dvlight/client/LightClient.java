package com.darkvolfi.dvlight.client;

import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvlight.DVLight;
import com.darkvolfi.dvlight.LightConfig;
import com.darkvolfi.dvlight.LightConfig.Mode;
import com.darkvolfi.dvlight.LightConfig.Shape;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Finds the moving lights every tick, fades them in and out, and hands them to {@link DynamicLights}. */
@Mod.EventBusSubscriber(modid = DVLight.MOD_ID, value = Dist.CLIENT)
public final class LightClient {
    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.dvlight.toggle",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, DVLib.KEY_CATEGORY);
    public static final KeyMapping SHAPE_KEY = new KeyMapping("key.dvlight.shape",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, DVLib.KEY_CATEGORY);

    /** Fancy: light centre to 1/16 block and cone direction in fine steps; Fast: block centre, coarse cone. */
    private static final float FANCY_POS_STEP = 1 / 16f, FANCY_CONE_STEP = 0.015f, FAST_CONE_STEP = 0.15f;

    /** A light and its brightness; it keeps its last place while it fades out after its entity is gone. */
    private static final class Fade {
        float level;
        boolean cone;
        DynamicLights.Light place;
    }

    private static final Map<Integer, Fade> fades = new HashMap<>();
    private static boolean enabled = true;
    private static ClientLevel lastLevel;
    private static int ticks;

    private LightClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            // Another world: its chunk meshes are new, nothing to clean up
            lastLevel = mc.level;
            fades.clear();
            DynamicLights.forget();
        }
        if (mc.level == null) return;
        handleKeys(mc);

        Mode mode = LightConfig.MODE.get();
        if (!enabled || mode == Mode.OFF) {
            fades.clear();
            DynamicLights.publish(Map.of(), LightConfig.CONE_ANGLE.get(), LightConfig.CONE_RANGE.get(), false);
            return;
        }
        ticks++;
        boolean fancy = mode == Mode.FANCY;
        // Fast: half as many updates, lights move a whole block at a time, no fading
        if (!fancy && ticks % 2 != 0) return;

        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        double range = LightConfig.RANGE.get();
        boolean cones = LightConfig.SHAPE.get() == Shape.CONE;
        boolean smooth = fancy && LightConfig.SMOOTH.get();

        Map<Integer, Fade> seen = new HashMap<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.isSpectator() || entity.distanceToSqr(eye) > range * range) continue;
            LightSources.Glow glow = LightSources.of(entity);
            Fade fade = fades.get(entity.getId());
            if (glow.level() <= 0 && fade == null) continue;
            if (fade == null) fade = new Fade();

            fade.level = smooth ? approach(fade.level, glow.level()) : glow.level();
            fade.cone = cones && glow.held() && entity instanceof Player;
            fade.place = place(entity, Math.round(fade.level), fade.cone, 1f, fancy);
            if (fade.level > 0) seen.put(entity.getId(), fade);
        }
        // Lights whose entity left (or went out of range) fade out where they were
        for (Iterator<Map.Entry<Integer, Fade>> it = fades.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Fade> e = it.next();
            if (seen.containsKey(e.getKey())) continue;
            Fade fade = e.getValue();
            fade.level = smooth ? approach(fade.level, 0) : 0;
            if (fade.level > 0 && fade.place != null) {
                fade.place = withLevel(fade.place, Math.round(fade.level));
                seen.put(e.getKey(), fade);
            }
        }
        fades.clear();
        fades.putAll(seen);
        Map<Integer, DynamicLights.Light> lights = new HashMap<>();
        fades.forEach((id, fade) -> {
            if (fade.place != null && fade.place.level() > 0) lights.put(id, fade.place);
        });
        DynamicLights.publish(lights, LightConfig.CONE_ANGLE.get(), LightConfig.CONE_RANGE.get(), fancy);
    }

    /** Where the light is, rounded so chunks only rebuild when it moves noticeably. */
    private static DynamicLights.Light place(Entity entity, int level, boolean cone, float partialTick, boolean fancy) {
        float posStep = fancy ? FANCY_POS_STEP : 0;
        if (cone) {
            float step = fancy ? FANCY_CONE_STEP : FAST_CONE_STEP;
            Vec3 look = entity.getViewVector(partialTick);
            float dx = snap(look.x, step), dy = snap(look.y, step), dz = snap(look.z, step);
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0) {
                Vec3 eye = entity.getEyePosition(partialTick);
                return new DynamicLights.Light(at(eye.x, posStep), at(eye.y, posStep), at(eye.z, posStep),
                        level, true, dx / len, dy / len, dz / len);
            }
        }
        Vec3 pos = entity.getPosition(partialTick);
        double y = pos.y + entity.getBbHeight() * 0.6;
        return new DynamicLights.Light(at(pos.x, posStep), at(y, posStep), at(pos.z, posStep), level, false, 0, 0, 0);
    }

    private static DynamicLights.Light withLevel(DynamicLights.Light p, int level) {
        return new DynamicLights.Light(p.x(), p.y(), p.z(), level, p.cone(), p.dx(), p.dy(), p.dz());
    }

    /** {@code step} 0: the block centre. */
    private static float at(double coord, float step) {
        return step <= 0 ? Mth.floor(coord) + 0.5f : snap(coord, step);
    }

    private static float snap(double value, float step) {
        return Math.round(value / step) * step;
    }

    /** Rises fast and falls slower, about a third of a second either way. */
    private static float approach(float from, float to) {
        if (to > from) return Math.min(to, from + 3f);
        return Math.max(to, from - 1.5f);
    }

    private static void handleKeys(Minecraft mc) {
        while (TOGGLE_KEY.consumeClick()) {
            enabled = !enabled;
            message(mc, enabled ? "dvlight.message.on" : "dvlight.message.off");
        }
        while (SHAPE_KEY.consumeClick()) {
            Shape next = LightConfig.SHAPE.get() == Shape.ROUND ? Shape.CONE : Shape.ROUND;
            LightConfig.SHAPE.set(next);
            LightConfig.SPEC.save();
            message(mc, next == Shape.CONE ? "dvlight.message.cone" : "dvlight.message.round");
        }
    }

    private static void message(Minecraft mc, String key) {
        if (mc.player != null) mc.player.displayClientMessage(Component.translatable(key), true);
    }

    @Mod.EventBusSubscriber(modid = DVLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onRegisterLightKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_KEY);
            event.register(SHAPE_KEY);
        }
    }
}
