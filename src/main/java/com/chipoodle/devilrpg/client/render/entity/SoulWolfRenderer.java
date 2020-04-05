package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulWolfCollarLayer;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulWolfGelLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModel;
import com.chipoodle.devilrpg.entity.soulwolf.SoulWolfEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWolfRenderer extends MobRenderer<SoulWolfEntity, SoulWolfModel<SoulWolfEntity>> {
	public static final ResourceLocation WOLF_TEXTURES = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soulwolf/soulwolf_tame_blue.png");
	public static final ResourceLocation TAMED_WOLF_TEXTURES = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soulwolf/soulwolf_a.png");
	private static final ResourceLocation ANGRY_WOLF_TEXTURES = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soulwolf/soulwolf_angry.png");

	public SoulWolfRenderer(EntityRendererManager renderManagerIn) {
		super(renderManagerIn, new SoulWolfModel<>(), 0.5F);
		this.addLayer(new SoulWolfGelLayer<>(this));
		this.addLayer(new SoulWolfCollarLayer(this));
	}

	protected float handleRotationFloat(SoulWolfEntity livingBase, float partialTicks) {
		return livingBase.getTailRotation();
	}

	@Override
	public void render(SoulWolfEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
			IRenderTypeBuffer bufferIn, int packedLightIn) {

		if (entityIn.isWolfWet()) {
			float f = entityIn.getBrightness() * entityIn.getShadingWhileWet(partialTicks);
			this.entityModel.func_228253_a_(f, f, f);
		}

		
		
		
		IVertexBuilder ivertexbuilder = bufferIn
				.getBuffer(RenderType.entityTranslucent(this.getEntityTexture(entityIn)));
		
		RenderSystem.enableAlphaTest();
		RenderSystem.fogDensity(1.0f);
		RenderSystem.alphaFunc(1, 0.5f);
		RenderType.translucent();
		

		super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);

		if (entityIn.isWolfWet()) {
			this.entityModel.func_228253_a_(1.0F, 1.0F, 1.0F);
		}

		super.renderName(entityIn, entityIn.getHealth() + "/" + entityIn.getMaxHealth(), matrixStackIn, bufferIn,
				packedLightIn);
	}

	@Override
	protected void preRenderCallback(SoulWolfEntity entityIn, MatrixStack matrixStackIn, float partialTickTime) {
		super.preRenderCallback(entityIn, matrixStackIn, partialTickTime);
	}

	public ResourceLocation getEntityTexture(SoulWolfEntity entity) {
		if (entity.isTamed()) {
			return TAMED_WOLF_TEXTURES;
		} else {
			return entity.isAngry() ? ANGRY_WOLF_TEXTURES : WOLF_TEXTURES;
		}
	}
}