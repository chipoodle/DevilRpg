package com.chipoodle.devilrpg.client.render.entity.model;

import java.util.List;
import java.util.Random;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.util.HandSide;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WerewolfHumanModel <T extends PlayerEntity> extends PlayerModel<T> {
	   private List<ModelRenderer> modelRenderers = Lists.newArrayList();
	   public final ModelRenderer leftArmwear;
	   public final ModelRenderer rightArmwear;
	   public final ModelRenderer leftLegwear;
	   public final ModelRenderer rightLegwear;
	   public final ModelRenderer bodyWear;
	   private final ModelRenderer cape;
	   private final ModelRenderer deadmau5Head;
	   private final boolean smallArms;

	   public WerewolfHumanModel(float modelSize, boolean smallArmsIn) {
	      super(modelSize, smallArmsIn);
	      
	      this.smallArms = smallArmsIn;
	      this.deadmau5Head = new ModelRenderer(this, 24, 0);
	      this.deadmau5Head.addBox(-3.0F, -6.0F, -1.0F, 6.0F, 6.0F, 1.0F, modelSize);
	      this.cape = new ModelRenderer(this, 0, 0);
	      this.cape.setTexSize(64, 32);
	      this.cape.addBox(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F, modelSize);
	      if (smallArmsIn) {
	         this.leftArm = new ModelRenderer(this, 32, 48);
	         this.leftArm.addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, modelSize);
	         this.leftArm.setPos(5.0F, 2.5F, 0.0F);
	         this.rightArm = new ModelRenderer(this, 40, 16);
	         this.rightArm.addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, modelSize);
	         this.rightArm.setPos(-5.0F, 2.5F, 0.0F);
	         this.leftArmwear = new ModelRenderer(this, 48, 48);
	         this.leftArmwear.addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, modelSize + 0.25F);
	         this.leftArmwear.setPos(5.0F, 2.5F, 0.0F);
	         this.rightArmwear = new ModelRenderer(this, 40, 32);
	         this.rightArmwear.addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, modelSize + 0.25F);
	         this.rightArmwear.setPos(-5.0F, 2.5F, 10.0F);
	      } else {
	         this.leftArm = new ModelRenderer(this, 32, 48);
	         this.leftArm.addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, modelSize);
	         this.leftArm.setPos(5.0F, 2.0F, 0.0F);
	         this.leftArmwear = new ModelRenderer(this, 48, 48);
	         this.leftArmwear.addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, modelSize + 0.25F);
	         this.leftArmwear.setPos(5.0F, 2.0F, 0.0F);
	         this.rightArmwear = new ModelRenderer(this, 40, 32);
	         this.rightArmwear.addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, modelSize + 0.25F);
	         this.rightArmwear.setPos(-5.0F, 2.0F, 10.0F);
	      }

	      this.leftLeg = new ModelRenderer(this, 16, 48);
	      this.leftLeg.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, modelSize);
	      this.leftLeg.setPos(1.9F, 12.0F, 0.0F);
	      this.leftLegwear = new ModelRenderer(this, 0, 48);
	      this.leftLegwear.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, modelSize + 0.25F);
	      this.leftLegwear.setPos(1.9F, 12.0F, 0.0F);
	      this.rightLegwear = new ModelRenderer(this, 0, 32);
	      this.rightLegwear.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, modelSize + 0.25F);
	      this.rightLegwear.setPos(-1.9F, 12.0F, 0.0F);
	      this.bodyWear = new ModelRenderer(this, 16, 32);
	      this.bodyWear.addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, modelSize + 0.25F);
	      this.bodyWear.setPos(0.0F, 0.0F, 0.0F);
	   }

	   protected Iterable<ModelRenderer> bodyParts() {
	      return Iterables.concat(super.bodyParts(), ImmutableList.of(this.leftLegwear, this.rightLegwear, this.leftArmwear, this.rightArmwear, this.bodyWear));
	   }

	   public void renderEars(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn) {
	      this.deadmau5Head.copyFrom(this.head);
	      this.deadmau5Head.x = 0.0F;
	      this.deadmau5Head.y = 0.0F;
	      this.deadmau5Head.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn);
	   }

	   public void renderCloak(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn) {
	      this.cape.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn);
	   }

	   /**
	    * Sets this entity's model rotation angles
	    */
	   public void setupAnim(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
	      super.setupAnim(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
	      this.leftLegwear.copyFrom(this.leftLeg);
	      this.rightLegwear.copyFrom(this.rightLeg);
	      this.leftArmwear.copyFrom(this.leftArm);
	      this.rightArmwear.copyFrom(this.rightArm);
	      this.bodyWear.copyFrom(this.body);
	      if (entityIn.getItemBySlot(EquipmentSlotType.CHEST).isEmpty()) {
	         if (entityIn.isCrouching()) {
	            this.cape.z = 1.4F;
	            this.cape.y = 1.85F;
	         } else {
	            this.cape.z = 0.0F;
	            this.cape.y = 0.0F;
	         }
	      } else if (entityIn.isCrouching()) {
	         this.cape.z = 0.3F;
	         this.cape.y = 0.8F;
	      } else {
	         this.cape.z = -1.1F;
	         this.cape.y = -0.85F;
	      }

	   }

	   public void setAllVisible(boolean visible) {
	      super.setAllVisible(visible);
	      this.leftArmwear.visible = visible;
	      this.rightArmwear.visible = visible;
	      this.leftLegwear.visible = visible;
	      this.rightLegwear.visible = visible;
	      this.bodyWear.visible = visible;
	      this.cape.visible = visible;
	      this.deadmau5Head.visible = visible;
	   }

	   public void translateToHand(HandSide sideIn, MatrixStack matrixStackIn) {
	      ModelRenderer modelrenderer = this.getArm(sideIn);
	      if (this.smallArms) {
	         float f = 0.5F * (float)(sideIn == HandSide.RIGHT ? 1 : -1);
	         modelrenderer.x += f;
	         modelrenderer.translateAndRotate(matrixStackIn);
	         modelrenderer.x -= f;
	      } else {
	         modelrenderer.translateAndRotate(matrixStackIn);
	      }

	   }

	   public ModelRenderer getRandomModelPart(Random randomIn) {
	      return this.modelRenderers.get(randomIn.nextInt(this.modelRenderers.size()));
	   }

	   public void accept(ModelRenderer p_accept_1_) {
	      if (this.modelRenderers == null) {
	         this.modelRenderers = Lists.newArrayList();
	      }

	      this.modelRenderers.add(p_accept_1_);
	   }
	}
