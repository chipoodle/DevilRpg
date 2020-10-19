package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulWolfGelLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModelHeart;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EnergyLayer;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWolfRenderer extends MobRenderer<SoulWolfEntity, SoulWolfModelHeart<SoulWolfEntity>> {
	public static final ResourceLocation HEART_TEXTURES = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soulwolf/soulwolf_heart.png");
	
	public SoulWolfRenderer(EntityRendererManager renderManagerIn) {
		super(renderManagerIn, new SoulWolfModelHeart<>(), 0.5F);
		this.addLayer(new SoulWolfGelLayer<>(this));
	}

	protected float handleRotationFloat(SoulWolfEntity livingBase, float partialTicks) {
		return livingBase.getTailRotation();
	}
	
	protected int getBlockLight(SoulWolfEntity entityIn, float partialTicks) {
		return 1;
	}
	
	@Override
	public ResourceLocation getEntityTexture(SoulWolfEntity entity) {
		return HEART_TEXTURES;
	}

	@Override
	public void render(SoulWolfEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
			IRenderTypeBuffer bufferIn, int packedLightIn) {

		if (entityIn.isWolfWet()) {
			float f = entityIn.getBrightness() * entityIn.getShadingWhileWet(partialTicks);
			this.entityModel.func_228253_a_(f, f, f);
		}
		
		super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);

		if (entityIn.isWolfWet()) {
			this.entityModel.func_228253_a_(1.0F, 1.0F, 1.0F);
		}
	}

	@Override
	protected void preRenderCallback(SoulWolfEntity entityIn, MatrixStack matrixStackIn, float partialTickTime) {
		super.preRenderCallback(entityIn, matrixStackIn, partialTickTime);
		
	}

}