package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.client.render.entity.model.WispModel;
import com.chipoodle.devilrpg.entity.WispEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WispRenderer extends MobRenderer<WispEntity, WispModel<WispEntity>> {
   public static final ResourceLocation[] PARROT_TEXTURES = new ResourceLocation[]{new ResourceLocation("textures/entity/parrot/parrot_red_blue.png"), new ResourceLocation("textures/entity/parrot/parrot_blue.png"), new ResourceLocation("textures/entity/parrot/parrot_green.png"), new ResourceLocation("textures/entity/parrot/parrot_yellow_blue.png"), new ResourceLocation("textures/entity/parrot/parrot_grey.png")};

   public WispRenderer(EntityRendererManager renderManagerIn) {
      super(renderManagerIn, new WispModel<WispEntity>(), 0.3F);
   }

   public ResourceLocation getEntityTexture(WispEntity entity) {
      return PARROT_TEXTURES[entity.getVariant()];
   }

   public float handleRotationFloat(WispEntity livingBase, float partialTicks) {
      float f = MathHelper.lerp(partialTicks, livingBase.oFlap, livingBase.flap);
      float f1 = MathHelper.lerp(partialTicks, livingBase.oFlapSpeed, livingBase.flapSpeed);
      return (MathHelper.sin(f) + 1.0F) * f1;
   }
   
   public void render(WispEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn) {
	      super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
	      super.renderName(entityIn, entityIn.getHealth() + "/" + entityIn.getMaxHealth(), matrixStackIn, bufferIn,
				   packedLightIn);
   }
   
}
