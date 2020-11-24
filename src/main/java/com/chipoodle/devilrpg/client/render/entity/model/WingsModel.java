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
	private final ModelRenderer field_228231_a_;
	//private final ModelRenderer field_228232_b_;
	private final ModelRenderer ala1;
	private final ModelRenderer ala2;

	public WingsModel() {
		super(false, 24.0F, 0.0F);
		this.textureWidth = 64;
		this.textureHeight = 64;
		this.field_228231_a_ = new ModelRenderer(this);
		//this.field_228231_a_.setRotationPoint(0.0F, 19.0F, 0.0F);
		//this.field_228232_b_ = new ModelRenderer(this, 0, 0);
		//this.field_228232_b_.setRotationPoint(0.0F, 0.0F, 0.0F);
		//this.field_228231_a_.addChild(this.field_228232_b_);

		//this.field_228232_b_.addBox(-3.5F, -4.0F, -5.0F, 7.0F, 7.0F, 10.0F, 0.0F);
		float zWingPosition= -9.0f;
		float yWingPosition= 3.9f;
		
		this.ala1 = new ModelRenderer(this, 0, 18);
		this.ala1.setRotationPoint(-1.5F, -3.0F, -4.0F);
		this.ala1.rotateAngleX = 0.0F;
		this.ala1.rotateAngleY = 0.2618F;
		this.ala1.rotateAngleZ = 0.0F;
		this.field_228231_a_.addChild(this.ala1);
		this.ala1.addBox(-9.0F,yWingPosition, zWingPosition, 9.0F, 0.0F, 6.0F, 0.001F);

		this.ala2 = new ModelRenderer(this, 0, 18);
		this.ala2.setRotationPoint(1.5F, -3.0F, -4.0F);
		this.ala2.rotateAngleX = 0.0F;
		this.ala2.rotateAngleY = -0.2618F;
		this.ala2.rotateAngleZ = 0.0F;
		this.ala2.mirror = true;
		this.field_228231_a_.addChild(this.ala2);
		this.ala2.addBox(0.0F, yWingPosition, zWingPosition, 9.0F, 0.0F, 6.0F, 0.001F);

	}

	public void setLivingAnimations(T entityIn, float limbSwing, float limbSwingAmount, float partialTick) {
		super.setLivingAnimations(entityIn, limbSwing, limbSwingAmount, partialTick);
	}

	public void setRotationAngles(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		float wingsRotationX = 4.4F;
		float wingsRotationY = -0.5618F;
		
		this.field_228231_a_.rotateAngleX = -0.0F;
		this.field_228231_a_.rotationPointY = 24.0F;
		this.field_228231_a_.rotationPointZ = 8F;
		boolean flag = entityIn.isOnGround() && entityIn.getMotion().lengthSquared() < 1.0E-7D;
		if (flag) {
			this.ala1.rotateAngleX = wingsRotationX;
			this.ala1.rotateAngleY = -wingsRotationY;
			this.ala1.rotateAngleZ = 0.0F;
			this.ala2.rotateAngleX = wingsRotationX;
			this.ala2.rotateAngleY = wingsRotationY;
			this.ala2.rotateAngleZ = 0.0F;
		} else {
			float f = ageInTicks * 2.1F;
			this.ala1.rotateAngleX = wingsRotationX;
			this.ala1.rotateAngleY = MathHelper.cos(f) * (float) Math.PI * 0.15F;
			this.ala1.rotateAngleZ = 0.0F;
			this.ala2.rotateAngleX = this.ala1.rotateAngleX;
			this.ala2.rotateAngleY = -this.ala1.rotateAngleY;
			this.ala2.rotateAngleZ = this.ala1.rotateAngleZ;
			this.field_228231_a_.rotateAngleX = 0.0F;
			this.field_228231_a_.rotateAngleY = 0.0F;
			this.field_228231_a_.rotateAngleZ = 0.0F;
		}

	}

	protected Iterable<ModelRenderer> getHeadParts() {
		return ImmutableList.of();
	}

	protected Iterable<ModelRenderer> getBodyParts() {
		return ImmutableList.of(this.field_228231_a_);
	}
}