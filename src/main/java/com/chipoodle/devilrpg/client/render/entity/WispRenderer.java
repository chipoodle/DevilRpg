package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.client.render.entity.model.WaspModel;
import com.chipoodle.devilrpg.entity.WispEntity;

import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.model.BeeModel;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WispRenderer extends MobRenderer<WispEntity, WaspModel<WispEntity>> {
   private static final ResourceLocation angryWaspResource = new ResourceLocation("textures/entity/bee/bee_angry.png");
   private static final ResourceLocation waspResource = new ResourceLocation("textures/entity/bee/bee.png");
  
   public WispRenderer(EntityRendererManager p_i226033_1_) {
      super(p_i226033_1_, new WaspModel<>(), 0.4F);
   }

   public ResourceLocation getEntityTexture(WispEntity entity) {
      if (entity.func_226427_ez_()) {
         return angryWaspResource;
      } else {
         return waspResource;
      }
   }
}
