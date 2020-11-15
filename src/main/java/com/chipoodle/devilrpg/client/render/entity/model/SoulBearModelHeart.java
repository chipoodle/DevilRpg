package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.google.common.collect.ImmutableList;

import net.minecraft.client.renderer.entity.model.QuadrupedModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulBearModelHeart<T extends SoulBearEntity> extends QuadrupedModel<T> {
	private ModelRenderer heart;

	public SoulBearModelHeart() {
		super(12, 0.0F, true, 16.0F, 4.0F, 2.25F, 2.0F, 24);
		 this.heart = new ModelRenderer(this, 14, 14);
		this.textureWidth = 128;
		this.textureHeight = 64;
		this.heart = new ModelRenderer(this);
		//this.heart.setTextureOffset(0, 19).addBox(-5.0F, -13.0F, -7.0F, 14.0F, 14.0F, 11.0F, 0.0F);
		this.heart.setTextureOffset(39, 0).addBox(-1.0F, -23.0F, -4.0F, 6.0F, 6.0F, 6.0F, 0.0F);
		this.heart.setRotationPoint(-2.0F, 9.0F, 12.0F);
	}

	protected Iterable<ModelRenderer> getHeadParts() {
		return ImmutableList.of(this.heart);
	}

	protected Iterable<ModelRenderer> getBodyParts() {
		return ImmutableList.of(this.heart);
	}

	public void setRotationAngles(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		super.setRotationAngles(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		float f = ageInTicks - (float) entityIn.ticksExisted;
		float f1 = entityIn.getStandingAnimationScale(f);
		f1 = f1 * f1;
		float f2 = 1.0F - f1;
		this.heart.rotateAngleX = ((float) Math.PI / 2F) - f1 * (float) Math.PI * 0.35F;
		this.heart.rotationPointY = 9.0F * f2 + 11.0F * f1;
	}
}