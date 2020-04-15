package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModel;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModelHeart;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
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
public class SoulWolfGelLayer<T extends SoulWolfEntity> extends LayerRenderer<T, SoulWolfModelHeart<T>> {
	private final EntityModel<T> soulWolfModel = new SoulWolfModel<T>();
	private final ResourceLocation TAMED_WOLF_TEXTURES = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soulwolf/soulwolf_a.png");
	private final ResourceLocation WOLF_TEXTURES = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soulwolf/soulwolf_tame_blue_a.png");
	private final ResourceLocation ANGRY_WOLF_TEXTURES = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soulwolf/soulwolf_angry.png");

	public SoulWolfGelLayer(IEntityRenderer<T, SoulWolfModelHeart<T>> p_i50923_1_) {
		super(p_i50923_1_);
	}

	public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, T entitylivingbaseIn,
			float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
			float headPitch) {
		if (!entitylivingbaseIn.isInvisible()) {

			/*
			 * matrixStackIn.translate(0.0f,-0.14f, 0.0f); matrixStackIn.scale(1.15f, 1.15f,
			 * 1.15f);
			 */

			this.getEntityModel().setModelAttributes(this.soulWolfModel);
			this.soulWolfModel.setLivingAnimations(entitylivingbaseIn, limbSwing, limbSwingAmount, partialTicks);
			this.soulWolfModel.render(entitylivingbaseIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw,
					headPitch);

			IVertexBuilder ivertexbuilder = bufferIn
					.getBuffer(RenderType.entityTranslucent(getEntityTexture(entitylivingbaseIn)));

			//float[] aFloat = groovy(entitylivingbaseIn,partialTicks);
			
			this.soulWolfModel.render(matrixStackIn, ivertexbuilder, packedLightIn,LivingRenderer.getPackedOverlay(entitylivingbaseIn, 0.1F), 1.0F, 1.0F, 1.0F, 1.0F);
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

	public ResourceLocation getEntityTexture(SoulWolfEntity entity) {
		if (entity.isTamed()) {
			if (entity.isAngry())
				return ANGRY_WOLF_TEXTURES;

			return TAMED_WOLF_TEXTURES;
		} else {
			return entity.isAngry() ? ANGRY_WOLF_TEXTURES : WOLF_TEXTURES;
		}
	}
}