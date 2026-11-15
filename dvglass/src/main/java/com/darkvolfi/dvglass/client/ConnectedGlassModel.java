package com.darkvolfi.dvglass.client;

import com.darkvolfi.dvglass.DVGlass;
import com.darkvolfi.dvglass.GlassConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a vanilla glass block or pane model. Every glass face is cut into four quarters, and each quarter is drawn
 * with the texture variant that matches its two neighbours and the diagonal one: a frame corner, a frame edge,
 * an inner corner dot or no frame at all. Faces with no neighbour data (items in hand) stay vanilla.
 */
public final class ConnectedGlassModel extends BakedModelWrapper<BakedModel> {
    /** 8 connection bits per face, face {@code d} at bits {@code d.ordinal() * 8}; see {@link #NEIGHBOURS}. */
    private static final ModelProperty<Long> CONNECTIONS = new ModelProperty<>();
    /** Extra bits after the 6 faces: the same glass directly above / below (used to hide stacked pane edges). */
    private static final long SAME_ABOVE = 1L << 48, SAME_BELOW = 1L << 49;

    /** Texture variants, in index order: frame on all sides, frame top+bottom, frame left+right, corner dots, none. */
    private static final String[] VARIANTS = {"full", "horizontal", "vertical", "corner", "none"};
    private static final int FULL = 0, HORIZONTAL = 1, VERTICAL = 2, CORNER = 3, NONE = 4;

    /** Per face, the neighbour checked by each connection bit, as {right, up} steps along the face. */
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    private final Block block;
    private final boolean pane;
    private final ResourceLocation glassTexture;
    private final String texture;
    /** Resolved on first use: the atlas is only uploaded after models are baked. Normal variants, then clear ones. */
    private volatile TextureAtlasSprite[] sprites;

    public ConnectedGlassModel(BakedModel original, Block block, boolean pane, String texture) {
        super(original);
        this.block = block;
        this.pane = pane;
        this.texture = texture;
        this.glassTexture = new ResourceLocation("minecraft", "block/" + texture);
    }

    /** The direction that is "right" on a face seen from outside, matching vanilla's default UVs. */
    private static Direction right(Direction face) {
        return switch (face) {
            case NORTH -> Direction.WEST;
            case SOUTH, UP, DOWN -> Direction.EAST;
            case WEST -> Direction.SOUTH;
            case EAST -> Direction.NORTH;
        };
    }

