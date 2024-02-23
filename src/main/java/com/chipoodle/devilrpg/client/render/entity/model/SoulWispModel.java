package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulWisp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class SoulWispModel<T extends SoulWisp> extends HierarchicalModel<T> implements ArmedModel {

    public static final ModelLayerLocation DEFAULT_LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "soulwisp"), "main");
    public static final ModelLayerLocation ARCHER_LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "soulwisparcher"), "main");
    public static final ModelLayerLocation BOMBER_LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "soulwispbomber"), "main");
    public static final ModelLayerLocation CURSE_LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "soulwispcurse"), "main");
    public static final ModelLayerLocation HEALTH_LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "soulwisphealth"), "main");
    private static final float FLYING_ANIMATION_X_ROT = ((float) Math.PI / 4F);
    private static final float MAX_HAND_HOLDING_ITEM_X_ROT_RAD = -1.134464F;
    private static final float MIN_HAND_HOLDING_ITEM_X_ROT_RAD = (-(float) Math.PI / 3F);
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart right_arm;
    private final ModelPart left_arm;
    private final ModelPart right_wing;
    private final ModelPart left_wing;

    public SoulWispModel(ModelPart p_233312_) {
        super(RenderType::entityTranslucent);
        this.root = p_233312_.getChild("root");
        this.head = this.root.getChild("head");
        this.body = this.root.getChild("body");
        this.right_arm = this.body.getChild("right_arm");
        this.left_arm = this.body.getChild("left_arm");
        this.right_wing = this.body.getChild("right_wing");
        this.left_wing = this.body.getChild("left_wing");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition partdefinition1 = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 23.5F, 0.0F));
        partdefinition1.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -5.0F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.99F, 0.0F));
        PartDefinition partdefinition2 = partdefinition1.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 10).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(0, 16).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 5.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offset(0.0F, -4.0F, 0.0F));
        partdefinition2.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(23, 0).addBox(-0.75F, -0.5F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offset(-1.75F, 0.5F, 0.0F));
        partdefinition2.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(23, 6).addBox(-0.25F, -0.5F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offset(1.75F, 0.5F, 0.0F));
        partdefinition2.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(16, 14).addBox(0.0F, 1.0F, 0.0F, 0.0F, 5.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 0.0F, 0.6F));
        partdefinition2.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(16, 14).addBox(0.0F, 1.0F, 0.0F, 0.0F, 5.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 0.0F, 0.6F));
        return LayerDefinition.create(meshdefinition, 32, 32);
    }

    public ModelPart root() {
        return this.root;
    }

    public void setupAnim(SoulWisp p_233325_, float p_233326_, float p_233327_, float p_233328_, float p_233329_, float p_233330_) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        float f = p_233328_ * 20.0F * ((float) Math.PI / 180F) + p_233326_;
        float f1 = Mth.cos(f) * (float) Math.PI * 0.15F + p_233327_;
        float f2 = p_233328_ - (float) p_233325_.tickCount;
        float f3 = p_233328_ * 9.0F * ((float) Math.PI / 180F);
        float f4 = Math.min(p_233327_ / 0.3F, 1.0F);
        float f5 = 1.0F - f4;
        float f6 = p_233325_.getHoldingItemAnimationProgress(f2);
        if (p_233325_.isDancing()) {
            float f7 = p_233328_ * 8.0F * ((float) Math.PI / 180F) + p_233327_;
            float f8 = Mth.cos(f7) * 16.0F * ((float) Math.PI / 180F);
            float f9 = p_233325_.getSpinningProgress(f2);
            float f10 = Mth.cos(f7) * 14.0F * ((float) Math.PI / 180F);
            float f11 = Mth.cos(f7) * 30.0F * ((float) Math.PI / 180F);
            this.root.yRot = p_233325_.isSpinning() ? 12.566371F * f9 : this.root.yRot;
            this.root.zRot = f8 * (1.0F - f9);
            this.head.yRot = f11 * (1.0F - f9);
            this.head.zRot = f10 * (1.0F - f9);
        } else {
            this.head.xRot = p_233330_ * ((float) Math.PI / 180F);
            this.head.yRot = p_233329_ * ((float) Math.PI / 180F);
        }

        this.right_wing.xRot = 0.43633232F * (1.0F - f4);
        this.right_wing.yRot = (-(float) Math.PI / 4F) + f1;
        this.left_wing.xRot = 0.43633232F * (1.0F - f4);
        this.left_wing.yRot = ((float) Math.PI / 4F) - f1;
        this.body.xRot = f4 * ((float) Math.PI / 4F);
        float f12 = f6 * Mth.lerp(f4, (-(float) Math.PI / 3F), -1.134464F);
        this.root.y += (float) Math.cos(f3) * 0.25F * f5;
        this.right_arm.xRot = f12;
        this.left_arm.xRot = f12;
        float f13 = f5 * (1.0F - f6);
        float f14 = 0.43633232F - Mth.cos(f3 + ((float) Math.PI * 1.5F)) * (float) Math.PI * 0.075F * f13;
        this.left_arm.zRot = -f14;
        this.right_arm.zRot = f14;
        this.right_arm.yRot = 0.27925268F * f6;
        this.left_arm.yRot = -0.27925268F * f6;
    }

    public void translateToHand(@NotNull HumanoidArm p_233322_, @NotNull PoseStack p_233323_) {
        float f = 1.0F;
        float f1 = 3.0F;
        this.root.translateAndRotate(p_233323_);
        this.body.translateAndRotate(p_233323_);
        p_233323_.translate(0.0F, 0.0625F, 0.1875F);
        p_233323_.mulPose(Axis.XP.rotation(this.right_arm.xRot));
        p_233323_.scale(0.7F, 0.7F, 0.7F);
        p_233323_.translate(0.0625F, 0.0F, 0.0F);
    }
}
