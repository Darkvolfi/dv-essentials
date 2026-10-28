package com.darkvolfi.dvlib.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.Set;

/** A living entity's face, rendered from its real model into a square in the GUI (DV Map markers, DV Lens). */
public final class MobFace {
    /** Entity types whose renderer failed once in the GUI; callers draw something else for them. */
    private static final Set<EntityType<?>> BROKEN = new HashSet<>();
    /** Side of the square shown for a normal-sized head, in blocks. */
    private static final float HEAD_BLOCKS = 0.6f;

    private MobFace() {}

    public static boolean canDraw(LivingEntity e) {
        return !BROKEN.contains(e.getType());
    }

    /** Draws the face centred on (cx, cy), {@code size} pixels square. */
    public static void draw(GuiGraphics g, LivingEntity e, int cx, int cy, int size) {
        // A sleeping (or swimming, crouching…) mob has a tiny box and lies sideways, which zoomed into one
        // corner of its face. Stand it up just for this render; client-side only, restored below.
        Pose pose = e.getPose();
        if (pose != Pose.STANDING) e.setPose(Pose.STANDING);
        try {
            drawStanding(g, e, cx, cy, size);
        } finally {
            if (pose != Pose.STANDING) e.setPose(pose);
        }
    }

    private static void drawStanding(GuiGraphics g, LivingEntity e, int cx, int cy, int size) {
        float head;
        float centerY;
        if (e instanceof Slime || e instanceof Ghast || e instanceof Shulker) {
            // No separate head: the whole body is the face.
            head = Mth.clamp(Math.min(e.getBbWidth(), e.getBbHeight()) * 1.15f, 0.4f, 6.0f);
            centerY = e.getBbHeight() / 2.0f;
        } else {
            // Crop tightly around the eyes so only the face shows. Most heads are about half a block;
            // wide mobs (spiders, golems, ravagers, giants) have bigger ones, babies smaller-looking ones.
            head = HEAD_BLOCKS * Math.max(1.0f, e.getBbWidth() / 0.9f) * (e.isBaby() ? 0.75f : 1.0f);
            centerY = e.getEyeHeight() + head * 0.1f;
        }
        int scale = Math.max(1, Math.round(size / head));
        int half = size / 2;

        float bodyRot = e.yBodyRot, bodyRotO = e.yBodyRotO, headRot = e.yHeadRot, headRotO = e.yHeadRotO;
        float xRot = e.getXRot(), xRotO = e.xRotO, yRot = e.getYRot();
        // Face the viewer, like the inventory preview.
        e.yBodyRot = e.yBodyRotO = e.yHeadRot = e.yHeadRotO = 180.0f;
        e.setXRot(0.0f);
        e.xRotO = 0.0f;
        e.setYRot(180.0f);
        g.enableScissor(cx - half, cy - half, cx - half + size, cy - half + size);
        try {
            InventoryScreen.renderEntityInInventory(g, cx, cy + Math.round(centerY * scale), scale,
                    new Quaternionf().rotateZ((float) Math.PI), null, e);
        } catch (RuntimeException ex) {
            BROKEN.add(e.getType());
        } finally {
            g.disableScissor();
            e.yBodyRot = bodyRot;
            e.yBodyRotO = bodyRotO;
            e.yHeadRot = headRot;
            e.yHeadRotO = headRotO;
            e.setXRot(xRot);
            e.xRotO = xRotO;
            e.setYRot(yRot);
        }
        // Each face renders in depth; clear so the next icon is not hidden behind this one.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }
}
