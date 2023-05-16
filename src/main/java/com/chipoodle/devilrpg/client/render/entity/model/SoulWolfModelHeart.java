package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulWolf;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.ColorableAgeableListModel;
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
public class SoulWolfModelHeart<T extends SoulWolf> extends ColorableAgeableListModel<T> {

	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(DevilRpg.MODID, "soulwolfheart"), "main");

	private final ModelPart heart;
	private final ModelPart head;
	private final ModelPart leftEye;
	private final ModelPart rightEye;

    public SoulWolfModelHeart(ModelPart model) {
		head = model.getChild("head");
		leftEye = head.getChild("left_eye");
		rightEye = head.getChild("right_eye");
		heart = model.getChild("heart");

	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition partdefinition1 = partdefinition
				.addOrReplaceChild("head",
						CubeListBuilder.create(),
						PartPose.offset(-1.0F, 13.5F, -7.0F));
		partdefinition1.addOrReplaceChild("left_eye",
				CubeListBuilder
						.create()
						.texOffs(32, 4)
						.addBox(1.75F, -1.0F, -2.5F, 1.0F, 0.75F, 0.5F),
				PartPose.ZERO);
		partdefinition1.addOrReplaceChild("right_eye",
				CubeListBuilder
						.create()
						.texOffs(32, 0)
						.addBox(-0.75F, -1.0F, -2.5F, 1.0F, 0.75F, 0.5F),
				PartPose.ZERO);
		partdefinition.addOrReplaceChild("heart",
				CubeListBuilder.create().texOffs(21, 0)
						.addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, 14.0F, -2.0F, ((float)Math.PI / 2F), 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 32);
	}

	protected Iterable<ModelPart> headParts() {
		return ImmutableList.of(this.head);
	}

	protected Iterable<ModelPart> bodyParts() {
		return ImmutableList.of(this.heart);
	}
	public void prepareMobModel(T entityIn, float p_104133_, float p_104134_, float partialTick) {
		this.heart.zRot = entityIn.getBodyRollAngle(partialTick, -0.16F);
	}

	public void setupAnim(T p_104137_, float p_104138_, float p_104139_, float p_104140_, float p_104141_, float p_104142_) {
		this.head.xRot = p_104142_ * ((float)Math.PI / 180F);
		this.head.yRot = p_104141_ * ((float)Math.PI / 180F);
	}
}