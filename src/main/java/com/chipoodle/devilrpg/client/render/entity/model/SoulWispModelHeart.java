package com.chipoodle.devilrpg.client.render.entity.model;

import java.util.Random;

import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.google.common.collect.ImmutableList;

import net.minecraft.client.renderer.entity.model.AgeableModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWispModelHeart<T extends SoulWispEntity> extends AgeableModel<T> {
	private final ModelRenderer cuerpo;
	private final ModelRenderer[] blazeSticks;
	private final ModelRenderer[] tentacles;
	private final ModelRenderer wispRightEye;
	private final ModelRenderer wispLeftEye;

	private final float distanciaDesdeElCentro = 4.0F;
	private final int numberOfSticks = 5;
	private final int numberOfTentacles = 9;
	// private float field_228241_n_;

	public SoulWispModelHeart() {
		super(false, 8.0F, 0.0F);

		this.textureWidth = 64;
		this.textureHeight = 64;
		this.cuerpo = new ModelRenderer(this);
		this.cuerpo.setRotationPoint(0.0F, 19.0F, 0.0F);
		this.cuerpo.addBox(-2.5F, -2.5F, -2.5F, 5.0F, 5.0F, 5.0F, 0.0f);

		wispRightEye = new ModelRenderer(this, 32, 0);
		wispLeftEye = new ModelRenderer(this, 32, 4);
		wispRightEye.addBox(-3.25F, -2.0F, -3.4F, 2.0F, 2.0F, 2.0F);
		wispLeftEye.addBox(1.25F, -2.0F, -3.4F, 2.0F, 2.0F, 2.0F);
		cuerpo.addChild(wispRightEye);
		cuerpo.addChild(wispLeftEye);

		this.blazeSticks = new ModelRenderer[numberOfSticks];
		for (int i = 0; i < this.blazeSticks.length; ++i) {
			this.blazeSticks[i] = new ModelRenderer(this, 0, 16);
			this.blazeSticks[i].addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.0F, -1.0F);
			cuerpo.addChild(this.blazeSticks[i]);
		}
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

	public void setRotationAngles(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch) {

		/*
		 * if (this.field_228241_n_ > 0.0F) { this.cuerpo.rotateAngleX =
		 * ModelUtils.func_228283_a_(this.cuerpo.rotateAngleX, 3.0915928F,
		 * this.field_228241_n_); }
		 */
		renderSticks(ageInTicks);
		renderTentacles(ageInTicks);
	}

	private void renderTentacles(float ageInTicks) {
		for (int i = 0; i < this.tentacles.length; ++i) {
			this.tentacles[i].rotateAngleX = 0.2F * MathHelper.sin(ageInTicks * 0.3F + (float) i) + 0.4F;
		}
	}

	private void renderSticks(float ageInTicks) {
		// float f = ageInTicks * (float) Math.PI * -0.1F;

		// f = ((float) Math.PI / 4F) + ageInTicks * (float) Math.PI * 0.03;
		float f = (float) (ageInTicks * 0.43);

		for (int j = 0; j < numberOfSticks; ++j) {
			this.blazeSticks[j].rotationPointY = 3.25F + MathHelper.cos(((float) (j * 2.5) + ageInTicks) * 0.25F);
			this.blazeSticks[j].rotateAngleY = f;
			// this.blazeSticks[j].rotateAngleX = MathHelper.sin(f)*0.3f;
			this.blazeSticks[j].rotationPointX = -MathHelper.sin(f) * distanciaDesdeElCentro;
			this.blazeSticks[j].rotationPointZ = -MathHelper.cos(f) * distanciaDesdeElCentro;
			f += Math.PI * 2 / numberOfSticks;
		}
	}

	protected Iterable<ModelRenderer> getHeadParts() {
		return ImmutableList.of();
	}

	protected Iterable<ModelRenderer> getBodyParts() {
		return ImmutableList.of(this.cuerpo);
	}
}
