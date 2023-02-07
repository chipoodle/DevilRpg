package com.chipoodle.devilrpg.client.render.entity.layer;

import java.util.Random;

import com.chipoodle.devilrpg.client.render.entity.model.WerewolfTransformedModel;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class StuckInBodyWerewolfLayer<T extends LivingEntity, M extends WerewolfTransformedModel<T>>
		extends LayerRenderer<T, M> {
	public StuckInBodyWerewolfLayer(LivingRenderer<T, M> p_i226041_1_) {
		super(p_i226041_1_);
	}

	protected abstract int numStuck(T p_225631_1_);

	protected abstract void renderStuckItem(MatrixStack p_225632_1_, IRenderTypeBuffer p_225632_2_, int p_225632_3_,
			Entity p_225632_4_, float p_225632_5_, float p_225632_6_, float p_225632_7_, float p_225632_8_);

	public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, T entitylivingbaseIn,
			float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
			float headPitch) {
		int i = this.numStuck(entitylivingbaseIn);
		Random random = new Random(entitylivingbaseIn.getId());
		if (i > 0) {
			for (int j = 0; j < i; ++j) {
				matrixStackIn.pushPose();
				ModelRenderer modelrenderer = this.getParentModel().getRandomModelPart(random);
				ModelRenderer.ModelBox modelrenderer$modelbox = modelrenderer.getRandomCube(random);
				modelrenderer.translateAndRotate(matrixStackIn);
				float f = random.nextFloat();
				float f1 = random.nextFloat();
				float f2 = random.nextFloat();
				float f3 = MathHelper.lerp(f, modelrenderer$modelbox.minX, modelrenderer$modelbox.maxX) / 16.0F;
				float f4 = MathHelper.lerp(f1, modelrenderer$modelbox.minY, modelrenderer$modelbox.maxY) / 16.0F;
				float f5 = MathHelper.lerp(f2, modelrenderer$modelbox.minZ, modelrenderer$modelbox.maxZ) / 16.0F;
				matrixStackIn.translate(f3, f4, f5);
				f = -1.0F * (f * 2.0F - 1.0F);
				f1 = -1.0F * (f1 * 2.0F - 1.0F);
				f2 = -1.0F * (f2 * 2.0F - 1.0F);
				this.renderStuckItem(matrixStackIn, bufferIn, packedLightIn, entitylivingbaseIn, f, f1, f2,
						partialTicks);
				matrixStackIn.popPose();
			}

		}
	}
}
