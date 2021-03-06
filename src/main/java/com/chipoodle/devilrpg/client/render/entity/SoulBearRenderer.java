package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulBearGelLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SoulBearModelHeart;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulBearRenderer extends MobRenderer<SoulBearEntity, SoulBearModelHeart<SoulBearEntity>> {
   //private static final ResourceLocation POLAR_BEAR_TEXTURE = new ResourceLocation("textures/entity/bear/polarbear.png");
   public static final ResourceLocation POLAR_BEAR_TEXTURE = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soul_heart_white.png");
   
   
   public SoulBearRenderer(EntityRendererManager renderManagerIn) {
      super(renderManagerIn, new SoulBearModelHeart<>(), 0.9F);
      this.addLayer(new SoulBearGelLayer<>(this));
   }
   
   protected int getBlockLightLevel(SoulBearEntity entityIn, BlockPos pos) {
		return 1;
	}

   public ResourceLocation getTextureLocation(SoulBearEntity entity) {
      return POLAR_BEAR_TEXTURE;
   }

   protected void scale(SoulBearEntity entitylivingbaseIn, MatrixStack matrixStackIn, float partialTickTime) {
      matrixStackIn.scale(1.2F, 1.2F, 1.2F);
      super.scale(entitylivingbaseIn, matrixStackIn, partialTickTime);
   }
}