    /** The direction that is "up" on a face seen from outside, matching vanilla's default UVs. */
    private static Direction up(Direction face) {
        return switch (face) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state,
                                           @NotNull ModelData modelData) {
        ModelData data = super.getModelData(level, pos, state, modelData);
        // Which of the 27 blocks around (and including) this one are the same glass.
        boolean[] same = new boolean[27];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 27; i++) {
            same[i] = level.getBlockState(cursor.setWithOffset(pos, i % 3 - 1, i / 3 % 3 - 1, i / 9 - 1)).getBlock() == block;
        }
        long connections = 0;
        for (Direction face : Direction.values()) {
            Direction r = right(face), u = up(face);
            for (int bit = 0; bit < NEIGHBOURS.length; bit++) {
                int a = NEIGHBOURS[bit][0], b = NEIGHBOURS[bit][1];
                int x = r.getStepX() * a + u.getStepX() * b, y = r.getStepY() * a + u.getStepY() * b, z = r.getStepZ() * a + u.getStepZ() * b;
                // A full block's neighbour covered on this side hides its face, so the frame stays there.
                boolean joined = isSame(same, x, y, z)
                        && (pane || !isSame(same, x + face.getStepX(), y + face.getStepY(), z + face.getStepZ()));
                if (joined) {
                    connections |= 1L << (face.ordinal() * 8 + bit);
                }
            }
        }
        // Only a pane of the same shape covers the whole edge; otherwise hiding it would leave a hole.
        if (level.getBlockState(pos.above()) == state) connections |= SAME_ABOVE;
        if (level.getBlockState(pos.below()) == state) connections |= SAME_BELOW;
        return data.derive().with(CONNECTIONS, connections).build();
    }

    private static boolean hiddenEdge(Direction dir, long connections) {
        return dir == Direction.UP ? (connections & SAME_ABOVE) != 0 : dir == Direction.DOWN && (connections & SAME_BELOW) != 0;
    }

    private static boolean isSame(boolean[] same, int x, int y, int z) {
        return Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1 && same[(x + 1) + (y + 1) * 3 + (z + 1) * 9];
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        Long connections = data.get(CONNECTIONS);
        if (connections == null || quads.isEmpty()) {
            return quads;
        }
        TextureAtlasSprite[] sprites = sprites();
        if (sprites.length == 0) {
            return quads;
        }
        int first = GlassConfig.BORDERLESS.get() ? VARIANTS.length : 0;
        List<BakedQuad> out = new ArrayList<>(quads.size() * 4);
        for (BakedQuad quad : quads) {
            if (quad.getSprite().contents().name().equals(glassTexture)) {
                int bits = (int) (connections >>> (quad.getDirection().ordinal() * 8)) & 0xFF;
                split(quad, bits, sprites, first, out);
            } else if (pane && hiddenEdge(quad.getDirection(), connections)) {
                // Vanilla pane edges on top and bottom have no cullface, so stacked panes would show a line between them.
                continue;
            } else {
                out.add(quad);
            }
        }
        return out;
    }

    private TextureAtlasSprite[] sprites() {
        TextureAtlasSprite[] result = sprites;
        if (result == null) {
            TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            result = new TextureAtlasSprite[VARIANTS.length * 2];
            for (int i = 0; i < result.length; i++) {
                String variant = (i < VARIANTS.length ? "" : "clear_") + VARIANTS[i % VARIANTS.length];
                result[i] = atlas.getSprite(new ResourceLocation(DVGlass.MOD_ID, "block/" + texture + "_" + variant));
                if (result[i].contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                    result = new TextureAtlasSprite[0]; // textures missing (e.g. a resource pack removed them): stay vanilla
                    break;
                }
            }
            sprites = result;
        }
        return result;
    }

    /** Position along {@code dir}, 0 at the block side opposite to it, 1 at the side it points to. */
    private static float along(float x, float y, float z, Direction dir) {
        float c = (float) dir.getAxis().choose(x, y, z);
        return dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? c : 1 - c;
    }

    private static float back(float value, Direction dir) {
        return dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? value : 1 - value;
    }

    /** Cuts {@code quad} along the middle lines of its face and draws each piece with its quarter's variant. */
    private static void split(BakedQuad quad, int bits, TextureAtlasSprite[] sprites, int first, List<BakedQuad> out) {
        Direction face = quad.getDirection();
        Direction r = right(face), u = up(face);
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        float[] a = new float[4], b = new float[4];
        float a0 = 1, a1 = 0, b0 = 1, b1 = 0;
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(vertices[i * stride]);
            float y = Float.intBitsToFloat(vertices[i * stride + 1]);
            float z = Float.intBitsToFloat(vertices[i * stride + 2]);
            a[i] = along(x, y, z, r);
            b[i] = along(x, y, z, u);
            a0 = Math.min(a0, a[i]); a1 = Math.max(a1, a[i]);
            b0 = Math.min(b0, b[i]); b1 = Math.max(b1, b[i]);
        }
        float aMid = (a0 + a1) / 2, bMid = (b0 + b1) / 2;
        for (int qa = 0; qa < 2; qa++) {
            float lowA = Math.max(a0, qa * 0.5f), highA = Math.min(a1, qa * 0.5f + 0.5f);
            if (highA - lowA < 1e-4f) continue;
            for (int qb = 0; qb < 2; qb++) {
                float lowB = Math.max(b0, qb * 0.5f), highB = Math.min(b1, qb * 0.5f + 0.5f);
                if (highB - lowB < 1e-4f) continue;
                TextureAtlasSprite sprite = sprites[first + variant(bits, qa == 1, qb == 1)];
                int[] piece = vertices.clone();
                for (int i = 0; i < 4; i++) {
                    float na = a[i] <= aMid ? lowA : highA;
                    float nb = b[i] <= bMid ? lowB : highB;
                    int o = i * stride;
                    float[] pos = {Float.intBitsToFloat(vertices[o]), Float.intBitsToFloat(vertices[o + 1]), Float.intBitsToFloat(vertices[o + 2])};
                    pos[r.getAxis().ordinal()] = back(na, r);
                    pos[u.getAxis().ordinal()] = back(nb, u);
                    piece[o] = Float.floatToRawIntBits(pos[0]);
                    piece[o + 1] = Float.floatToRawIntBits(pos[1]);
                    piece[o + 2] = Float.floatToRawIntBits(pos[2]);
                    piece[o + 4] = Float.floatToRawIntBits(sprite.getU(na * 16));
                    piece[o + 5] = Float.floatToRawIntBits(sprite.getV((1 - nb) * 16));
                }
                out.add(new BakedQuad(piece, quad.getTintIndex(), face, sprite, quad.isShade(), quad.hasAmbientOcclusion()));
            }
        }
    }

    /** Texture variant for one quarter of a face (right or left half, top or bottom half) with these connection bits. */
    private static int variant(int bits, boolean rightSide, boolean topSide) {
        boolean side = (bits & (rightSide ? 1 : 2)) != 0;
        boolean vertical = (bits & (topSide ? 4 : 8)) != 0;
        boolean diagonal = (bits & (rightSide ? (topSide ? 16 : 64) : (topSide ? 32 : 128))) != 0;
        if (!side && !vertical) return FULL;
        if (side && !vertical) return HORIZONTAL;
        if (!side) return VERTICAL;
        return diagonal ? NONE : CORNER;
    }
}
