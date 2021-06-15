package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.google.common.collect.ImmutableList;

import net.minecraft.client.renderer.entity.model.AgeableModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WingsModel<T extends SoulWispEntity> extends AgeableModel<T> {
	private final ModelRenderer body;
	//private final ModelRenderer torso;
	private final ModelRenderer ala1;
	private final ModelRenderer ala2;

	public WingsModel() {
		super(false, 24.0F, 0.0F);
		this.texWidth = 64;
		this.texHeight = 64;
		this.body = new ModelRenderer(this);
		//this.body.setPos(0.0F, 19.0F, 0.0F);
		//this.torso = new ModelRenderer(this, 0, 0);
		//this.torso.setPos(0.0F, 0.0F, 0.0F);
		//this.body.addChild(this.torso);

		//this.torso.addBox(-3.5F, -4.0F, -5.0F, 7.0F, 7.0F, 10.0F, 0.0F);
		float zWingPosition= -9.0f;
		float yWingPosition= 3.9f;
		
		this.ala1 = new ModelRenderer(this, 0, 18);
		this.ala1.setPos(-1.5F, -3.0F, -4.0F);
		this.ala1.xRot = 0.0F;
		this.ala1.yRot = 0.2618F;
		this.ala1.zRot = 0.0F;
		this.body.addChild(this.ala1);
		this.ala1.addBox(-9.0F,yWingPosition, zWingPosition, 9.0F, 0.0F, 6.0F, 0.001F);

		this.ala2 = new ModelRenderer(this, 0, 18);
		this.ala2.setPos(1.5F, -3.0F, -4.0F);
		this.ala2.xRot = 0.0F;
		this.ala2.yRot = -0.2618F;
		this.ala2.zRot = 0.0F;
		this.ala2.mirror = true;
		this.body.addChild(this.ala2);
		this.ala2.addBox(0.0F, yWingPosition, zWingPosition, 9.0F, 0.0F, 6.0F, 0.001F);

	}

	public void prepareMobModel(T entityIn, float limbSwing, float limbSwingAmount, float partialTick) {
		super.prepareMobModel(entityIn, limbSwing, limbSwingAmount, partialTick);
	}

	public void setupAnim(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		float wingsRotationX = 4.4F;
		float wingsRotationY = -0.5618F;
		
		this.body.xRot = -0.0F;
		this.body.yRot = 24.0F;
		this.body.zRot = 8F;
		boolean flag = entityIn.isOnGround() && entityIn.getDeltaMovement().lengthSqr() < 1.0E-7D;
		if (flag) {
			this.ala1.xRot = wingsRotationX;
			this.ala1.yRot = -wingsRotationY;
			this.ala1.zRot = 0.0F;
			this.ala2.xRot = wingsRotationX;
			this.ala2.yRot = wingsRotationY;
			this.ala2.zRot = 0.0F;
		} else {
			float f = ageInTicks * 2.1F;
			this.ala1.xRot = wingsRotationX;
			this.ala1.yRot = MathHelper.cos(f) * (float) Math.PI * 0.15F;
			this.ala1.zRot = 0.0F;
			this.ala2.xRot = this.ala1.xRot;
			this.ala2.yRot = -this.ala1.yRot;
			this.ala2.zRot = this.ala1.zRot;
			this.body.xRot = 0.0F;
			this.body.yRot = 0.0F;
			this.body.zRot = 0.0F;
		}

	}

	@Override
	protected Iterable<ModelRenderer> headParts() {
		return ImmutableList.of();
	}

	@Override
	protected Iterable<ModelRenderer> bodyParts() {
		return ImmutableList.of(this.body);
	}
}