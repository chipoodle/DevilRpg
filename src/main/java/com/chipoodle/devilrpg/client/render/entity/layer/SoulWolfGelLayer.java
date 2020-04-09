package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModel;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModelHeart;
import com.chipoodle.devilrpg.entity.soulwolf.SoulWolfEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
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

			this.soulWolfModel.render(matrixStackIn, ivertexbuilder, packedLightIn,
					LivingRenderer.getPackedOverlay(entitylivingbaseIn,0.1F), 1.0F, 1.0F, 1.0F, 1.0f);
		}
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