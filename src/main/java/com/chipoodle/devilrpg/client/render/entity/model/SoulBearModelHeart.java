package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulBear;
import com.google.common.collect.ImmutableList;

import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulBearModelHeart<T extends SoulBear> extends AgeableListModel<T> {

	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "soulbearheart"), "main");

	private final ModelPart head;
	private final ModelPart heart;
	private final ModelPart leftEye;
	private final ModelPart rightEye;


	public SoulBearModelHeart(ModelPart model) {
		//super(model, true, 16.0F, 4.0F, 2.25F, 2.0F, 24);
		head = model.getChild("head");
		heart = model.getChild("heart");
		leftEye = head.getChild("left_eye");
		rightEye = head.getChild("right_eye");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();


		PartDefinition partdefinition1 = partdefinition.addOrReplaceChild("head",
				CubeListBuilder.create().texOffs(0, 0),
				PartPose.offset(0.0F, 10.0F, -16.0F));

		partdefinition1.addOrReplaceChild("right_eye",
				CubeListBuilder
						.create()
						.texOffs(32, 0)
						.addBox(-2.0F, -0.25F, -3.5F, 1.0F, 0.75F, 0.5F),
				PartPose.ZERO);

		partdefinition1.addOrReplaceChild("left_eye",
				CubeListBuilder
						.create()
						.texOffs(32, 4)
						.addBox(1.0F, -0.25F, -3.5F, 1.0F, 0.75F, 0.5F),
				PartPose.ZERO);
		partdefinition.addOrReplaceChild("heart",
				CubeListBuilder.create().texOffs(39, 0)
						.addBox(-1.0F, -23.0F, -4.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offset(-2.0F, 9.0F, 12.0F));


		return LayerDefinition.create(meshdefinition, 128, 64);
	}

	protected Iterable<ModelPart> headParts() {
		return ImmutableList.of(this.head);
	}

	protected Iterable<ModelPart> bodyParts() {return ImmutableList.of(this.heart);
	}
	public void prepareMobModel(T p_104132_, float p_104133_, float p_104134_, float p_104135_) {

	}


	public void setupAnim(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks,float netHeadYaw, float headPitch) {
		//super.setupAnim(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		float f = ageInTicks - (float) entityIn.tickCount;
		float f1 = entityIn.getStandingAnimationScale(f);
		f1 = f1 * f1;
		float f2 = 1.0F - f1;
		this.heart.xRot = ((float) Math.PI / 2F) - f1 * (float) Math.PI * 0.35F;
		this.heart.y = 9.0F * f2 + 11.0F * f1;
		if (entityIn.isBaby()) {
			this.head.y = 10.0F * f2 - 9.0F * f1;
			this.head.z = -16.0F * f2 - 7.0F * f1;
		} else {
			this.head.y = 10.0F * f2 - 14.0F * f1;
			this.head.z = -16.0F * f2 - 3.0F * f1;
		}

		this.head.xRot += f1 * (float) Math.PI * 0.15F;
		
		this.head.xRot = headPitch * ((float) Math.PI / 180F);
		this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
	}
}