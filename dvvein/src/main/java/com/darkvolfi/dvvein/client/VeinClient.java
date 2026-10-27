package com.darkvolfi.dvvein.client;

import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvvein.DVVein;
import com.darkvolfi.dvvein.VeinConfig;
import com.darkvolfi.dvvein.VeinMiner;
import com.darkvolfi.dvvein.VeinShape;
import com.darkvolfi.dvvein.network.VeinKeyPacket;
import com.darkvolfi.dvvein.network.VeinNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = DVVein.MOD_ID, value = Dist.CLIENT)
public final class VeinClient {
    public static final KeyMapping VEIN_KEY = new KeyMapping("key.dvvein.vein",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, DVLib.KEY_CATEGORY);

    private static boolean held;
    private static VeinShape shape = VeinShape.SHAPELESS;
    private static boolean sentHeld;
    private static VeinShape sentShape = VeinShape.SHAPELESS;
    private static List<BlockPos> preview = List.of();
    private static Set<BlockPos> previewSet = Set.of();
    private static BlockPos previewStart;
    private static BlockState previewState;
    private static VeinShape previewShape;

    private VeinClient() {}

    /** Without DV Vein on the server nothing happens there, so the client must not act (or swallow clicks). */
    public static boolean serverHasMod() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && VeinNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        held = serverHasMod() && VEIN_KEY.isDown() && mc.screen == null;

        if (held != sentHeld || shape != sentShape) {
            VeinNetwork.CHANNEL.sendToServer(new VeinKeyPacket(held, shape));
            sentHeld = held;
            sentShape = shape;
        }
        updatePreview(mc);
    }

    /** While the vein key is held, the mouse wheel picks the shape instead of the hotbar slot. */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!held) return;
        shape = shape.cycle(event.getScrollDelta() > 0 ? -1 : 1);
        event.setCanceled(true);
    }

    private static void updatePreview(Minecraft mc) {
        if (!held || !VeinConfig.SHOW_PREVIEW.get() || mc.level == null
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            preview = List.of();
            previewStart = null;
            return;
        }
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        // Shapes follow where you look, so recompute them every tick; a vein only when its target
        // changes, and every second in case nearby blocks changed.
        boolean same = hit.getBlockPos().equals(previewStart) && state == previewState && shape == previewShape;
        if (same && shape == VeinShape.SHAPELESS && mc.level.getGameTime() % 20 != 0) return;
        previewStart = hit.getBlockPos();
        previewState = state;
        previewShape = shape;
        preview = VeinMiner.canVein(mc.player, state)
                ? shape.blocks(mc.level, mc.player, previewStart, state, VeinConfig.maxBlocks(), VeinConfig.groupOres())
                : List.of();
        previewSet = new HashSet<>(preview);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || preview.size() < 2) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        Direction.Axis[] axes = Direction.Axis.values();
        for (BlockPos pos : preview) {
            for (Direction.Axis axis : axes) {
                // The two axes across this edge direction.
                Direction.Axis a = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
                Direction.Axis b = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
                for (int u = 0; u <= 1; u++) {
                    for (int v = 0; v <= 1; v++) {
                        if (!isOutline(pos, a, u, b, v)) continue;
                        int[] start = {pos.getX(), pos.getY(), pos.getZ()};
                        start[a.ordinal()] += u;
                        start[b.ordinal()] += v;
                        int[] end = start.clone();
                        end[axis.ordinal()] += 1;
                        line(lines, matrix, normal, start, end, axis);
                    }
                }
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /**
     * Whether a block edge is part of the merged outline. Looks at the cells sharing the edge: the two face
     * neighbours across it and the diagonal one. A flat wall (one face neighbour) and the inside (all filled)
     * hide it; corners, inner corners and blocks touching only diagonally (separate pieces) show it.
     */
    private static boolean isOutline(BlockPos pos, Direction.Axis a, int u, Direction.Axis b, int v) {
        BlockPos sideA = pos.relative(a, u == 1 ? 1 : -1);
        BlockPos sideB = pos.relative(b, v == 1 ? 1 : -1);
        boolean hasA = previewSet.contains(sideA);
        boolean hasB = previewSet.contains(sideB);
        boolean hasDiagonal = previewSet.contains(sideA.relative(b, v == 1 ? 1 : -1));
        return hasA == hasB ? !(hasA && hasDiagonal) : hasDiagonal;
    }

    private static void line(VertexConsumer lines, Matrix4f matrix, Matrix3f normal, int[] from, int[] to, Direction.Axis axis) {
        float nx = axis == Direction.Axis.X ? 1 : 0;
        float ny = axis == Direction.Axis.Y ? 1 : 0;
        float nz = axis == Direction.Axis.Z ? 1 : 0;
        lines.vertex(matrix, from[0], from[1], from[2]).color(1f, 0.82f, 0.25f, 0.9f).normal(normal, nx, ny, nz).endVertex();
        lines.vertex(matrix, to[0], to[1], to[2]).color(1f, 0.82f, 0.25f, 0.9f).normal(normal, nx, ny, nz).endVertex();
    }

    /** Under the crosshair while the key is held: the shape (wheel to change) and the block count. */
    private static void renderCount(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (!held || gui.getMinecraft().options.hideGui) return;
        var font = gui.getMinecraft().font;
        Component name = Component.translatable("dvvein.shape_hint", Component.translatable(shape.translationKey()));
        g.drawString(font, name, (width - font.width(name)) / 2, height / 2 + 10, 0xFFD040);
        if (preview.size() >= 2) {
            Component count = Component.translatable("dvvein.count", preview.size());
            g.drawString(font, count, (width - font.width(count)) / 2, height / 2 + 21, 0xFFFFFF);
        }
    }

    @Mod.EventBusSubscriber(modid = DVVein.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(VEIN_KEY);
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("vein_count", VeinClient::renderCount);
        }
    }
}
