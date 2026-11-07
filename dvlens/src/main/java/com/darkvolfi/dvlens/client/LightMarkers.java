package com.darkvolfi.dvlens.client;

import com.darkvolfi.dvlens.DVLens;
import com.darkvolfi.dvlens.LensConfig;
import com.darkvolfi.dvlib.DVLib;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Light overlay: a cross on every block top around the player where hostile mobs can spawn.
 * Red means even in daylight (no sky above), yellow only at night. Uses the game's own spawn rules for
 * the dimension: block light limit, the darkness test, a solid top and two free blocks of space.
 */
@Mod.EventBusSubscriber(modid = DVLens.MOD_ID, value = Dist.CLIENT)
public final class LightMarkers {
    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.dvlens.light",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, DVLib.KEY_CATEGORY);

    public enum Spawn { NONE, NIGHT, ALWAYS }

    /** How much darker the sky gets at midnight (Level.getSkyDarken goes 0..11). */
    private static final int NIGHT_SKY_DARKEN = 11;

    private record Marker(BlockPos pos, Spawn spawn) {}

    private static boolean shown;
    private static List<Marker> markers = List.of();
    private static BlockPos scannedAt;

    private LightMarkers() {}

    public static boolean shown() {
        return shown;
    }

    /** Whether hostile mobs can spawn standing in {@code pos} (an air block on top of a solid one). */
    public static Spawn spawnAt(ClientLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.isValidSpawn(level, below, EntityType.ZOMBIE)) return Spawn.NONE;
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, feet, feet.getFluidState(), EntityType.ZOMBIE)
                || !NaturalSpawner.isValidEmptySpawnBlock(level, pos.above(), head, head.getFluidState(), EntityType.ZOMBIE)) {
            return Spawn.NONE;
        }
        // Mushroom fields, the deep dark and the like have no hostile spawns at all.
        if (level.getBiome(pos).value().getMobSettings().getMobs(MobCategory.MONSTER).isEmpty()) return Spawn.NONE;

        DimensionType dim = level.dimensionType();
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        if (block > dim.monsterSpawnBlockLightLimit()) return Spawn.NONE;
        int sky = dim.hasSkyLight() ? level.getBrightness(LightLayer.SKY, pos) : 0;
        int darkest = dim.monsterSpawnLightTest().getMaxValue();
        if (Math.max(block, sky) <= darkest) return Spawn.ALWAYS;
        if (Math.max(block, sky - NIGHT_SKY_DARKEN) <= darkest) return Spawn.NIGHT;
        return Spawn.NONE;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (TOGGLE_KEY.consumeClick()) {
            shown = !shown;
            scannedAt = null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!shown || mc.level == null || mc.player == null) {
            markers = List.of();
            return;
        }
        // Rescan when the player moves to another block, and once a second for torches placed or broken.
        BlockPos here = mc.player.blockPosition();
        if (here.equals(scannedAt) && mc.level.getGameTime() % 20 != 0) return;
        scannedAt = here;
        markers = scan(mc.level, here);
    }

    private static List<Marker> scan(ClientLevel level, BlockPos centre) {
        int r = LensConfig.LIGHT_RADIUS.get();
        int ry = Math.max(4, r / 2);
        boolean night = LensConfig.LIGHT_NIGHT_MARKS.get();
        List<Marker> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -ry; y <= ry; y++) {
                    pos.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
                    if (!level.isLoaded(pos)) continue;
                    Spawn spawn = spawnAt(level, pos);
                    if (spawn == Spawn.ALWAYS || (night && spawn == Spawn.NIGHT)) found.add(new Marker(pos.immutable(), spawn));
                }
            }
        }
        return found;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || markers.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Marker m : markers) {
            // Relative to the camera, so far-off coordinates keep their float precision.
            float x = (float) (m.pos().getX() - cam.x);
            float y = (float) (m.pos().getY() - cam.y) + 0.01f;
            float z = (float) (m.pos().getZ() - cam.z);
            float red = 1f;
            float green = m.spawn() == Spawn.ALWAYS ? 0.2f : 0.85f;
            float in = 0.2f;
            cross(lines, matrix, normal, x + in, y, z + in, x + 1 - in, z + 1 - in, red, green);
            cross(lines, matrix, normal, x + 1 - in, y, z + in, x + in, z + 1 - in, red, green);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void cross(VertexConsumer lines, Matrix4f matrix, Matrix3f normal,
                              float x1, float y, float z1, float x2, float z2, float red, float green) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        lines.vertex(matrix, x1, y, z1).color(red, green, 0.2f, 0.9f).normal(normal, dx / len, 0, dz / len).endVertex();
        lines.vertex(matrix, x2, y, z2).color(red, green, 0.2f, 0.9f).normal(normal, dx / len, 0, dz / len).endVertex();
    }

    @Mod.EventBusSubscriber(modid = DVLens.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        // Not "onRegisterKeys": Forge names a handler by package, class simple name, method and event, so it would
        // clash with LensOverlay.ModEvents and register this key twice instead of both keys.
        @SubscribeEvent
        public static void onRegisterLightKey(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_KEY);
        }
    }
}
