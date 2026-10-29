package com.darkvolfi.dvbackpack.client;

import com.darkvolfi.dvbackpack.BackpackBlock;
import com.darkvolfi.dvbackpack.BackpackItem;
import com.darkvolfi.dvbackpack.DVBackpack;
import com.darkvolfi.dvbackpack.WornBackpack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/** Draws the worn backpack on the player's back, with its tanks beside it. */
public class BackpackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** The worn model: the set-down one raised, with the sleeping bag rolled up under the bag (loaded by {@link BackpackClient}). */
    public static final ResourceLocation WORN_MODEL = new ResourceLocation(DVBackpack.MOD_ID, "block/backpack_worn");
    private final RandomSource random = RandomSource.create();

    public BackpackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
        if (player.isInvisible()) return;
        ItemStack stack = WornBackpack.find(player);
        if (!(stack.getItem() instanceof BackpackItem item)) return;
        BlockState state = DVBackpack.BACKPACK_BLOCK.get().defaultBlockState().setValue(BackpackBlock.TIER, item.tier().ordinal());

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        // Model space has y pointing down and the back towards +z. Turning the model over on x puts its top up
        // and its pocket away from the player; its top (15.5/16) comes to the shoulders and its shoulder straps
        // (z = 11.6/16) to the back (z = 2/16).
        pose.translate(0, 15.5 / 16, 5.6 / 16);
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.translate(-0.5, 0, -0.5);
        drawModel(pose, buffers, light, state, item.tier().color(), BackpackItem.bagColor(stack));
        TankRender.render(pose, buffers, light, BackpackItem.tanks(stack), 3);
        pose.popPose();
    }

    /**
     * The block model, quad by quad, because it has two tints (the tier's colour, the sleeping bag's colour) and the
     * block renderer's one-off drawing only gives every tinted quad the first.
     */
    private void drawModel(PoseStack pose, MultiBufferSource buffers, int light, BlockState state, int tierColor, int bagColor) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(WORN_MODEL);
        VertexConsumer buffer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        for (Direction side : SIDES) {
            random.setSeed(42);
            for (BakedQuad quad : model.getQuads(state, side, random, ModelData.EMPTY, null)) {
                int rgb = !quad.isTinted() ? 0xFFFFFF : quad.getTintIndex() == 0 ? tierColor : bagColor;
                buffer.putBulkData(pose.last(), quad, (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f,
                        light, OverlayTexture.NO_OVERLAY);
            }
        }
    }
}
