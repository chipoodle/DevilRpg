package com.chipoodle.devilrpg.client.render.entity.model;

import java.util.Random;

import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.google.common.collect.ImmutableList;

import net.minecraft.client.renderer.entity.model.AgeableModel;
import net.minecraft.client.renderer.entity.model.ModelUtils;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWispModel<T extends SoulWispEntity> extends AgeableModel<T> {
	private final ModelRenderer cuerpo2;
	private final ModelRenderer cuerpo;
	private float field_228241_n_;

	private final ModelRenderer[] tentacles;
	private final int numberOfTentacles = 9;

	public SoulWispModel() {
		super(false, 8.0F, 0.0F);
		this.textureWidth = 64;
		this.textureHeight = 64;
		this.cuerpo2 = new ModelRenderer(this);
		this.cuerpo2.setRotationPoint(0.0F, 19.0F, 0.0F);
		this.cuerpo = new ModelRenderer(this, 0, 0);
		this.cuerpo.setRotationPoint(0.0F, 0.0F, 0.0F);
		this.cuerpo2.addChild(this.cuerpo);
		this.cuerpo.addBox(-3.5F, -3.5F, -3.5F, 7.0F, 7.0F, 7.0F, 0.0f);

		Random random = new Random(1660L);
		this.tentacles = new ModelRenderer[numberOfTentacles];
		for (int i = 0; i < this.tentacles.length; ++i) {
			this.tentacles[i] = new ModelRenderer(this, 0, 0);
			float f = (((float) (i % 3) - (float) (i / 3 % 2) * 0.5F + 0.25F) / 2.0F * 2.0F - 1.0F) * 5.0F;
			float f1 = ((float) (i / 3) / 2.0F * 2.0F - 1.0F) * 5.0F;
			int j = random.nextInt(7) + 4;
			this.tentacles[i].addBox(-1.0F, 0.0F, -1.0F, 1.0F, (float) j, 1.0F);
			this.tentacles[i].rotationPointX = (float) (f * 0.5);
			this.tentacles[i].rotationPointZ = (float) (f1 * 0.5);
			this.tentacles[i].rotationPointY = 3.6F;
			cuerpo.addChild(this.tentacles[i]);
		}
	}

	private void renderTentacles(float ageInTicks) {
		for (int i = 0; i < this.tentacles.length; ++i) {
			this.tentacles[i].rotateAngleX = 0.2F * MathHelper.sin(ageInTicks * 0.3F + (float) i) + 0.4F;
		}
	}

	public void setLivingAnimations(T entityIn, float limbSwing, float limbSwingAmount, float partialTick) {
		super.setLivingAnimations(entityIn, limbSwing, limbSwingAmount, partialTick);
	}

	public void setRotationAngles(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch) {
		renderTentacles(ageInTicks);
		this.cuerpo2.rotateAngleX = 0.0F;
		this.cuerpo2.rotationPointY = 19.0F;
		boolean flag = entityIn.isOnGround() && entityIn.getMotion().lengthSquared() < 1.0E-7D;
		if (flag) {
		} else {
			float f = ageInTicks * 2.1F;
			this.cuerpo2.rotateAngleX = 0.0F;
			this.cuerpo2.rotateAngleY = 0.0F;
			this.cuerpo2.rotateAngleZ = 0.0F;
		}

		if (this.field_228241_n_ > 0.0F) {
			this.cuerpo2.rotateAngleX = ModelUtils.func_228283_a_(this.cuerpo2.rotateAngleX, 3.0915928F,
					this.field_228241_n_);
		}
	}

	protected Iterable<ModelRenderer> getHeadParts() {
		return ImmutableList.of();
	}

	protected Iterable<ModelRenderer> getBodyParts() {
		return ImmutableList.of(this.cuerpo2);
	}
}
