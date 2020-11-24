package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulWispGelLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWispModelHeart;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWispRenderer extends MobRenderer<SoulWispEntity, SoulWispModelHeart<SoulWispEntity>> {
	private static final ResourceLocation WISP_TEXTURES = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soul/soul_heart_white.png");
	//private static final ResourceLocation WISP_GEL = new ResourceLocation("textures/entity/ghast/ghast.png");

	public SoulWispRenderer(EntityRendererManager renderManagerIn) {
		// super(renderManagerIn, new PlayerModel<SoulWispEntity>(0.0F, false), 0.5F);
		super(renderManagerIn, new SoulWispModelHeart<SoulWispEntity>(), 0.5F);

		this.addLayer(new SoulWispGelLayer<>(this, 0.01F));
	}

	protected int getBlockLight(SoulWispEntity entityIn, float partialTicks) {
		return 8;
	}

	@Override
	public ResourceLocation getEntityTexture(SoulWispEntity entity) {
		return WISP_TEXTURES;
	}

	@Override
	public void render(SoulWispEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
			IRenderTypeBuffer bufferIn, int packedLightIn) {
		super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
	}

	@Override
	protected void preRenderCallback(SoulWispEntity entityIn, MatrixStack matrixStackIn, float partialTickTime) {
		// matrixStackIn.scale(x, y, z);
		super.preRenderCallback(entityIn, matrixStackIn, partialTickTime);
	}
}
