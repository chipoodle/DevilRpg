package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulFireBallEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.model.GenericHeadModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulFireBallRenderer extends EntityRenderer<SoulFireBallEntity> {
	private static final ResourceLocation WITHER_TEXTURES = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soulsnowball/freeze_texture.jpg");
	private final GenericHeadModel skeletonHeadModel = new GenericHeadModel();

	public SoulFireBallRenderer(EntityRendererManager renderManagerIn) {
		super(renderManagerIn);
	}

	protected int getBlockLightLevel(SoulFireBallEntity entityIn,  BlockPos pos) {
		return 15;
	}

	public void render(SoulFireBallEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
			IRenderTypeBuffer bufferIn, int packedLightIn) {
		matrixStackIn.pushPose();
		matrixStackIn.scale(-0.50F, -0.50F, 0.50F);
		// matrixStackIn.scale(-1.0F, -1.0F, 1.0F);
		float f = MathHelper.rotLerp(entityIn.yRotO, entityIn.yRot, partialTicks);
		float f1 = MathHelper.lerp(partialTicks, entityIn.xRotO, entityIn.xRot);

		IVertexBuilder ivertexbuilder = bufferIn
				.getBuffer(this.skeletonHeadModel.renderType(this.getTextureLocation(entityIn)));
		this.skeletonHeadModel.setupAnim(0.0F, f, f1);
		this.skeletonHeadModel.renderToBuffer(matrixStackIn, ivertexbuilder, packedLightIn, OverlayTexture.NO_OVERLAY, 1.0F,
				1.0F, 1.0F, 1.0F);
		matrixStackIn.popPose();
		super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
	}

	public ResourceLocation getTextureLocation(SoulFireBallEntity entity) {
		return WITHER_TEXTURES;
	}

}
