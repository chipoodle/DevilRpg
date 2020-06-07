package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.SoulBearModel;
import com.chipoodle.devilrpg.client.render.entity.model.SoulBearModelHeart;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulBearGelLayer<T extends SoulBearEntity> extends LayerRenderer<T, SoulBearModelHeart<T>> {
	private final EntityModel<T> soulBearModel = new SoulBearModel<T>();
	private final ResourceLocation BEAR_TEXTURES = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soulbear/soulbear_a.png");
	
	public SoulBearGelLayer(IEntityRenderer<T, SoulBearModelHeart<T>> p_i50923_1_) {
		super(p_i50923_1_);
	}

	public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, T entitylivingbaseIn,
			float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
			float headPitch) {
		if (!entitylivingbaseIn.isInvisible()) {

			this.getEntityModel().setModelAttributes(this.soulBearModel);
			this.soulBearModel.setLivingAnimations(entitylivingbaseIn, limbSwing, limbSwingAmount, partialTicks);
			this.soulBearModel.render(entitylivingbaseIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw,
					headPitch);

			IVertexBuilder ivertexbuilder = bufferIn
					.getBuffer(RenderType.entityTranslucent(getEntityTexture(entitylivingbaseIn)));

			//float[] aFloat = groovy(entitylivingbaseIn,partialTicks);
			
			this.soulBearModel.render(matrixStackIn, ivertexbuilder, packedLightIn,LivingRenderer.getPackedOverlay(entitylivingbaseIn, 0.1F), 1.0F, 1.0F, 1.0F, 1.0F);
		}
	}

	private float[] groovy(T entitylivingbaseIn, float partialTicks) {
		float f;
		float f1;
		float f2;
		int i1 = 25;
		int i = entitylivingbaseIn.ticksExisted / 25 + entitylivingbaseIn.getEntityId();
		int j = DyeColor.values().length;
		int k = i % j;
		int l = (i + 1) % j;
		float f3 = ((float) (entitylivingbaseIn.ticksExisted % 25) + partialTicks) / 25.0F;
		float[] afloat1 = SheepEntity.getDyeRgb(DyeColor.byId(k));
		float[] afloat2 = SheepEntity.getDyeRgb(DyeColor.byId(l));
		f = afloat1[0] * (1.0F - f3) + afloat2[0] * f3;
		f1 = afloat1[1] * (1.0F - f3) + afloat2[1] * f3;
		f2 = afloat1[2] * (1.0F - f3) + afloat2[2] * f3;
		float[] returnFloat = {f,f1,f2};
		return returnFloat;
	}

	public ResourceLocation getEntityTexture(SoulBearEntity entity) {
			return BEAR_TEXTURES;
	}
}