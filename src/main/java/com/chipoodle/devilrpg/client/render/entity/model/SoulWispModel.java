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
	private final ModelRenderer bone;
	private final ModelRenderer body;
	private float rollAmount;

	private final ModelRenderer[] tentacles;
	private final int numberOfTentacles = 9;

	public SoulWispModel() {
		super(false, 8.0F, 0.0F);
		this.texWidth = 64;
		this.texHeight = 64;
		this.bone = new ModelRenderer(this);
		this.bone.setPos(0.0F, 19.0F, 0.0F);
		this.body = new ModelRenderer(this, 0, 0);
		this.body.setPos(0.0F, 0.0F, 0.0F);
		this.bone.addChild(this.body);
		this.body.addBox(-3.5F, -3.5F, -3.5F, 7.0F, 7.0F, 7.0F, 0.0f);

		Random random = new Random(1660L);
		this.tentacles = new ModelRenderer[numberOfTentacles];
		for (int i = 0; i < this.tentacles.length; ++i) {
			this.tentacles[i] = new ModelRenderer(this, 0, 0);
			float f = (((float) (i % 3) - (float) (i / 3 % 2) * 0.5F + 0.25F) / 2.0F * 2.0F - 1.0F) * 5.0F;
			float f1 = ((float) (i / 3) / 2.0F * 2.0F - 1.0F) * 5.0F;
			int j = random.nextInt(7) + 4;
			this.tentacles[i].addBox(-1.0F, 0.0F, -1.0F, 1.0F, (float) j, 1.0F);
			this.tentacles[i].x = (float) (f * 0.5);
			this.tentacles[i].z = (float) (f1 * 0.5);
			this.tentacles[i].y = 3.6F;
			body.addChild(this.tentacles[i]);
		}
	}

	private void renderTentacles(float ageInTicks) {
		for (int i = 0; i < this.tentacles.length; ++i) {
			this.tentacles[i].xRot = 0.2F * MathHelper.sin(ageInTicks * 0.3F + (float) i) + 0.4F;
		}
	}

	public void prepareMobModel(T entityIn, float limbSwing, float limbSwingAmount, float partialTick) {
		super.prepareMobModel(entityIn, limbSwing, limbSwingAmount, partialTick);
	}

	public void setupAnim(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		renderTentacles(ageInTicks);
		this.bone.xRot = 0.0F;
		this.bone.y = 19.0F;
		boolean flag = entityIn.isOnGround() && entityIn.getDeltaMovement().lengthSqr() < 1.0E-7D;
		if (flag) {
		} else {
			float f = ageInTicks * 2.1F;
			this.bone.xRot = 0.0F;
			this.bone.yRot = 0.0F;
			this.bone.zRot = 0.0F;
		}

		if (this.rollAmount > 0.0F) {
			this.bone.xRot = ModelUtils.rotlerpRad(this.bone.xRot, 3.0915928F, this.rollAmount);
		}
	}

	@Override
	protected Iterable<ModelRenderer> headParts() {
		return ImmutableList.of();
	}

	@Override
	protected Iterable<ModelRenderer> bodyParts() {
		return ImmutableList.of(this.bone);
	}
}
