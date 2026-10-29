package com.darkvolfi.dvsouls.client;

import com.darkvolfi.dvsouls.DVSouls;
import com.darkvolfi.dvsouls.hybrid.FeatheredPig;
import com.darkvolfi.dvsouls.hybrid.LavaHorse;
import com.darkvolfi.dvsouls.hybrid.WolfFox;
import com.darkvolfi.dvsouls.hybrid.WoollyCow;
import com.google.common.collect.Iterables;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.HorseArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SaddleLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.util.List;

/**
 * How the hybrids look: each is drawn with the model of the creature it is built on, in its own texture (made by
 * tools/make-souls-mobs.ps1), plus what the other half gives it: wool on the cow, wings on the pig, a glow in the horse's
 * cracks; the wolf fox wears the fox's own model and texture, and holds things in its mouth.
 */
final class HybridRenderers {
    private static final ModelLayerLocation FEATHERED_PIG = new ModelLayerLocation(new ResourceLocation(DVSouls.MOD_ID, "feathered_pig"), "main");
    private static final ModelLayerLocation FEATHERED_PIG_SADDLE = new ModelLayerLocation(new ResourceLocation(DVSouls.MOD_ID, "feathered_pig"), "saddle");
    private static final ModelLayerLocation WOOLLY_COW_WOOL = new ModelLayerLocation(new ResourceLocation(DVSouls.MOD_ID, "woolly_cow"), "wool");

