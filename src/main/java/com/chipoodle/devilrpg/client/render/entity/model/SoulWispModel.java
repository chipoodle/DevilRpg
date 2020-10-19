package com.chipoodle.devilrpg.client.render.entity.model;

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
	}

	public void setLivingAnimations(T entityIn, float limbSwing, float limbSwingAmount, float partialTick) {
		super.setLivingAnimations(entityIn, limbSwing, limbSwingAmount, partialTick);
	}

	public void render(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		this.cuerpo2.rotateAngleX = 0.0F;
		this.cuerpo2.rotationPointY = 19.0F;
		boolean flag = entityIn.onGround && entityIn.getMotion().lengthSquared() < 1.0E-7D;
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
