package com.darkvolfi.dvvein.client;

import com.darkvolfi.dvlib.DVLib;
import com.darkvolfi.dvvein.DVVein;
import com.darkvolfi.dvvein.VeinConfig;
import com.darkvolfi.dvvein.VeinMiner;
import com.darkvolfi.dvvein.VeinShape;
import com.darkvolfi.dvvein.network.BlacklistPacket;
import com.darkvolfi.dvvein.network.VeinKeyPacket;
import com.darkvolfi.dvvein.network.VeinNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = DVVein.MOD_ID, value = Dist.CLIENT)
public final class VeinClient {
    public static final KeyMapping VEIN_KEY = new KeyMapping("key.dvvein.vein",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, DVLib.KEY_CATEGORY);
    /** Hold to open the shape wheel. */
    public static final KeyMapping SHAPE_KEY = new KeyMapping("key.dvvein.shape",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, DVLib.KEY_CATEGORY);

    private static boolean held;
    private static VeinShape shape = VeinShape.SHAPELESS;
    private static boolean sentHeld;
    private static VeinShape sentShape = VeinShape.SHAPELESS;
    private static List<BlockPos> preview = List.of();
    private static Set<BlockPos> previewSet = Set.of();
    private static BlockPos previewStart;
    private static BlockState previewState;
    private static VeinShape previewShape;
    private static Object sentBlacklistTo;
    private static List<String> sentBlacklist = List.of();

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
        while (SHAPE_KEY.consumeClick()) {
            if (serverHasMod() && mc.screen == null) mc.setScreen(new VeinShapeWheel());
        }

        if (held != sentHeld || shape != sentShape) {
            VeinNetwork.CHANNEL.sendToServer(new VeinKeyPacket(held, shape));
            sentHeld = held;
            sentShape = shape;
        }
        // On joining a server, and whenever the list changes (middle-click, config file, a loaded DV profile).
        List<? extends String> blacklist = VeinConfig.BLACKLIST.get();
        if (serverHasMod() && (mc.getConnection() != sentBlacklistTo || !blacklist.equals(sentBlacklist))) {
            List<ResourceLocation> ids = new ArrayList<>();
            for (String id : blacklist) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) ids.add(rl);
            }
            VeinNetwork.CHANNEL.sendToServer(new BlacklistPacket(ids));
            sentBlacklistTo = mc.getConnection();
            sentBlacklist = List.copyOf(blacklist);
        }
        updatePreview(mc);
    }

    static VeinShape shape() {
        return shape;
    }

    static void setShape(VeinShape picked) {
        shape = picked;
    }

    /** Client side: whether the vein key is held (and the server has DV Vein). */
    public static boolean isHeld() {
        return held;
    }

    private static boolean isBlacklisted(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && VeinConfig.BLACKLIST.get().contains(id.toString());
    }

    /** Vein key + middle click on a block: add it to or remove it from the blacklist. */
    @SubscribeEvent
    public static void onPickBlock(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!held || !event.isPickBlock() || mc.level == null || mc.player == null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return;

        List<String> list = new ArrayList<>(VeinConfig.BLACKLIST.get());
        boolean added = !list.remove(id.toString());
        if (added) list.add(id.toString());
        VeinConfig.BLACKLIST.set(list);
        VeinConfig.CLIENT_SPEC.save();
        mc.player.displayClientMessage(Component.translatable(added ? "dvvein.blacklist.added" : "dvvein.blacklist.removed",
                state.getBlock().getName()).withStyle(added ? ChatFormatting.RED : ChatFormatting.GREEN), true);
        previewStart = null; // recompute the outline
        event.setCanceled(true);
        event.setSwingHand(false);
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
        preview = VeinMiner.canVein(mc.player, state) && !isBlacklisted(state)
                ? shape.blocks(mc.level, mc.player, previewStart, state, VeinConfig.maxBlocks(), VeinConfig.groupOres())
                : List.of();
        if (!VeinConfig.BLACKLIST.get().isEmpty()) {
            // The server skips these inside shapes too; keep the clicked block so the count still makes sense.
            List<BlockPos> kept = new ArrayList<>();
            for (int i = 0; i < preview.size(); i++) {
                if (i == 0 || !isBlacklisted(mc.level.getBlockState(preview.get(i)))) kept.add(preview.get(i));
            }
            preview = kept;
        }
        previewSet = new HashSet<>(preview);
    }

    /** Pushes outline edges this far off the block faces, like vanilla's block outline, so they don't flicker into them. */
    private static final double INFLATE = 0.002;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || preview.size() < 2) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();

        // Edges relative to the camera: world coordinates in floats lose precision far from 0,0 and the lines shake.
        List<float[]> edges = new ArrayList<>();
        Direction.Axis[] axes = Direction.Axis.values();
        for (BlockPos pos : preview) {
            for (Direction.Axis axis : axes) {
                // The two axes across this edge direction.
                Direction.Axis a = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
                Direction.Axis b = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
                for (int u = 0; u <= 1; u++) {
                    for (int v = 0; v <= 1; v++) {
                        if (!isOutline(pos, a, u, b, v)) continue;
                        double[] start = {pos.getX() + 0.5 - cam.x, pos.getY() + 0.5 - cam.y, pos.getZ() + 0.5 - cam.z};
                        start[a.ordinal()] += (u - 0.5) * (1 + 2 * INFLATE);
                        start[b.ordinal()] += (v - 0.5) * (1 + 2 * INFLATE);
                        double[] end = start.clone();
                        start[axis.ordinal()] -= 0.5 + INFLATE;
                        end[axis.ordinal()] += 0.5 + INFLATE;
                        edges.add(new float[]{(float) start[0], (float) start[1], (float) start[2],
                                (float) end[0], (float) end[1], (float) end[2], axis.ordinal()});
                    }
                }
            }
        }

        // Faint pass without depth test: the parts hidden inside walls still show where the shape goes.
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(Math.max(2.5f, Minecraft.getInstance().getWindow().getWidth() / 1920f * 2.5f));
        BufferBuilder hidden = Tesselator.getInstance().getBuilder();
        hidden.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (float[] e : edges) line(hidden, matrix, normal, e, 0.25f);
        BufferUploader.drawWithShader(hidden.end());
        RenderSystem.enableDepthTest();

        // Bright pass with depth test: the edges you can actually see.
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (float[] e : edges) line(lines, matrix, normal, e, 0.95f);
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

    /** One edge {x1, y1, z1, x2, y2, z2, axis}. */
    private static void line(VertexConsumer lines, Matrix4f matrix, Matrix3f normal, float[] e, float alpha) {
        float nx = e[6] == 0 ? 1 : 0;
        float ny = e[6] == 1 ? 1 : 0;
        float nz = e[6] == 2 ? 1 : 0;
        lines.vertex(matrix, e[0], e[1], e[2]).color(1f, 0.82f, 0.25f, alpha).normal(normal, nx, ny, nz).endVertex();
        lines.vertex(matrix, e[3], e[4], e[5]).color(1f, 0.82f, 0.25f, alpha).normal(normal, nx, ny, nz).endVertex();
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
            event.register(SHAPE_KEY);
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("vein_count", VeinClient::renderCount);
        }
    }
}
