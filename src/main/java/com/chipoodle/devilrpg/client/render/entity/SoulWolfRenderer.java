package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulWolfGelLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModelHeart;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWolfRenderer extends MobRenderer<SoulWolfEntity, SoulWolfModelHeart<SoulWolfEntity>> {
	public static final ResourceLocation HEART_TEXTURES = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soul_heart_white.png");
	
	public SoulWolfRenderer(EntityRendererManager renderManagerIn) {
		super(renderManagerIn, new SoulWolfModelHeart<>(), 0.5F);
		this.addLayer(new SoulWolfGelLayer<>(this));
	}

	protected float getBob(SoulWolfEntity livingBase, float partialTicks) {
		return livingBase.getTailAngle();
	}
	
	protected int getBlockLightLevel(SoulWolfEntity entityIn,  BlockPos pos) {
		return 2;
	}
	
	@Override
	public ResourceLocation getTextureLocation(SoulWolfEntity entity) {
		return HEART_TEXTURES;
	}

	@Override
	public void render(SoulWolfEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
			IRenderTypeBuffer bufferIn, int packedLightIn) {

		if (entityIn.isWet()) {
			float f = entityIn.getBrightness() * entityIn.getWetShade(partialTicks);
			this.model.setColor(f, f, f);
		}
		
		super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);

		if (entityIn.isWet()) {
			this.model.setColor(1.0F, 1.0F, 1.0F);
		}
	}

	@Override
	protected void scale(SoulWolfEntity entityIn, MatrixStack matrixStackIn, float partialTickTime) {
		super.scale(entityIn, matrixStackIn, partialTickTime);
		
	}

}