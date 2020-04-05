package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModel;
import com.chipoodle.devilrpg.entity.soulwolf.SoulWolfEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWolfCollarLayer extends LayerRenderer<SoulWolfEntity, SoulWolfModel<SoulWolfEntity>> {
	private static final ResourceLocation WOLF_COLLAR = new ResourceLocation("textures/entity/wolf/wolf_collar.png");

	   public SoulWolfCollarLayer(IEntityRenderer<SoulWolfEntity, SoulWolfModel<SoulWolfEntity>> rendererIn) {
	      super(rendererIn);
	   }

	   public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, SoulWolfEntity entitylivingbaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
	      if (entitylivingbaseIn.isTamed() && !entitylivingbaseIn.isInvisible()) {
	         float[] afloat = entitylivingbaseIn.getCollarColor().getColorComponentValues();
	         renderCutoutModel(this.getEntityModel(), WOLF_COLLAR, matrixStackIn, bufferIn, packedLightIn, entitylivingbaseIn, afloat[0], afloat[1], afloat[2]);
	      }
	   }
}
