package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.client.render.entity.layer.SoulWispGelLayer;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Vector3f;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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

	protected int getBlockLight(SoulWispEntity entityIn, float partialTicks) {
		return 8;
	}

	@Override
	public ResourceLocation getEntityTexture(SoulWispEntity entity) {
		return WISP_TEXTURES;
	}

	@Override
	protected void preRenderCallback(SoulWispEntity entityIn, MatrixStack matrixStackIn, float partialTickTime) {
		// float f = 0.9375F;
		float f = 0.2375F;
		matrixStackIn.scale(f, f, f);
		 super.preRenderCallback(entityIn, matrixStackIn, partialTickTime);
	}

	public void render(SoulWispEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
			IRenderTypeBuffer bufferIn, int packedLightIn) {
		super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
	}

	public Vec3d getRenderOffset(SoulWispEntity entityIn, float partialTicks) {
		return entityIn.isCrouching() ? new Vec3d(0.0D, -0.125D, 0.0D) : super.getRenderOffset(entityIn, partialTicks);
	}

	protected void applyRotations(SoulWispEntity entityLiving, MatrixStack matrixStackIn, float ageInTicks,
			float rotationYaw, float partialTicks) {
		float f = entityLiving.getSwimAnimation(partialTicks);
		if (entityLiving.isElytraFlying()) {
			super.applyRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
			float f1 = (float) entityLiving.getTicksElytraFlying() + partialTicks;
			float f2 = MathHelper.clamp(f1 * f1 / 100.0F, 0.0F, 1.0F);
			if (!entityLiving.isSpinAttacking()) {
				matrixStackIn.rotate(Vector3f.XP.rotationDegrees(f2 * (-90.0F - entityLiving.rotationPitch)));
			}

			Vec3d vec3d = entityLiving.getLook(partialTicks);
			Vec3d vec3d1 = entityLiving.getMotion();
			double d0 = Entity.horizontalMag(vec3d1);
			double d1 = Entity.horizontalMag(vec3d);
			if (d0 > 0.0D && d1 > 0.0D) {
				double d2 = (vec3d1.x * vec3d.x + vec3d1.z * vec3d.z) / (Math.sqrt(d0) * Math.sqrt(d1));
				double d3 = vec3d1.x * vec3d.z - vec3d1.z * vec3d.x;
				matrixStackIn.rotate(Vector3f.YP.rotation((float) (Math.signum(d3) * Math.acos(d2))));
			}
		} else if (f > 0.0F) {
			super.applyRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
			float f3 = entityLiving.isInWater() ? -90.0F - entityLiving.rotationPitch : -90.0F;
			float f4 = MathHelper.lerp(f, 0.0F, f3);
			matrixStackIn.rotate(Vector3f.XP.rotationDegrees(f4));
			if (entityLiving.isActualySwimming()) {
				matrixStackIn.translate(0.0D, -1.0D, (double) 0.3F);
			}
		} else {
			super.applyRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
		}

	}
	
	protected void renderName(SoulWispEntity entityIn, String displayNameIn, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn) {
	      matrixStackIn.push();
	      displayNameIn = "";
	      super.renderName(entityIn, displayNameIn, matrixStackIn, bufferIn, packedLightIn);
	      matrixStackIn.pop();
	   }
}
