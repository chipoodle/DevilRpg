package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModel;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModelHeart;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.EnergyLayer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWolfGelLayer<T extends SoulWolfEntity> extends GhostEnergyLayer<T, SoulWolfModelHeart<T>> {
	private final EntityModel<T> soulWolfModel = new SoulWolfModel<T>();
	private final ResourceLocation WOLF_GEL = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soulgelghost.png");
	//private final ResourceLocation WOLF_GEL = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soulgel.png");
	private IEntityRenderer<T, SoulWolfModelHeart<T>> entityRenderer;

	public SoulWolfGelLayer(IEntityRenderer<T, SoulWolfModelHeart<T>> p_i50923_1_) {
		super(p_i50923_1_);
		entityRenderer = p_i50923_1_;
	}

	public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, T entitylivingbaseIn,
			float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
			float headPitch) {
		groovyMethod(matrixStackIn, bufferIn, packedLightIn, entitylivingbaseIn, partialTicks);

		super.render(matrixStackIn, bufferIn, packedLightIn, entitylivingbaseIn, limbSwing, limbSwingAmount,
				partialTicks, ageInTicks, netHeadYaw, headPitch);
	}

	public ResourceLocation getEntityTexture(SoulWolfEntity entity) {
		return WOLF_GEL;
	}

	protected float xOffset(float p_225634_1_) {
		return p_225634_1_ * 0.01F;
	}

	protected ResourceLocation getTextureLocation() {
		return WOLF_GEL;
	}

	@Override
	protected EntityModel<T> model() {
		return soulWolfModel;
	}

	private void groovyMethod(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn,
			T entitylivingbaseIn, float partialTicks) {
		if (!entitylivingbaseIn.isInvisible()) {
			IVertexBuilder ivertexbuilder = entitylivingbaseIn.getBuffer(bufferIn,
					entityRenderer.getTextureLocation(entitylivingbaseIn));
			float[] rgbArray = entitylivingbaseIn.groovyRed(entitylivingbaseIn, partialTicks);
			entityRenderer.getModel().renderToBuffer(matrixStackIn, ivertexbuilder, packedLightIn,
					LivingRenderer.getOverlayCoords(entitylivingbaseIn, 0.1F), rgbArray[0], rgbArray[1], rgbArray[2],
					1.0F);
		}
	}
}