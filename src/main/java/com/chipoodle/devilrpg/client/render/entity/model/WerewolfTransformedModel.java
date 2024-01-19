package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.animation.WerewolfRendererAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class WerewolfTransformedModel<T extends Entity> extends HierarchicalModel<T> implements ArmedModel, HeadedModel {
    // This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
    public static final ModelLayerLocation WEREWOLF_LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "werewolftransformedlayer"), "main");
    public static final ModelLayerLocation WEREWOLF_INNER_ARMOR_LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "werewolftransformed_inner_armor_layer"), "main");
    public static final ModelLayerLocation WEREWOLF_OUTER_ARMOR_LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "werewolftransformed_outer_armor_layer"), "main");


    private static final Vector3f ANIMATION_VECTOR_CACHE = new Vector3f();
    public final AnimationState walkAnimationState = new AnimationState();
    public final AnimationState leftAttackAnimationState = new AnimationState();
    public final AnimationState rightAttackAnimationState = new AnimationState();
    public final AnimationState crouchAnimationState = new AnimationState();
    public final AnimationState swimAnimationState = new AnimationState();

    public final ModelPart root;

    public final ModelPart head;
    public final ModelPart body;
    public final ModelPart legs;
    public final ModelPart arms;
    public final ModelPart rightArm;
    public final ModelPart leftArm;
    public final ModelPart rightLeg;
    public final ModelPart leftLeg;
    public final ModelPart ears;
    private final ModelPart torax;
    private final ModelPart tail;
    private final ModelPart neckBase;
    private final ModelPart rightHand;
    private final ModelPart leftHand;

    private final ModelPart rFingers;
    private final ModelPart lFingers;

    private final ModelPart rightWrist;
    private final ModelPart leftWrist;
    private final ModelPart upperJaw;
    private final ModelPart lowerJaw;
    private final ModelPart chest;
    private final ModelPart rightLegCBone;
    private final ModelPart leftLegCBone;
    private final ModelPart rFootBone;
    private final ModelPart lFootBone;
    private final ModelPart rightLegBBone;
    private final ModelPart leftLegBBone;
    private final List<ModelPart> parts;

    public HumanoidModel.ArmPose leftArmPose = HumanoidModel.ArmPose.EMPTY;
    public HumanoidModel.ArmPose rightArmPose = HumanoidModel.ArmPose.EMPTY;
    private float swimAmount;

    public WerewolfTransformedModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root;
        this.head = this.root.getChild("head");
        this.body = this.root.getChild("body");
        this.legs = this.root.getChild("legs");

        this.arms = body.getChild("arms");
        this.torax = body.getChild("torax");

        this.rightArm = arms.getChild("right_arm");
        this.leftArm = arms.getChild("left_arm");

        this.rightLeg = legs.getChild("right_leg");
        this.leftLeg = legs.getChild("left_leg");

        this.upperJaw = head.getChild("upper_jaw");
        this.ears = head.getChild("ears");
        this.neckBase = head.getChild("neck_base");
        this.lowerJaw = head.getChild("lower_jaw");

        this.rightHand = rightArm.getChild("r_hand");
        this.leftHand = leftArm.getChild("l_hand");

        this.rFingers = rightHand.getChild("r_fingers");
        this.lFingers = leftHand.getChild("l_fingers");

        this.rightWrist = rightHand.getChild("r_wrist_r1");
        this.leftWrist = leftHand.getChild("l_wrist_r1");

        this.chest = torax.getChild("chest");
        this.tail = torax.getChild("tail");

        this.rightLegCBone = rightLeg.getChild("r_leg_c_bone");
        this.leftLegCBone = leftLeg.getChild("l_leg_c_bone");

        this.rightLegBBone = rightLegCBone.getChild("r_leg_b_bone");
        this.leftLegBBone = leftLegCBone.getChild("l_leg_b_bone");

        this.rFootBone = rightLegBBone.getChild("r_foot_bone");
        this.lFootBone = leftLegBBone.getChild("l_foot_bone");

        this.parts = List.of(head, body, arms, legs, rightArm, leftArm, rightLeg, leftLeg, ears, upperJaw, lowerJaw, torax, chest, tail, neckBase, rightHand, leftHand,leftWrist,rightWrist, rFingers,lFingers, rightLegCBone, leftLegCBone, rightLegBBone, leftLegBBone, rFootBone, lFootBone);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.1479F, -6.8055F, -9.425F, 8.0F, 8.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -8.0F, 2.0F, 0.0873F, 0.0F, 0.0F));

        PartDefinition side_burn_r_r1 = head.addOrReplaceChild("side_burn_r_r1", CubeListBuilder.create().texOffs(28, 45).addBox(3.0F, -11.0F, 4.0F, 2.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.1479F, 4.1945F, -9.425F, 0.0094F, -1.1708F, -0.0071F));

        PartDefinition side_burn_l_r1 = head.addOrReplaceChild("side_burn_l_r1", CubeListBuilder.create().texOffs(28, 33).addBox(-5.0F, -11.0F, 4.0F, 2.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.1479F, 4.1945F, -9.425F, 0.0163F, 1.1708F, 0.0144F));

        PartDefinition upper_jaw = head.addOrReplaceChild("upper_jaw", CubeListBuilder.create().texOffs(0, 25).addBox(-2.0F, -12.0F, -6.0F, 4.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(46, 18).addBox(-2.0F, -10.01F, -6.0F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.1479F, 11.1945F, -9.425F));

        PartDefinition nose_r1 = upper_jaw.addOrReplaceChild("nose_r1", CubeListBuilder.create().texOffs(44, 33).addBox(-1.5F, -13.4F, -2.5F, 3.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3006F, 0.0F, 0.0F));

        PartDefinition ears = head.addOrReplaceChild("ears", CubeListBuilder.create(), PartPose.offset(-0.1479F, -6.8055F, -5.425F));

        PartDefinition r_ear_r1 = ears.addOrReplaceChild("r_ear_r1", CubeListBuilder.create().texOffs(22, 0).mirror().addBox(1.0F, -3.5F, 4.0F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -1.0F, -4.0F, 0.0F, 0.0F, 0.1279F));

        PartDefinition l_ear_r1 = ears.addOrReplaceChild("l_ear_r1", CubeListBuilder.create().texOffs(13, 14).mirror().addBox(-4.0F, -3.5F, 4.0F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -1.0F, -4.0F, 0.0F, 0.0F, -0.1279F));

        PartDefinition neck_base = head.addOrReplaceChild("neck_base", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.1479F, 5.1945F, -10.425F, -0.5236F, 0.0F, 0.0F));

        PartDefinition upper_hair_r1 = neck_base.addOrReplaceChild("upper_hair_r1", CubeListBuilder.create().texOffs(0, 14).addBox(-1.5F, 5.5F, -6.0F, 3.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -7.0F, 0.0F, -3.1337F, 0.0F, 0.0F));

        PartDefinition neck_r1 = neck_base.addOrReplaceChild("neck_r1", CubeListBuilder.create().texOffs(28, 0).addBox(-3.5F, -10.0F, -3.0F, 7.0F, 8.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 4.0F, 0.0541F, 0.0F, 0.0F));

        PartDefinition lower_jaw = head.addOrReplaceChild("lower_jaw", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.1479F, 6.1945F, -8.425F, 1.9635F, 0.0F, 0.0F));

        PartDefinition mouth_r1 = lower_jaw.addOrReplaceChild("mouth_r1", CubeListBuilder.create().texOffs(42, 69).addBox(-1.5F, -4.5F, 3.0F, 3.0F, 9.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(20, 109).addBox(-1.5F, -4.5F, 5.01F, 3.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0442F, 0.0F, 0.0F));

        PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, -1.0F, 2.5F));

        PartDefinition arms = body.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.offset(0.0F, -6.0F, 1.0F));

        PartDefinition right_arm = arms.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(4.0F, 0.1811F, -0.3015F));

        PartDefinition r_arm_b_r1 = right_arm.addOrReplaceChild("r_arm_b_r1", CubeListBuilder.create().texOffs(48, 77).addBox(1.0F, 3.0F, 2.5F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -4.5F, -0.0045F, 0.0008F, -0.1684F));

        PartDefinition r_arm_a_r1 = right_arm.addOrReplaceChild("r_arm_a_r1", CubeListBuilder.create().texOffs(0, 108).addBox(0.0F, -1.0F, 2.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -4.5F, -0.011F, 0.0F, 0.0F));

        PartDefinition r_hand = right_arm.addOrReplaceChild("r_hand", CubeListBuilder.create(), PartPose.offsetAndRotation(4.6375F, 8.048F, -0.0552F, 1.5708F, 1.3526F, -1.5708F));

        PartDefinition r_wrist_r1 = r_hand.addOrReplaceChild("r_wrist_r1", CubeListBuilder.create().texOffs(32, 118).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0021F, -7.5057F, -0.0121F, 3.1416F, 0.0F, 0.0F));

        PartDefinition r_arm_c_r1 = r_hand.addOrReplaceChild("r_arm_c_r1", CubeListBuilder.create().texOffs(48, 112).addBox(-2.0F, -3.5F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0021F, -2.4943F, 0.0121F, -0.0057F, -0.0021F, -3.1416F));

        PartDefinition r_fingers = r_hand.addOrReplaceChild("r_fingers", CubeListBuilder.create().texOffs(12, 119).addBox(-0.4932F, -2.2317F, -1.8442F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.4711F, -11.1374F, -0.1679F, 0.0F, 0.0F, -3.1416F));

        PartDefinition r_finger_5_r1 = r_fingers.addOrReplaceChild("r_finger_5_r1", CubeListBuilder.create().texOffs(16, 124).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.2068F, -0.7317F, 1.1558F, 0.0F, 1.5708F, 0.0F));

        PartDefinition r_finger_4_r1 = r_fingers.addOrReplaceChild("r_finger_4_r1", CubeListBuilder.create().texOffs(16, 119).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.2068F, -0.2317F, -0.3442F, 0.0F, -1.5708F, 0.0F));

        PartDefinition r_finger_2_r1 = r_fingers.addOrReplaceChild("r_finger_2_r1", CubeListBuilder.create().texOffs(12, 124).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.2932F, -0.7317F, -1.3442F, 0.0F, 1.5708F, 0.0F));

        PartDefinition r_finger_1_r1 = r_fingers.addOrReplaceChild("r_finger_1_r1", CubeListBuilder.create().texOffs(8, 120).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.7932F, -0.7317F, 1.1558F, 0.0F, -1.5708F, 0.0F));

        PartDefinition left_arm = arms.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(-4.0F, 0.1811F, -0.3015F));

        PartDefinition l_arm_b_r1 = left_arm.addOrReplaceChild("l_arm_b_r1", CubeListBuilder.create().texOffs(48, 89).addBox(-5.0F, 3.0F, 2.5F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -4.5F, -0.0045F, 0.0008F, 0.1684F));

        PartDefinition l_arm_a_r1 = left_arm.addOrReplaceChild("l_arm_a_r1", CubeListBuilder.create().texOffs(0, 98).addBox(-5.0F, -1.0F, 2.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -4.5F, -0.011F, 0.0F, 0.0F));

        PartDefinition l_hand = left_arm.addOrReplaceChild("l_hand", CubeListBuilder.create(), PartPose.offsetAndRotation(-4.6375F, 8.048F, -0.0552F, 1.5708F, -1.3526F, 1.5708F));

        PartDefinition l_wrist_r1 = l_hand.addOrReplaceChild("l_wrist_r1", CubeListBuilder.create().texOffs(32, 111).addBox(-1.5F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.4576F, -7.5077F, -0.0283F, 3.1416F, 0.0F, 0.0F));

        PartDefinition l_arm_c_r1 = l_hand.addOrReplaceChild("l_arm_c_r1", CubeListBuilder.create().texOffs(48, 101).addBox(-2.0F, -3.5F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0383F, -2.4963F, -0.0124F, -0.0057F, -0.0021F, -3.1416F));

        PartDefinition l_fingers = l_hand.addOrReplaceChild("l_fingers", CubeListBuilder.create().texOffs(0, 119).addBox(-0.5216F, -2.2305F, -1.8648F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5008F, -11.1382F, -0.1634F, 0.0F, 0.0F, -3.1416F));

        PartDefinition l_finger_5_r1 = l_fingers.addOrReplaceChild("l_finger_5_r1", CubeListBuilder.create().texOffs(4, 124).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.2216F, -0.7305F, 1.1352F, 0.0F, -1.5708F, 0.0F));

        PartDefinition l_finger_4_r1 = l_fingers.addOrReplaceChild("l_finger_4_r1", CubeListBuilder.create().texOffs(4, 119).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.2216F, -0.2305F, -0.3648F, 0.0F, 1.5708F, 0.0F));

        PartDefinition l_finger_2_r1 = l_fingers.addOrReplaceChild("l_finger_2_r1", CubeListBuilder.create().texOffs(0, 124).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.2784F, -0.7305F, -1.3648F, 0.0F, -1.5708F, 0.0F));

        PartDefinition l_finger_1_r1 = l_fingers.addOrReplaceChild("l_finger_1_r1", CubeListBuilder.create().texOffs(8, 124).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.7784F, -0.7305F, 1.1352F, 0.0F, 1.5708F, 0.0F));

        PartDefinition torax = body.addOrReplaceChild("torax", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 3.0F, 2.5F, 0.0436F, 0.0F, 0.0F));

        PartDefinition entrepierna_r1 = torax.addOrReplaceChild("entrepierna_r1", CubeListBuilder.create().texOffs(52, 42).addBox(-1.5F, -0.5F, -13.0F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, -6.9869F, 0.6511F, 1.1595F, 0.0F, 0.0F));

        PartDefinition abdomen_r1 = torax.addOrReplaceChild("abdomen_r1", CubeListBuilder.create().texOffs(0, 40).addBox(-3.0F, -5.5F, -4.0F, 6.0F, 11.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, -0.9893F, -0.863F, -0.0047F, 0.0F, 0.0F));

        PartDefinition chest = torax.addOrReplaceChild("chest", CubeListBuilder.create(), PartPose.offset(-0.0881F, -7.6054F, -2.1993F));

        PartDefinition body_base_r1 = chest.addOrReplaceChild("body_base_r1", CubeListBuilder.create().texOffs(20, 15).addBox(-4.0F, -4.0F, -5.0F, 8.0F, 8.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3815F, 0.0F, 0.0F));

        PartDefinition tail = torax.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offset(0.0F, 5.0F, 3.0F));

        PartDefinition tail_d_r1 = tail.addOrReplaceChild("tail_d_r1", CubeListBuilder.create().texOffs(52, 69).addBox(-1.5F, 11.7F, -11.6F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, -13.9869F, -2.3489F, 1.028F, 0.0F, 0.0F));

        PartDefinition tail_c_r1 = tail.addOrReplaceChild("tail_c_r1", CubeListBuilder.create().texOffs(48, 59).addBox(-2.0F, 15.7F, -10.6F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, -13.9869F, -2.3489F, 0.9468F, 0.0F, 0.0F));

        PartDefinition tail_a_r1 = tail.addOrReplaceChild("tail_a_r1", CubeListBuilder.create().texOffs(52, 42).addBox(-1.5F, 6.5F, -13.0F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, -13.9869F, -2.3489F, 1.1595F, 0.0F, 0.0F));

        PartDefinition legs = partdefinition.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.5F, 5.0F, 0.1309F, 0.0F, 0.0F));

        PartDefinition right_leg = legs.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition r_leg_a_r1 = right_leg.addOrReplaceChild("r_leg_a_r1", CubeListBuilder.create().texOffs(12, 64).addBox(0.5F, -8.0F, -8.5F, 3.0F, 12.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 9.5F, -2.0F, -0.9222F, -0.2296F, -0.2032F));

        PartDefinition r_leg_c_bone = right_leg.addOrReplaceChild("r_leg_c_bone", CubeListBuilder.create(), PartPose.offset(5.4573F, 6.4935F, -6.9164F));

        PartDefinition r_leg_c_r1 = r_leg_c_bone.addOrReplaceChild("r_leg_c_r1", CubeListBuilder.create().texOffs(14, 83).addBox(3.4046F, 7.3F, -2.5F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.9096F, -5.0447F, -2.5901F, 0.6595F, -0.2296F, -0.2032F));

        PartDefinition r_leg_b_bone = r_leg_c_bone.addOrReplaceChild("r_leg_b_bone", CubeListBuilder.create(), PartPose.offset(-5.4573F, -6.4935F, 6.9164F));

        PartDefinition r_leg_b_r1 = r_leg_b_bone.addOrReplaceChild("r_leg_b_r1", CubeListBuilder.create().texOffs(14, 76).addBox(1.15F, 1.3F, -6.5F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 9.5F, -2.0F, 0.8874F, 0.0F, 0.0F));

        PartDefinition r_foot_bone = r_leg_b_bone.addOrReplaceChild("r_foot_bone", CubeListBuilder.create(), PartPose.offset(5.6415F, 16.0429F, -5.6638F));

        PartDefinition r_foot_r1 = r_foot_bone.addOrReplaceChild("r_foot_r1", CubeListBuilder.create().texOffs(14, 93).addBox(-1.5F, -1.25F, -3.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0445F, 0.5262F, 1.9452F, -0.1399F, 0.0213F, -0.0058F));

        PartDefinition left_leg = legs.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition l_leg_a_r1 = left_leg.addOrReplaceChild("l_leg_a_r1", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, -8.0F, -8.5F, 3.0F, 12.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 9.5F, -2.0F, -0.9222F, 0.2296F, 0.2032F));

        PartDefinition l_leg_c_bone = left_leg.addOrReplaceChild("l_leg_c_bone", CubeListBuilder.create(), PartPose.offset(-5.4573F, 6.4935F, -6.9164F));

        PartDefinition l_leg_c_r1 = l_leg_c_bone.addOrReplaceChild("l_leg_c_r1", CubeListBuilder.create().texOffs(0, 83).addBox(-5.4046F, 7.2999F, -2.5F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.9096F, -5.0447F, -2.5901F, 0.6595F, 0.2296F, 0.2032F));

        PartDefinition l_leg_b_bone = l_leg_c_bone.addOrReplaceChild("l_leg_b_bone", CubeListBuilder.create(), PartPose.offset(5.4573F, -6.4935F, 6.9164F));

        PartDefinition l_leg_b_r1 = l_leg_b_bone.addOrReplaceChild("l_leg_b_r1", CubeListBuilder.create().texOffs(0, 76).addBox(-4.15F, 1.3F, -6.5F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 9.5F, -2.0F, 0.8874F, 0.0F, 0.0F));

        PartDefinition l_foot_bone = l_leg_b_bone.addOrReplaceChild("l_foot_bone", CubeListBuilder.create(), PartPose.offset(-5.65F, 16.0395F, -5.6898F));

        PartDefinition l_foot_r1 = l_foot_bone.addOrReplaceChild("l_foot_r1", CubeListBuilder.create().texOffs(0, 93).addBox(-1.25F, -1.25F, -2.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, 0.3784F, 0.9588F, -0.1309F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    public List<ModelPart> getParts() {
        return parts;
    }

    @Override
    public void setupAnim(@NotNull T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        Player player = (Player) entity;
        setupAnimationStates(entity, (int) ageInTicks, player);
        getParts().forEach(ModelPart::resetPose);
        float f = (float) entity.getDeltaMovement().horizontalDistanceSqr();
        float f1 = Mth.clamp(f * 400.0F, 0.2F, 2.0F);

        this.animateHeadLookTarget(netHeadYaw, headPitch);
        animateIdlePose(ageInTicks);
        this.animate(this.walkAnimationState, WerewolfRendererAnimation.MODEL_WALK, ageInTicks, f1);
        this.animate(this.crouchAnimationState, WerewolfRendererAnimation.MODEL_CROUCH, ageInTicks);
        this.animate(this.swimAnimationState, WerewolfRendererAnimation.MODEL_SWIM, ageInTicks, swimAmount * 2.0f);
        this.animate(this.leftAttackAnimationState, WerewolfRendererAnimation.MODEL_ATTACK_LEFT, ageInTicks, 1.0f);
        this.animate(this.rightAttackAnimationState, WerewolfRendererAnimation.MODEL_ATTACK_RIGHT, ageInTicks, 1.0f);

        //setupAttackAnimation(player,ageInTicks);
    }

    private void setupAnimationStates(T entity, int ageInTicks, Player player) {
        switch (player.getPose()) {
            case CROUCHING, STANDING -> {
                this.swimAnimationState.stop();
                this.crouchAnimationState.animateWhen(player.isCrouching(), ageInTicks);
                this.walkAnimationState.animateWhen(entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3D, ageInTicks);
                this.leftAttackAnimationState.animateWhen(player.swingingArm == InteractionHand.MAIN_HAND && !(this.attackTime <= 0.0F), ageInTicks);
                this.rightAttackAnimationState.animateWhen(player.swingingArm == InteractionHand.OFF_HAND && !(this.attackTime <= 0.0F),  ageInTicks);
            }
            case SWIMMING ->
                    this.swimAnimationState.animateWhen(player.isSwimming() && player.isVisuallySwimming() && swimAmount > 0.0f, ageInTicks);
            default -> {
                this.walkAnimationState.stop();
                this.leftAttackAnimationState.stop();
                this.rightAttackAnimationState.stop();
                this.crouchAnimationState.stop();
                this.swimAnimationState.stop();
            }
        }
    }

    private HumanoidArm getAttackArm(Player player) {
        HumanoidArm humanoidarm = player.getMainArm();
        return player.swingingArm == InteractionHand.MAIN_HAND ? humanoidarm : humanoidarm.getOpposite();
    }

    public void prepareMobModel(@NotNull T p_102861_, float p_102862_, float p_102863_, float p_102864_) {
        this.swimAmount = ((Player) p_102861_).getSwimAmount(p_102864_);
        super.prepareMobModel(p_102861_, p_102862_, p_102863_, p_102864_);
    }

    private void animateHeadLookTarget(float p_233517_, float p_233518_) {
        this.head.xRot = p_233518_ * ((float) Math.PI / 180F);
        this.head.yRot = p_233517_ * ((float) Math.PI / 180F);
    }

    private void animateIdlePose(float p_233515_) {
        float f = p_233515_ * 0.1F;
        float f1 = Mth.cos(f);
        float f2 = Mth.sin(f);
        this.head.zRot += 0.06F * f1;
        this.head.xRot += 0.06F * f2;
        this.body.zRot += 0.025F * f2;
        this.body.xRot += 0.025F * f1;
    }

    private void poseRightArm(Player player) {
        switch (this.rightArmPose) {
            case EMPTY -> this.rightArm.yRot = 0.0F;
            case BLOCK -> {
                this.rightArm.xRot = this.rightArm.xRot * 0.5F - 0.9424779F;
                this.rightArm.yRot = (-(float) Math.PI / 6F);
            }
            case ITEM -> {
                this.rightArm.xRot = this.rightArm.xRot * 0.5F - ((float) Math.PI / 10F);
                this.rightArm.yRot = 0.0F;
            }
            case THROW_SPEAR -> {
                this.rightArm.xRot = this.rightArm.xRot * 0.5F - (float) Math.PI;
                this.rightArm.yRot = 0.0F;
            }
            case BOW_AND_ARROW -> {
                this.rightArm.yRot = -0.1F + this.head.yRot;
                this.leftArm.yRot = 0.1F + this.head.yRot + 0.4F;
                this.rightArm.xRot = (-(float) Math.PI / 2F) + this.head.xRot;
                this.leftArm.xRot = (-(float) Math.PI / 2F) + this.head.xRot;
            }
            case CROSSBOW_CHARGE -> AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, player, true);
            case CROSSBOW_HOLD -> AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, true);
            case SPYGLASS -> {
                this.rightArm.xRot = Mth.clamp(this.head.xRot - 1.9198622F - (player.isCrouching() ? 0.2617994F : 0.0F), -2.4F, 3.3F);
                this.rightArm.yRot = this.head.yRot - 0.2617994F;
            }
            case TOOT_HORN -> {
                this.rightArm.xRot = Mth.clamp(this.head.xRot, -1.2F, 1.2F) - 1.4835298F;
                this.rightArm.yRot = this.head.yRot - ((float) Math.PI / 6F);
            }
            //default:
            //this.rightArmPose.applyTransform(this, player, net.minecraft.world.entity.HumanoidArm.RIGHT);
        }

    }

    private void poseLeftArm(Player player) {
        switch (this.leftArmPose) {
            case EMPTY -> this.leftArm.yRot = 0.0F;
            case BLOCK -> {
                this.leftArm.xRot = this.leftArm.xRot * 0.5F - 0.9424779F;
                this.leftArm.yRot = ((float) Math.PI / 6F);
            }
            case ITEM -> {
                this.leftArm.xRot = this.leftArm.xRot * 0.5F - ((float) Math.PI / 10F);
                this.leftArm.yRot = 0.0F;
            }
            case THROW_SPEAR -> {
                this.leftArm.xRot = this.leftArm.xRot * 0.5F - (float) Math.PI;
                this.leftArm.yRot = 0.0F;
            }
            case BOW_AND_ARROW -> {
                this.rightArm.yRot = -0.1F + this.head.yRot - 0.4F;
                this.leftArm.yRot = 0.1F + this.head.yRot;
                this.rightArm.xRot = (-(float) Math.PI / 2F) + this.head.xRot;
                this.leftArm.xRot = (-(float) Math.PI / 2F) + this.head.xRot;
            }
            case CROSSBOW_CHARGE -> AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, player, false);
            case CROSSBOW_HOLD -> AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, false);
            case SPYGLASS -> {
                this.leftArm.xRot = Mth.clamp(this.head.xRot - 1.9198622F - (player.isCrouching() ? 0.2617994F : 0.0F), -2.4F, 3.3F);
                this.leftArm.yRot = this.head.yRot + 0.2617994F;
            }
            case TOOT_HORN -> {
                this.leftArm.xRot = Mth.clamp(this.head.xRot, -1.2F, 1.2F) - 1.4835298F;
                this.leftArm.yRot = this.head.yRot + ((float) Math.PI / 6F);
            }
            //default:
            //this.leftArmPose.applyTransform(this, player, net.minecraft.world.entity.HumanoidArm.LEFT);
        }
    }

    protected float rotlerpRad(float p_102836_, float p_102837_, float p_102838_) {
        float f = (p_102838_ - p_102837_) % ((float) Math.PI * 2F);
        if (f < -(float) Math.PI) {
            f += ((float) Math.PI * 2F);
        }

        if (f >= (float) Math.PI) {
            f -= ((float) Math.PI * 2F);
        }

        return p_102837_ + p_102836_ * f;
    }

    public @NotNull ModelPart getHead() {
        return this.head;
    }

    @Override
    public void renderToBuffer(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        legs.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public @NotNull ModelPart root() {
        return root;
    }

    public ModelPart getRandomModelPart(RandomSource p_233439_) {
        //return this.parts.get(p_233439_.nextInt(this.parts.size()));
        List<ModelPart> list = List.of(head, body, legs);
        return list.get(p_233439_.nextInt(list.size()));
    }

    public void translateToHand(@NotNull HumanoidArm p_102854_, @NotNull PoseStack p_102855_) {
        this.getArm(p_102854_).translateAndRotate(p_102855_);
    }

    protected ModelPart getArm(HumanoidArm p_102852_) {
        return p_102852_ == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
        //return p_102852_ == HumanoidArm.RIGHT ? this.leftWrist : this.rightWrist;
    }
}
