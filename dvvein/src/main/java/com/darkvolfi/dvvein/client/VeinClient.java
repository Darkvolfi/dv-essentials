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
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
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
import org.lwjgl.glfw.GLFW;

import java.util.List;

@Mod.EventBusSubscriber(modid = DVVein.MOD_ID, value = Dist.CLIENT)
public final class VeinClient {
    public static final KeyMapping VEIN_KEY = new KeyMapping("key.dvvein.vein",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, DVLib.KEY_CATEGORY);

    private static boolean held;
    private static VeinShape shape = VeinShape.SHAPELESS;
    private static boolean sentHeld;
    private static VeinShape sentShape = VeinShape.SHAPELESS;
    private static List<BlockPos> preview = List.of();
    private static BlockPos previewStart;
    private static BlockState previewState;
    private static VeinShape previewShape;

    private VeinClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean serverHasMod = mc.getConnection() != null
                && VeinNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection());
        held = serverHasMod && VEIN_KEY.isDown() && mc.screen == null;

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
        for (BlockPos pos : preview) {
            LevelRenderer.renderLineBox(pose, lines, pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, 1f, 0.82f, 0.25f, 0.9f);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
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
