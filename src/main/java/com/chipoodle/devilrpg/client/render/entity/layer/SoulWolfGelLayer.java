package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModel;
import com.chipoodle.devilrpg.entity.soulwolf.SoulWolfEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWolfGelLayer<T extends SoulWolfEntity> extends LayerRenderer<T, SoulWolfModel<T>> {
	private final EntityModel<T> soulWolfModel = new SoulWolfModel<T>();

	public SoulWolfGelLayer(IEntityRenderer<T, SoulWolfModel<T>> p_i50923_1_) {
		super(p_i50923_1_);
	}

	public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, T entitylivingbaseIn,
			float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
			float headPitch) {
		if (!entitylivingbaseIn.isInvisible()) {
			
			matrixStackIn.translate(0.0f,-0.14f, 0.0f);
			matrixStackIn.scale(1.15f, 1.15f, 1.15f);
			
			
			this.getEntityModel().setModelAttributes(this.soulWolfModel);
			this.soulWolfModel.setLivingAnimations(entitylivingbaseIn, limbSwing, limbSwingAmount, partialTicks);
			this.soulWolfModel.render(entitylivingbaseIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw,
					headPitch);
			
			IVertexBuilder ivertexbuilder = bufferIn
					.getBuffer(RenderType.entityTranslucent(this.getEntityTexture(entitylivingbaseIn)));
						
			this.soulWolfModel.render(matrixStackIn, ivertexbuilder, packedLightIn,
					LivingRenderer.getPackedOverlay(entitylivingbaseIn, 0.9f), 1.0F, 1.0F, 1.0F, 1.0f);
		}
	}
}