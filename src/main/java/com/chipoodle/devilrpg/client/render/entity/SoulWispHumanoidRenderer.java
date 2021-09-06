package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWispHumanoidRenderer extends LivingRenderer<SoulWispEntity, PlayerModel<SoulWispEntity>> {
	//private static final ResourceLocation WISP_TEXTURES = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soulgel.png");
	private static final ResourceLocation WISP_TEXTURES = new ResourceLocation("textures/entity/illager/evoker.png");

	public SoulWispHumanoidRenderer(EntityRendererManager renderManagerIn) {
		super(renderManagerIn, new PlayerModel<SoulWispEntity>(0.0F, false), 0.5F);
		//this.addLayer(new SoulWispGelLayer<>(this));
	}

	protected int getBlockLightLevel(SoulWispEntity entityIn, BlockPos b) {
		return 8;
	}

	@Override
	public ResourceLocation getTextureLocation(SoulWispEntity entity) {
		return WISP_TEXTURES;
	}

	@Override
	protected void scale(SoulWispEntity entityIn, MatrixStack matrixStackIn, float partialTickTime) {
		// float f = 0.9375F;
		float f = 0.2375F;
		matrixStackIn.scale(f, f, f);
		 super.scale(entityIn, matrixStackIn, partialTickTime);
	}

	public void render(SoulWispEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
			IRenderTypeBuffer bufferIn, int packedLightIn) {
		super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
	}

	public Vector3d getRenderOffset(SoulWispEntity entityIn, float partialTicks) {
		return entityIn.isCrouching() ? new Vector3d(0.0D, -0.125D, 0.0D) : super.getRenderOffset(entityIn, partialTicks);
	}

	protected void setupRotations(SoulWispEntity entityLiving, MatrixStack matrixStackIn, float ageInTicks,
			float rotationYaw, float partialTicks) {
		float f = entityLiving.getSwimAmount(partialTicks);
		if (entityLiving.isFallFlying()) {
			super.setupRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
			float f1 = (float) entityLiving.getFallFlyingTicks() + partialTicks;
			float f2 = MathHelper.clamp(f1 * f1 / 100.0F, 0.0F, 1.0F);
			if (!entityLiving.isAutoSpinAttack()) {
				matrixStackIn.mulPose(Vector3f.XP.rotationDegrees(f2 * (-90.0F - entityLiving.xRot)));
			}

			Vector3d vec3d = entityLiving.getViewVector(partialTicks);
			Vector3d vec3d1 = entityLiving.getDeltaMovement();
			double d0 = Entity.getHorizontalDistanceSqr(vec3d1);
			double d1 = Entity.getHorizontalDistanceSqr(vec3d);
			if (d0 > 0.0D && d1 > 0.0D) {
				double d2 = (vec3d1.x * vec3d.x + vec3d1.z * vec3d.z) / (Math.sqrt(d0) * Math.sqrt(d1));
				double d3 = vec3d1.x * vec3d.z - vec3d1.z * vec3d.x;
				matrixStackIn.mulPose(Vector3f.YP.rotation((float) (Math.signum(d3) * Math.acos(d2))));
			}
		} else if (f > 0.0F) {
			super.setupRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
			float f3 = entityLiving.isInWater() ? -90.0F - entityLiving.xRot : -90.0F;
			float f4 = MathHelper.lerp(f, 0.0F, f3);
			matrixStackIn.mulPose(Vector3f.XP.rotationDegrees(f4));
			if (entityLiving.isVisuallySwimming()) {
				matrixStackIn.translate(0.0D, -1.0D, (double) 0.3F);
			}
		} else {
			super.setupRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
		}

	}
	
	protected void renderNameTag(SoulWispEntity entityIn, ITextComponent displayNameIn, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn) {
	      matrixStackIn.pushPose();
	      //displayNameIn = "";
	      super.renderNameTag(entityIn, displayNameIn, matrixStackIn, bufferIn, packedLightIn);
	      matrixStackIn.popPose();
	   }
}
