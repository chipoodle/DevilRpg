package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.google.common.collect.ImmutableList;

import net.minecraft.client.renderer.entity.model.TintedAgeableModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWolfModelHeart<T extends SoulWolfEntity> extends TintedAgeableModel<T> {
   private  ModelRenderer heart;
 

   public SoulWolfModelHeart() {
    this.heart = new ModelRenderer(this, 14, 14);
      this.heart.addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, 0.0F);
      this.heart.setRotationPoint(0.0F, 14.0F, -2.0F);
   }

   protected Iterable<ModelRenderer> getHeadParts() {
      return ImmutableList.of(this.heart);
   }

   protected Iterable<ModelRenderer> getBodyParts() {
      return ImmutableList.of(this.heart);
   }

   public void setLivingAnimations(T entityIn, float limbSwing, float limbSwingAmount, float partialTick) {
      this.heart.rotateAngleZ = entityIn.getShakeAngle(partialTick, -0.16F);
   }

   public void render(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
	   
   }
}