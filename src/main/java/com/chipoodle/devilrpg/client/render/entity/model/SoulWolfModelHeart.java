package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.google.common.collect.ImmutableList;

import net.minecraft.client.renderer.entity.model.TintedAgeableModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWolfModelHeart<T extends SoulWolfEntity> extends TintedAgeableModel<T> {
	private final ModelRenderer heart;
	private final ModelRenderer head;
	private final ModelRenderer wispRightEye;
	private final ModelRenderer wispLeftEye;
	
	public SoulWolfModelHeart() {
		head = new ModelRenderer(this, 0, 0);
		head.setPos(-1.0F, 13.5F, -7.0F);
		wispRightEye = new ModelRenderer(this, 32, 0);
		wispLeftEye = new ModelRenderer(this, 32, 4);
		wispRightEye.addBox(-0.75F, -1.0F, -2.5F, 1.0F, 0.75F, 0.5F);
		wispLeftEye.addBox(1.75F, -1.0F, -2.5F, 1.0F, 0.75F, 0.5F);
		head.addChild(wispRightEye);
		head.addChild(wispLeftEye);
		
		this.heart = new ModelRenderer(this, 14, 14);
		this.heart.addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, 0.0F);
		this.heart.setPos(0.0F, 14.0F, -2.0F);
	}

	protected Iterable<ModelRenderer> headParts() {
		return ImmutableList.of(this.head);
	}

	protected Iterable<ModelRenderer> bodyParts() {
		return ImmutableList.of(this.heart);
	}

	public void prepareMobModel(T entityIn, float limbSwing, float limbSwingAmount, float partialTick) {
		this.heart.zRot = entityIn.getBodyRollAngle(partialTick, -0.16F);
	}

	@Override
	public void setupAnim(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		this.head.xRot = headPitch * ((float) Math.PI / 180F);
		this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
	}
}