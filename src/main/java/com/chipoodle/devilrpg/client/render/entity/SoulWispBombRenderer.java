package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulWispGelLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWispModelHeart;
import com.chipoodle.devilrpg.entity.SoulWispBomberEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWispBombRenderer extends MobRenderer<SoulWispBomberEntity, SoulWispModelHeart<SoulWispBomberEntity>> {
	private static final ResourceLocation WISP_TEXTURES = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soul/soul_heart_white.png");

	public SoulWispBombRenderer(EntityRendererManager renderManagerIn) {
		super(renderManagerIn, new SoulWispModelHeart<>(), 0.5F);

		this.addLayer(new SoulWispGelLayer<>(this, 0.01F));
	}

	protected int getBlockLightLevel(SoulWispEntity entityIn,  BlockPos pos) {
		return 8;
	}

	@Override
	public ResourceLocation getTextureLocation(SoulWispBomberEntity entity) {
		return WISP_TEXTURES;
	}

	@Override
	public void render(SoulWispBomberEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
			IRenderTypeBuffer bufferIn, int packedLightIn) {
		super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
	}

	@Override
	protected void scale(SoulWispBomberEntity entityIn, MatrixStack matrixStackIn, float partialTickTime) {
		// matrixStackIn.scale(x, y, z);
		super.scale(entityIn, matrixStackIn, partialTickTime);
	}
}
