package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WerewolfLayer<T extends AbstractClientPlayerEntity> extends LayerRenderer<T, PlayerModel<T>> {
	private final ResourceLocation werewolfGelTexture = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soul/soulgel.png");
	private IEntityRenderer<T, PlayerModel<T>> entityRenderer;

	public WerewolfLayer(IEntityRenderer<T, PlayerModel<T>> p_i226038_1_) {
		super(p_i226038_1_);
		entityRenderer = p_i226038_1_;
	}

	@Override
	protected ResourceLocation getEntityTexture(T entityIn) {
		return this.entityRenderer.getEntityTexture(entityIn);
		// return werewolfGelTexture;
	}

	@Override
	public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, T entitylivingbaseIn,
			float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
			float headPitch) {
		renderCutoutModel(this.getEntityModel(), werewolfGelTexture, matrixStackIn, bufferIn, packedLightIn,
				entitylivingbaseIn, 1.0f, 1.0f, 1.0f);
	}

}
