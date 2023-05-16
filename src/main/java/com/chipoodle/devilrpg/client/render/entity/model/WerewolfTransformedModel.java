package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.DevilRpg;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WerewolfTransformedModel<T extends Entity> extends EntityModel<T> {
    // This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
    public static final ModelLayerLocation WEREWOLF_LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "werewolftransformedlayer"), "main");
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart legs;

    public WerewolfTransformedModel(ModelPart root) {
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.legs = root.getChild("legs");
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

        PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, -4.0F, -6.0F));

        PartDefinition arms = body.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.offset(0.0F, -4.0F, 3.0F));

        PartDefinition right_arm = arms.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(4.0F, 1.0F, 5.5F));

        PartDefinition r_arm_c_r1 = right_arm.addOrReplaceChild("r_arm_c_r1", CubeListBuilder.create().texOffs(48, 112).addBox(3.5F, 9.0F, 2.5F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -4.5F, 0.0057F, 0.0021F, 0.0F));

        PartDefinition r_arm_b_r1 = right_arm.addOrReplaceChild("r_arm_b_r1", CubeListBuilder.create().texOffs(48, 77).addBox(0.0F, 4.0F, 2.5F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -4.5F, -0.0046F, -0.0004F, -0.343F));

        PartDefinition r_arm_a_r1 = right_arm.addOrReplaceChild("r_arm_a_r1", CubeListBuilder.create().texOffs(0, 108).addBox(0.0F, -1.0F, 2.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -4.5F, -0.011F, 0.0F, 0.0F));

        PartDefinition r_hand = right_arm.addOrReplaceChild("r_hand", CubeListBuilder.create(), PartPose.offsetAndRotation(6.05F, 19.5833F, 0.25F, 0.0F, 1.5708F, -3.1416F));

        PartDefinition r_wrist_r1 = r_hand.addOrReplaceChild("r_wrist_r1", CubeListBuilder.create().texOffs(32, 118).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.1741F, 3.0979F, 0.5446F, 3.1416F, 0.0F, 0.0F));

        PartDefinition r_fingers = r_hand.addOrReplaceChild("r_fingers", CubeListBuilder.create().texOffs(12, 119).addBox(-0.36F, -2.3F, -1.7F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.1659F, -0.6021F, 0.2446F, 0.0F, 0.0F, -3.1416F));

        PartDefinition r_finger_5_r1 = r_fingers.addOrReplaceChild("r_finger_5_r1", CubeListBuilder.create().texOffs(16, 124).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.34F, -0.8F, 1.3F, 0.0F, 1.5708F, 0.0F));

        PartDefinition r_finger_4_r1 = r_fingers.addOrReplaceChild("r_finger_4_r1", CubeListBuilder.create().texOffs(16, 119).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.34F, -0.3F, -0.2F, 0.0F, -1.5708F, 0.0F));

        PartDefinition r_finger_2_r1 = r_fingers.addOrReplaceChild("r_finger_2_r1", CubeListBuilder.create().texOffs(12, 124).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.16F, -0.8F, -1.2F, 0.0F, 1.5708F, 0.0F));

        PartDefinition r_finger_1_r1 = r_fingers.addOrReplaceChild("r_finger_1_r1", CubeListBuilder.create().texOffs(8, 120).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.66F, -0.8F, 1.3F, 0.0F, -1.5708F, 0.0F));

        PartDefinition left_arm = arms.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(-4.0F, 1.0F, 5.5F));

        PartDefinition l_arm_c_r1 = left_arm.addOrReplaceChild("l_arm_c_r1", CubeListBuilder.create().texOffs(48, 101).addBox(-7.5F, 9.0F, 2.5F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -4.5F, 0.006F, 0.0008F, 0.0F));

        PartDefinition l_arm_b_r1 = left_arm.addOrReplaceChild("l_arm_b_r1", CubeListBuilder.create().texOffs(48, 89).addBox(-4.0F, 4.0F, 2.5F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -4.5F, -0.0043F, 0.0016F, 0.343F));

        PartDefinition l_arm_a_r1 = left_arm.addOrReplaceChild("l_arm_a_r1", CubeListBuilder.create().texOffs(0, 98).addBox(-5.0F, -1.0F, 2.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -4.5F, -0.011F, 0.0F, 0.0F));

        PartDefinition l_hand = left_arm.addOrReplaceChild("l_hand", CubeListBuilder.create(), PartPose.offsetAndRotation(-6.05F, 19.5833F, 0.25F, 0.0F, -1.5708F, 3.1416F));

        PartDefinition l_wrist_r1 = l_hand.addOrReplaceChild("l_wrist_r1", CubeListBuilder.create().texOffs(32, 111).addBox(-1.5F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.6474F, 3.0987F, 0.5521F, 3.1416F, 0.0F, 0.0F));

        PartDefinition l_fingers = l_hand.addOrReplaceChild("l_fingers", CubeListBuilder.create().texOffs(0, 119).addBox(-0.64F, -2.3F, -1.7F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.1926F, -0.6013F, 0.2521F, 0.0F, 0.0F, -3.1416F));

        PartDefinition l_finger_5_r1 = l_fingers.addOrReplaceChild("l_finger_5_r1", CubeListBuilder.create().texOffs(4, 124).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.34F, -0.8F, 1.3F, 0.0F, -1.5708F, 0.0F));

        PartDefinition l_finger_4_r1 = l_fingers.addOrReplaceChild("l_finger_4_r1", CubeListBuilder.create().texOffs(4, 119).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.34F, -0.3F, -0.2F, 0.0F, 1.5708F, 0.0F));

        PartDefinition l_finger_2_r1 = l_fingers.addOrReplaceChild("l_finger_2_r1", CubeListBuilder.create().texOffs(0, 124).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.16F, -0.8F, -1.2F, 0.0F, -1.5708F, 0.0F));

        PartDefinition l_finger_1_r1 = l_fingers.addOrReplaceChild("l_finger_1_r1", CubeListBuilder.create().texOffs(8, 124).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.66F, -0.8F, 1.3F, 0.0F, 1.5708F, 0.0F));

        PartDefinition torax = body.addOrReplaceChild("torax", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.0F, 11.0F, 0.0436F, 0.0F, 0.0F));

        PartDefinition entrepierna_r1 = torax.addOrReplaceChild("entrepierna_r1", CubeListBuilder.create().texOffs(52, 42).addBox(-1.5F, -0.5F, -13.0F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, -6.9869F, 0.6511F, 1.1595F, 0.0F, 0.0F));

        PartDefinition abdomen_r1 = torax.addOrReplaceChild("abdomen_r1", CubeListBuilder.create().texOffs(0, 40).addBox(-3.0F, -2.5F, -4.5F, 6.0F, 11.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, -3.9869F, -0.3489F, -0.0047F, 0.0F, 0.0F));

        PartDefinition body_base_r1 = torax.addOrReplaceChild("body_base_r1", CubeListBuilder.create().texOffs(20, 15).addBox(-4.0F, -15.0F, 5.5F, 8.0F, 8.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, 6.5131F, -7.8489F, 0.3815F, 0.0F, 0.0F));

        PartDefinition tail = torax.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offset(0.0F, 5.0F, 3.0F));

        PartDefinition tail_d_r1 = tail.addOrReplaceChild("tail_d_r1", CubeListBuilder.create().texOffs(52, 69).addBox(-1.5F, 11.7F, -11.6F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, -13.9869F, -2.3489F, 1.028F, 0.0F, 0.0F));

        PartDefinition tail_c_r1 = tail.addOrReplaceChild("tail_c_r1", CubeListBuilder.create().texOffs(48, 59).addBox(-2.0F, 15.7F, -10.6F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, -13.9869F, -2.3489F, 0.9468F, 0.0F, 0.0F));

        PartDefinition tail_a_r1 = tail.addOrReplaceChild("tail_a_r1", CubeListBuilder.create().texOffs(52, 42).addBox(-1.5F, 6.5F, -13.0F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0881F, -13.9869F, -2.3489F, 1.1595F, 0.0F, 0.0F));

        PartDefinition legs = partdefinition.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.5F, 5.0F, 0.1309F, 0.0F, 0.0F));

        PartDefinition right_leg = legs.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition r_leg_c_r1 = right_leg.addOrReplaceChild("r_leg_c_r1", CubeListBuilder.create().texOffs(14, 83).addBox(3.4046F, 7.3F, -2.5F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5477F, 1.4488F, -9.5065F, 0.6595F, -0.2296F, -0.2032F));

        PartDefinition r_leg_b_r1 = right_leg.addOrReplaceChild("r_leg_b_r1", CubeListBuilder.create().texOffs(14, 76).addBox(1.15F, 1.3F, -6.5F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 9.5F, -2.0F, 0.8874F, 0.0F, 0.0F));

        PartDefinition r_foot_r1 = right_leg.addOrReplaceChild("r_foot_r1", CubeListBuilder.create().texOffs(14, 93).addBox(-1.5F, -1.25F, -3.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.686F, 16.5691F, -3.7187F, -0.1399F, 0.0213F, -0.0058F));

        PartDefinition r_leg_a_r1 = right_leg.addOrReplaceChild("r_leg_a_r1", CubeListBuilder.create().texOffs(12, 64).addBox(0.5F, -8.0F, -8.5F, 3.0F, 12.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 9.5F, -2.0F, -0.9222F, -0.2296F, -0.2032F));

        PartDefinition left_leg = legs.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition l_leg_a_r1 = left_leg.addOrReplaceChild("l_leg_a_r1", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, -8.0F, -8.5F, 3.0F, 12.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 9.5F, -2.0F, -0.9222F, 0.2296F, 0.2032F));

        PartDefinition l_leg_c_r1 = left_leg.addOrReplaceChild("l_leg_c_r1", CubeListBuilder.create().texOffs(0, 83).addBox(-5.4046F, 7.2999F, -2.5F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5477F, 1.4488F, -9.5065F, 0.6595F, 0.2296F, 0.2032F));

        PartDefinition l_foot_r1 = left_leg.addOrReplaceChild("l_foot_r1", CubeListBuilder.create().texOffs(0, 93).addBox(-1.25F, -1.25F, -2.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.9F, 16.4179F, -4.731F, -0.1309F, 0.0F, 0.0F));

        PartDefinition l_leg_b_r1 = left_leg.addOrReplaceChild("l_leg_b_r1", CubeListBuilder.create().texOffs(0, 76).addBox(-4.15F, 1.3F, -6.5F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 9.5F, -2.0F, 0.8874F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        legs.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
