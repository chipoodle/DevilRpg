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
	private final ModelRenderer headModel;

    public SoulBearModelHeart() {
		super(12, 0.0F, true, 16.0F, 4.0F, 2.25F, 2.0F, 24);
		this.headModel = new ModelRenderer(this, 0, 0);
		this.headModel.setPos(0.0F, 10.0F, -16.0F);
        ModelRenderer wispRightEye = new ModelRenderer(this, 32, 0);
        ModelRenderer wispLeftEye = new ModelRenderer(this, 32, 4);
		wispRightEye.addBox(-2.0F, -0.25F, -3.5F, 1.0F, 0.75F, 0.5F);
		wispLeftEye.addBox(1.0F, -0.25F, -3.5F, 1.0F, 0.75F, 0.5F);
		headModel.addChild(wispRightEye);
		headModel.addChild(wispLeftEye);

		this.heart = new ModelRenderer(this, 14, 14);
		this.texWidth = 128;
		this.texHeight = 64;
		this.heart = new ModelRenderer(this);
		// this.heart.texOffs(0, 19).addBox(-5.0F, -13.0F, -7.0F, 14.0F, 14.0F,
		// 11.0F, 0.0F);
		this.heart.texOffs(39, 0).addBox(-1.0F, -23.0F, -4.0F, 6.0F, 6.0F, 6.0F, 0.0F);
		this.heart.setPos(-2.0F, 9.0F, 12.0F);
	}

	protected Iterable<ModelRenderer> headParts() {
		return ImmutableList.of(this.headModel);
	}

	protected Iterable<ModelRenderer> bodyParts() {
		return ImmutableList.of(this.heart);
	}

	public void setupAnim(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks,float netHeadYaw, float headPitch) {
		super.setupAnim(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		float f = ageInTicks - (float) entityIn.tickCount;
		float f1 = entityIn.getStandingAnimationScale(f);
		f1 = f1 * f1;
		float f2 = 1.0F - f1;
		this.heart.xRot = ((float) Math.PI / 2F) - f1 * (float) Math.PI * 0.35F;
		this.heart.y = 9.0F * f2 + 11.0F * f1;
		if (entityIn.isBaby()) {
			this.headModel.y = 10.0F * f2 - 9.0F * f1;
			this.headModel.z = -16.0F * f2 - 7.0F * f1;
		} else {
			this.headModel.y = 10.0F * f2 - 14.0F * f1;
			this.headModel.z = -16.0F * f2 - 3.0F * f1;
		}

		this.headModel.xRot += f1 * (float) Math.PI * 0.15F;
		
		this.headModel.xRot = headPitch * ((float) Math.PI / 180F);
		this.headModel.yRot = netHeadYaw * ((float) Math.PI / 180F);
	}
}