    private HybridRenderers() {}

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(DVSouls.MOD_ID, "textures/entity/" + name + ".png");
    }

    static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FEATHERED_PIG, () -> FeatheredPigModel.createLayer(CubeDeformation.NONE, true));
        event.registerLayerDefinition(FEATHERED_PIG_SADDLE, () -> FeatheredPigModel.createLayer(new CubeDeformation(0.5f), false));
        event.registerLayerDefinition(WOOLLY_COW_WOOL, CowWoolModel::createLayer);
    }

    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DVSouls.WOOLLY_COW.get(), WoollyCowRenderer::new);
        event.registerEntityRenderer(DVSouls.FEATHERED_PIG.get(), FeatheredPigRenderer::new);
        event.registerEntityRenderer(DVSouls.LAVA_HORSE.get(), LavaHorseRenderer::new);
        event.registerEntityRenderer(DVSouls.WOLF_FOX.get(), WolfFoxRenderer::new);
    }

    /** A cow with bare, pinkish skin under a thick coat of wool, drawn as a layer that shearing takes off. */
    static final class WoollyCowRenderer extends MobRenderer<WoollyCow, GrazingCowModel> {
        private static final ResourceLocation SKIN = texture("woolly_cow");
        private static final ResourceLocation WOOL = texture("woolly_cow_wool");

        WoollyCowRenderer(EntityRendererProvider.Context context) {
            super(context, new GrazingCowModel(context.bakeLayer(ModelLayers.COW)), 0.7f);
            CowWoolModel wool = new CowWoolModel(context.bakeLayer(WOOLLY_COW_WOOL));
            addLayer(new RenderLayer<>(this) {
                @Override
                public void render(PoseStack pose, MultiBufferSource buffers, int light, WoollyCow cow, float limbSwing,
                                   float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
                    if (cow.isSheared() || cow.isInvisible()) return;
                    coloredCutoutModelCopyLayerRender(getParentModel(), wool, WOOL, pose, buffers, light, cow,
                            limbSwing, limbSwingAmount, age, headYaw, headPitch, partialTick, 1, 1, 1);
                }
            });
        }

        @Override
        public ResourceLocation getTextureLocation(WoollyCow cow) {
            return SKIN;
        }
    }

    /** The cow's model, lowering its head to graze as a sheep does. */
    static final class GrazingCowModel extends CowModel<WoollyCow> {
        private float headTilt;

        GrazingCowModel(ModelPart root) {
            super(root);
        }

        @Override
        public void prepareMobModel(WoollyCow cow, float limbSwing, float limbSwingAmount, float partialTick) {
            super.prepareMobModel(cow, limbSwing, limbSwingAmount, partialTick);
            head.y = 4 + cow.getHeadEatPositionScale(partialTick) * 9;
            headTilt = cow.getHeadEatAngleScale(partialTick);
        }

        @Override
        public void setupAnim(WoollyCow cow, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(cow, limbSwing, limbSwingAmount, age, headYaw, headPitch);
            head.xRot = headTilt;
        }
    }

    /**
     * The cow's wool: its body puffed out on all sides, and the tops of its legs. Like the cow's own model, so it
     * moves with it; the head has no wool.
     */
    static final class CowWoolModel extends QuadrupedModel<WoollyCow> {
        CowWoolModel(ModelPart root) {
            // The cow model's own numbers for how a calf's head and body are scaled.
            super(root, false, 10, 4, 2, 2, 24);
        }

        static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0, 4, -8));
            root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(18, 4).addBox(-6, -10, -7, 12, 18, 10, new CubeDeformation(1.6f)),
                    PartPose.offsetAndRotation(0, 5, 2, Mth.HALF_PI, 0, 0));
            CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 6, 4, new CubeDeformation(0.5f));
            root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-4, 12, 7));
            root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(4, 12, 7));
            root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-4, 12, -6));
            root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(4, 12, -6));
            return LayerDefinition.create(mesh, 64, 32);
        }
    }

    /** A pig's model with a small wing on each side, which flap while it is in the air. */
    static final class FeatheredPigModel extends PigModel<FeatheredPig> {
        private final ModelPart leftWing;
        private final ModelPart rightWing;

        FeatheredPigModel(ModelPart root) {
            super(root);
            leftWing = root.hasChild("left_wing") ? root.getChild("left_wing") : null;
            rightWing = root.hasChild("right_wing") ? root.getChild("right_wing") : null;
        }

        /**
         * The pig's body and head; with {@code hen} also a hen's red comb on its head and a wing on each side, their
         * texture below the pig's (so 64x64). The saddle's model is the plain pig, a little larger.
         */
        static LayerDefinition createLayer(CubeDeformation deformation, boolean hen) {
            MeshDefinition mesh = QuadrupedModel.createBodyMesh(6, deformation);
            PartDefinition root = mesh.getRoot();
            CubeListBuilder head = CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-4, -4, -8, 8, 8, 8, deformation)
                    .texOffs(16, 16).addBox(-2, 0, -9, 4, 3, 1, deformation);
            if (hen) head.texOffs(16, 32).addBox(-1, -7, -6, 2, 3, 4);
            root.addOrReplaceChild("head", head, PartPose.offset(0, 12, -6));
            if (!hen) return LayerDefinition.create(mesh, 64, 32);
            // Against the body's sides (x ±5), from just below its back (y 10), hinged at the top.
            root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 32).addBox(0, 0, -3, 1, 4, 6), PartPose.offset(5, 10.5f, 0));
            root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 32).mirror().addBox(-1, 0, -3, 1, 4, 6), PartPose.offset(-5, 10.5f, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        protected Iterable<ModelPart> bodyParts() {
            return leftWing == null ? super.bodyParts() : Iterables.concat(super.bodyParts(), List.of(leftWing, rightWing));
        }

        @Override
        public void setupAnim(FeatheredPig pig, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(pig, limbSwing, limbSwingAmount, age, headYaw, headPitch);
            if (leftWing == null) return;
            float flap = pig.onGround() ? 0 : (Mth.sin(age * 1.4f) + 1) * 0.6f;
            leftWing.zRot = -flap;
            rightWing.zRot = flap;
        }
    }

    static final class FeatheredPigRenderer extends MobRenderer<FeatheredPig, FeatheredPigModel> {
        private static final ResourceLocation TEXTURE = texture("feathered_pig");

        FeatheredPigRenderer(EntityRendererProvider.Context context) {
            super(context, new FeatheredPigModel(context.bakeLayer(FEATHERED_PIG)), 0.7f);
            addLayer(new SaddleLayer<>(this, new FeatheredPigModel(context.bakeLayer(FEATHERED_PIG_SADDLE)),
                    new ResourceLocation("textures/entity/pig/pig_saddle.png")));
        }

        @Override
        public ResourceLocation getTextureLocation(FeatheredPig pig) {
            return TEXTURE;
        }
    }

    /** A basalt-dark horse whose cracks glow like embers, even in the dark. */
    static final class LavaHorseRenderer extends AbstractHorseRenderer<LavaHorse, HorseModel<LavaHorse>> {
        private static final ResourceLocation TEXTURE = texture("lava_horse");
        private static final RenderType GLOW = RenderType.eyes(texture("lava_horse_glow"));

        @SuppressWarnings({"unchecked", "rawtypes"}) // the game's horse armour layer is written for plain horses
        LavaHorseRenderer(EntityRendererProvider.Context context) {
            super(context, new HorseModel<>(context.bakeLayer(ModelLayers.HORSE)), 1.1f);
            addLayer((RenderLayer) new HorseArmorLayer((RenderLayerParent) this, context.getModelSet()));
            addLayer(new EyesLayer<>(this) {
                @Override
                public RenderType renderType() {
                    return GLOW;
                }
            });
        }

        @Override
        public ResourceLocation getTextureLocation(LavaHorse horse) {
            return TEXTURE;
        }
    }

    /**
     * Looks just like a fox: the game's own fox model and texture. The model is driven here rather than by the fox's,
     * which reads a fox's states (sleeping, pouncing); this one walks, looks about, sits like a fox when told to, and
     * cocks its head like a wolf.
     */
    static final class WolfFoxModel extends AgeableListModel<WolfFox> {
        final ModelPart head;
        private final ModelPart body;
        private final ModelPart tail;
        private final ModelPart rightHindLeg;
        private final ModelPart leftHindLeg;
        private final ModelPart rightFrontLeg;
        private final ModelPart leftFrontLeg;

        WolfFoxModel(ModelPart root) {
            // The fox model's own numbers for how a cub's head is scaled and placed.
            super(true, 8, 3.35f);
            head = root.getChild("head");
            body = root.getChild("body");
            tail = body.getChild("tail");
            rightHindLeg = root.getChild("right_hind_leg");
            leftHindLeg = root.getChild("left_hind_leg");
            rightFrontLeg = root.getChild("right_front_leg");
            leftFrontLeg = root.getChild("left_front_leg");
        }

        @Override
        public void prepareMobModel(WolfFox fox, float limbSwing, float limbSwingAmount, float partialTick) {
            for (ModelPart part : List.of(head, body, tail, rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg)) part.resetPose();
            float swing = limbSwing * 0.6662f;
            rightHindLeg.xRot = Mth.cos(swing) * 1.4f * limbSwingAmount;
            leftHindLeg.xRot = Mth.cos(swing + Mth.PI) * 1.4f * limbSwingAmount;
            rightFrontLeg.xRot = Mth.cos(swing + Mth.PI) * 1.4f * limbSwingAmount;
            leftFrontLeg.xRot = Mth.cos(swing) * 1.4f * limbSwingAmount;
            head.zRot = fox.getHeadRollAngle(partialTick);
            if (fox.isInSittingPose()) {
                // The fox's own sitting pose: body raised, hind legs folded under, tail on the ground.
                body.xRot = Mth.PI / 6;
                body.setPos(0, 9, -3);
                tail.xRot = Mth.PI / 4;
                tail.setPos(-4, 15, -2);
                head.setPos(-1, 10, -0.25f);
                rightHindLeg.xRot = leftHindLeg.xRot = -1.3089969f;
                rightHindLeg.setPos(-5, 21.5f, 6.75f);
                leftHindLeg.setPos(-1, 21.5f, 6.75f);
                rightFrontLeg.xRot = leftFrontLeg.xRot = -Mth.PI / 12;
            }
        }

        @Override
        public void setupAnim(WolfFox fox, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
            head.xRot = headPitch * Mth.DEG_TO_RAD;
            head.yRot = headYaw * Mth.DEG_TO_RAD;
        }

        @Override
        protected Iterable<ModelPart> headParts() {
            return List.of(head);
        }

        @Override
        protected Iterable<ModelPart> bodyParts() {
            return List.of(body, rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg);
        }
    }

    static final class WolfFoxRenderer extends MobRenderer<WolfFox, WolfFoxModel> {
        private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/fox/fox.png");

        WolfFoxRenderer(EntityRendererProvider.Context context) {
            super(context, new WolfFoxModel(context.bakeLayer(ModelLayers.FOX)), 0.4f);
            addLayer(new MouthItemLayer(this, context.getItemInHandRenderer()));
        }

        @Override
        public ResourceLocation getTextureLocation(WolfFox fox) {
            return TEXTURE;
        }
    }

    /** The carried item, held crosswise in the fox's mouth and turning with its head (placed as the fox's own is). */
    static final class MouthItemLayer extends RenderLayer<WolfFox, WolfFoxModel> {
        private final ItemInHandRenderer items;

        MouthItemLayer(RenderLayerParent<WolfFox, WolfFoxModel> parent, ItemInHandRenderer items) {
            super(parent);
            this.items = items;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, WolfFox fox, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float headYaw, float headPitch) {
            ItemStack item = fox.getMainHandItem();
            if (item.isEmpty()) return;
            ModelPart head = getParentModel().head;
            pose.pushPose();
            if (fox.isBaby()) {
                pose.scale(0.75f, 0.75f, 0.75f);
                pose.translate(0, 0.5f, 0.209375f);
            }
            pose.translate(head.x / 16, head.y / 16, head.z / 16);
            pose.mulPose(Axis.ZP.rotation(head.zRot));
            pose.mulPose(Axis.YP.rotation(head.yRot));
            pose.mulPose(Axis.XP.rotation(head.xRot));
            pose.translate(fox.isBaby() ? 0.04f : 0.06f, fox.isBaby() ? 0.26f : 0.27f, -0.5f);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            items.renderItem(fox, item, ItemDisplayContext.GROUND, false, pose, buffers, light);
            pose.popPose();
        }
    }
}
