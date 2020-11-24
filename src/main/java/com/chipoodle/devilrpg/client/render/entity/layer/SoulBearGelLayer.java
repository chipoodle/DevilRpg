package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.SoulBearModel;
import com.chipoodle.devilrpg.client.render.entity.model.SoulBearModelHeart;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
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
public class SoulBearGelLayer<T extends SoulBearEntity> extends EnergyLayer<T, SoulBearModelHeart<T>> {
	private final EntityModel<T> soulBearModel = new SoulBearModel<T>();
	private final ResourceLocation BEAR_GEL = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soul/soulgel.png");
	private IEntityRenderer<T, SoulBearModelHeart<T>> entityRenderer;

	public SoulBearGelLayer(IEntityRenderer<T, SoulBearModelHeart<T>> p_i50923_1_) {
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

	public ResourceLocation getEntityTexture(SoulBearEntity entity) {
		return BEAR_GEL;
	}

	protected float func_225634_a_(float p_225634_1_) {
		return p_225634_1_ * 0.01F;
	}

	protected ResourceLocation func_225633_a_() {
		return BEAR_GEL;
	}

	@Override
	protected EntityModel<T> func_225635_b_() {
		return soulBearModel;
	}

	private void groovyMethod(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn,
			T entitylivingbaseIn, float partialTicks) {
		if (!entitylivingbaseIn.isInvisible()) {
			IVertexBuilder ivertexbuilder = entitylivingbaseIn.getBuffer(bufferIn,
					entityRenderer.getEntityTexture(entitylivingbaseIn));
			float[] rgbArray = entitylivingbaseIn.groovyRed(entitylivingbaseIn, partialTicks);
			entityRenderer.getEntityModel().render(matrixStackIn, ivertexbuilder, packedLightIn,
					LivingRenderer.getPackedOverlay(entitylivingbaseIn, 0.1F), rgbArray[0], rgbArray[1], rgbArray[2],
					1.0F);
		}
	}
}