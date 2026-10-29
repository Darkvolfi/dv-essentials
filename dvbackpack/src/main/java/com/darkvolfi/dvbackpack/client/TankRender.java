package com.darkvolfi.dvbackpack.client;

import com.darkvolfi.dvbackpack.DVBackpack;
import com.darkvolfi.dvbackpack.upgrade.TankUpgrade;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The tank cards' tanks on the backpack's sides, in the backpack model's own space (pixels 0-16): a glass
 * column with cut corners (so it looks round), metal caps top and bottom, and the fluid inside as high as the
 * tank is full. The first tank goes on the left, the second on the right.
 */
final class TankRender {
    private static final ResourceLocation GLASS = new ResourceLocation(DVBackpack.MOD_ID, "block/tank_glass");
    private static final ResourceLocation METAL = new ResourceLocation("block/iron_block");
    /** Left and right tank, x from-to; both share z, and y from the bag's bottom (see {@link #render}). */
    private static final float[][] X = {{0.6f, 3.2f}, {12.8f, 15.4f}};
    private static final float Z0 = 6.7f;
    private static final float Z1 = 10.3f;
    private static final float BOTTOM = 0.5f;
    /** Just under the lid. */
    private static final float TOP = 8.3f;
    private static final float CAP = 0.6f;
    /** How much each corner of the column is cut off. */
    private static final float CORNER = 0.6f;
    /** How far the fluid sits inside the glass. */
    private static final float INSET = 0.3f;
    /** Keeps faces that would touch slightly apart (glass from caps, fluid from glass), so none flicker. */
    private static final float GAP = 0.02f;

    private TankRender() {}

    /** @param bagBottom how high the bag's bottom is in the model: 0 set down, 3 worn (the bedroll is under it) */
    static void render(PoseStack pose, MultiBufferSource buffers, int light, List<FluidStack> tanks, float bagBottom) {
        if (tanks.isEmpty()) return;
        // Not culled: the glass is seen from inside too, and no face can vanish for being wound the wrong way.
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite glass = atlas.apply(GLASS);
        TextureAtlasSprite metal = atlas.apply(METAL);
        float y0 = bagBottom + BOTTOM;
        float y1 = bagBottom + TOP;
        for (int i = 0; i < Math.min(2, tanks.size()); i++) {
            float x0 = X[i][0];
            float x1 = X[i][1];
            FluidStack fluid = tanks.get(i);
            if (!fluid.isEmpty()) {
                IClientFluidTypeExtensions look = IClientFluidTypeExtensions.of(fluid.getFluid());
                float fill = Math.min(1f, fluid.getAmount() / (float) TankUpgrade.CAPACITY);
                float bottom = y0 + CAP + 3 * GAP;
                float top = bottom + (y1 - CAP - 3 * GAP - bottom) * fill;
                column(pose, buffer, atlas.apply(look.getStillTexture(fluid)), x0 + INSET, bottom, Z0 + INSET,
                        x1 - INSET, top, Z1 - INSET, look.getTintColor(fluid), light, false);
            }
            column(pose, buffer, glass, x0, y0 + CAP + 1.5f * GAP, Z0, x1, y1 - CAP - 1.5f * GAP, Z1, 0xFFFFFFFF, light, true);
            column(pose, buffer, metal, x0 - 0.15f, y0, Z0 - 0.15f, x1 + 0.15f, y0 + CAP, Z1 + 0.15f, 0xFFFFFFFF, light, false);
            column(pose, buffer, metal, x0 - 0.15f, y1 - CAP, Z0 - 0.15f, x1 + 0.15f, y1, Z1 + 0.15f, 0xFFFFFFFF, light, false);
        }
    }

    /** A box with its four upright corners cut off: two boxes crossed, one narrower in x, one in z. */
    private static void column(PoseStack pose, VertexConsumer buffer, TextureAtlasSprite sprite,
                               float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light, boolean whole) {
        box(pose, buffer, sprite, x0 + CORNER, y0, z0, x1 - CORNER, y1, z1, argb, light, whole);
        // A hair shorter, so its top and bottom never lie in the same plane as the other box's (they would flicker).
        box(pose, buffer, sprite, x0, y0 + GAP, z0 + CORNER, x1, y1 - GAP, z1 - CORNER, argb, light, whole);
    }

    /**
     * A box in model pixels. Each face takes the part of the sprite its position covers, like a block model, or with
     * {@code whole} the whole sprite (the glass, whose streaks would otherwise mostly fall outside a thin tank).
     */
    private static void box(PoseStack pose, VertexConsumer buffer, TextureAtlasSprite sprite,
                            float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light, boolean whole) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float s = 1 / 16f;
        x0 *= s; y0 *= s; z0 *= s; x1 *= s; y1 *= s; z1 *= s;
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255, a = argb >>> 24;
        if (a == 0) a = 255;
        // Each face: four corners counter-clockwise seen from outside, and the sprite coordinates for them.
        face(buffer, m, n, sprite, whole, r, g, b, a, light, Direction.UP,
                x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, z0, x1, z1);
        face(buffer, m, n, sprite, whole, r, g, b, a, light, Direction.DOWN,
                x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, z0, x1, z1);
        face(buffer, m, n, sprite, whole, r, g, b, a, light, Direction.NORTH,
                x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, x0, 1 - y1, x1, 1 - y0);
        face(buffer, m, n, sprite, whole, r, g, b, a, light, Direction.SOUTH,
                x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, 1 - y1, x1, 1 - y0);
        face(buffer, m, n, sprite, whole, r, g, b, a, light, Direction.WEST,
                x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, z0, 1 - y1, z1, 1 - y0);
        face(buffer, m, n, sprite, whole, r, g, b, a, light, Direction.EAST,
                x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, z0, 1 - y1, z1, 1 - y0);
    }

    private static void face(VertexConsumer buffer, Matrix4f m, Matrix3f n, TextureAtlasSprite sprite, boolean whole, int r, int g, int b, int a, int light,
                             Direction side, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float u0, float v0, float u1, float v1) {
        if (whole) {
            u0 = 0;
            v0 = 0;
            u1 = 1;
            v1 = 1;
        }
        float su0 = sprite.getU(u0 * 16), su1 = sprite.getU(u1 * 16);
        float sv0 = sprite.getV(v0 * 16), sv1 = sprite.getV(v1 * 16);
        float nx = side.getStepX(), ny = side.getStepY(), nz = side.getStepZ();
        vertex(buffer, m, n, ax, ay, az, su0, sv0, r, g, b, a, light, nx, ny, nz);
        vertex(buffer, m, n, bx, by, bz, su0, sv1, r, g, b, a, light, nx, ny, nz);
        vertex(buffer, m, n, cx, cy, cz, su1, sv1, r, g, b, a, light, nx, ny, nz);
        vertex(buffer, m, n, dx, dy, dz, su1, sv0, r, g, b, a, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        buffer.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
