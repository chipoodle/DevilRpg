package com.chipoodle.devilrpg.client.render.entity.model;

import java.util.List;
import java.util.Random;

import com.chipoodle.devilrpg.DevilRpg;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.model.ModelHelper;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.HandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WerewolfTransformedModel<T extends LivingEntity> extends BipedModel<T> {
	private List<ModelRenderer> modelRenderers = Lists.newArrayList();
	// public ModelRenderer bipedHead;
	public ModelRenderer Nose;
	public ModelRenderer Snout;
	public ModelRenderer TeethU;
	public ModelRenderer TeethL;
	public ModelRenderer Mouth;
	public ModelRenderer LEar;
	public ModelRenderer REar;
	public ModelRenderer Neck;
	public ModelRenderer Neck2;
	public ModelRenderer SideburnL;
	public ModelRenderer SideburnR;
	// public ModelRenderer bipedBody;
	public ModelRenderer Abdomen;
	public ModelRenderer TailA;
	public ModelRenderer TailC;
	public ModelRenderer TailB;
	public ModelRenderer TailD;
	public ModelRenderer RLegA;
	public ModelRenderer RFoot;
	public ModelRenderer RLegB;
	public ModelRenderer RLegC;
	public ModelRenderer LLegB;
	public ModelRenderer LFoot;
	public ModelRenderer LLegC;
	public ModelRenderer LLegA;
	
	// public ModelRenderer bipedRightArm;
	public ModelRenderer rArmA;
	public ModelRenderer rArmB;
	public ModelRenderer rArmC;
	public ModelRenderer rArmD;
	// public ModelRenderer bipedLeftArm;
	public ModelRenderer lArmA;
	public ModelRenderer lArmB;
	public ModelRenderer lArmC;
	public ModelRenderer lArmD;
	public ModelRenderer RFinger1;
	public ModelRenderer RFinger2;
	public ModelRenderer RFinger3;
	public ModelRenderer RFinger4;
	public ModelRenderer RFinger5;
	public ModelRenderer LFinger1;
	public ModelRenderer LFinger2;
	public ModelRenderer LFinger3;
	public ModelRenderer LFinger4;
	public ModelRenderer LFinger5;
	
	ModelRenderer lArmEmbedB;
	ModelRenderer rArmEmbedB;

	public WerewolfTransformedModel(float modelSize, boolean smallArmsIn) {
		super(modelSize);
		textureWidth = 64;
		textureHeight = 128;

		bipedHead = new ModelRenderer(this, 0, 0);
		bipedHead.addBox(-4F, -3F, -6F, 8, 8, 6);
		bipedHead.setRotationPoint(0F, -8F, -6F);
		bipedHead.setTextureSize(64, 128);

		Nose = new ModelRenderer(this, 44, 33);
		Nose.addBox(-1.5F, -1.7F, -12.3F, 3, 2, 7);
		Nose.setRotationPoint(0F, -8F, -6F);
		setRotation(Nose, 0.2792527F, 0F, 0F);

		Snout = new ModelRenderer(this, 0, 25);
		Snout.addBox(-2F, 2F, -12F, 4, 2, 6);
		Snout.setRotationPoint(0F, -8F, -6F);

		TeethU = new ModelRenderer(this, 46, 18);
		TeethU.addBox(-2F, 4.01F, -12F, 4, 2, 5);
		TeethU.setRotationPoint(0F, -8F, -6F);

		TeethL = new ModelRenderer(this, 20, 109);
		TeethL.addBox(-1.5F, -12.5F, 2.01F, 3, 5, 2);
		TeethL.setRotationPoint(0F, -8F, -6F);
		setRotation(TeethL, 2.530727F, 0F, 0F);

		Mouth = new ModelRenderer(this, 42, 69);
		Mouth.addBox(-1.5F, -12.5F, 0F, 3, 9, 2);
		Mouth.setRotationPoint(0F, -8F, -6F);
		setRotation(Mouth, 2.530727F, 0F, 0F);

		LEar = new ModelRenderer(this, 13, 14);
		LEar.addBox(0.5F, -7.5F, -1F, 3, 5, 1);
		LEar.setRotationPoint(0F, -8F, -6F);
		setRotation(LEar, 0F, 0F, 0.1745329F);

		REar = new ModelRenderer(this, 22, 0);
		REar.addBox(-3.5F, -7.5F, -1F, 3, 5, 1);
		REar.setRotationPoint(0F, -8F, -6F);
		setRotation(REar, 0F, 0F, -0.1745329F);

		Neck = new ModelRenderer(this, 28, 0);
		Neck.addBox(-3.5F, -3F, -7F, 7, 8, 7);
		Neck.setRotationPoint(0F, -5F, -2F);
		setRotation(Neck, -0.6025001F, 0F, 0F);

		Neck2 = new ModelRenderer(this, 0, 14);
		Neck2.addBox(-1.5F, -2F, -5F, 3, 4, 7);
		Neck2.setRotationPoint(0F, -1F, -6F);
		setRotation(Neck2, -0.4537856F, 0F, 0F);

		SideburnL = new ModelRenderer(this, 28, 33);
		SideburnL.addBox(3F, 0F, -2F, 2, 6, 6);
		SideburnL.setRotationPoint(0F, -8F, -6F);
		setRotation(SideburnL, -0.2094395F, 0.418879F, -0.0872665F);

		SideburnR = new ModelRenderer(this, 28, 45);
		SideburnR.addBox(-5F, 0F, -2F, 2, 6, 6);
		SideburnR.setRotationPoint(0F, -8F, -6F);
		setRotation(SideburnR, -0.2094395F, -0.418879F, 0.0872665F);

		bipedBody = new ModelRenderer(this, 20, 15);
		bipedBody.addBox(-4F, 0F, -7F, 8, 8, 10);
		bipedBody.setRotationPoint(0F, -6F, -2.5F);
		setRotation(bipedBody, 0.641331F, 0F, 0F);

		Abdomen = new ModelRenderer(this, 0, 40);
		Abdomen.addBox(-3F, -8F, -8F, 6, 14, 8);
		Abdomen.setRotationPoint(0F, 4.5F, 5F);
		setRotation(Abdomen, 0.2695449F, 0F, 0F);

		TailA = new ModelRenderer(this, 52, 42);
		TailA.addBox(-1.5F, -1F, -2F, 3, 4, 3);
		TailA.setRotationPoint(0F, 9.5F, 6F);
		setRotation(TailA, 1.064651F, 0F, 0F);

		TailC = new ModelRenderer(this, 48, 59);
		TailC.addBox(-2F, 6.8F, -4.6F, 4, 6, 4);
		TailC.setRotationPoint(0F, 9.5F, 6F);
		setRotation(TailC, 1.099557F, 0F, 0F);

		TailB = new ModelRenderer(this, 48, 49);
		TailB.addBox(-2F, 2F, -2F, 4, 6, 4);
		TailB.setRotationPoint(0F, 9.5F, 6F);
		setRotation(TailB, 0.7504916F, 0F, 0F);

		TailD = new ModelRenderer(this, 52, 69);
		TailD.addBox(-1.5F, 9.8F, -4.1F, 3, 5, 3);
		TailD.setRotationPoint(0F, 9.5F, 6F);
		setRotation(TailD, 1.099557F, 0F, 0F);

		RLegA = new ModelRenderer(this, 12, 64);
		RLegA.addBox(-2.5F, -1.5F, -3.5F, 3, 8, 5);
		RLegA.setRotationPoint(-3F, 9.5F, 3F);
		setRotation(RLegA, -0.8126625F, 0F, 0F);

		RFoot = new ModelRenderer(this, 14, 93);
		RFoot.addBox(-2.506667F, 12.5F, -5F, 3, 2, 3);
		RFoot.setRotationPoint(-3F, 9.5F, 3F);

		RLegB = new ModelRenderer(this, 14, 76);
		RLegB.addBox(-1.9F, 4.2F, 0.5F, 2, 2, 5);
		RLegB.setRotationPoint(-3F, 9.5F, 3F);
		setRotation(RLegB, -0.8445741F, 0F, 0F);

		RLegC = new ModelRenderer(this, 14, 83);
		RLegC.addBox(-2F, 6.2F, 0.5F, 2, 8, 2);
		RLegC.setRotationPoint(-3F, 9.5F, 3F);
		setRotation(RLegC, -0.2860688F, 0F, 0F);

		LLegB = new ModelRenderer(this, 0, 76);
		LLegB.addBox(-0.1F, 4.2F, 0.5F, 2, 2, 5);
		LLegB.setRotationPoint(3F, 9.5F, 3F);
		setRotation(LLegB, -0.8445741F, 0F, 0F);

		LFoot = new ModelRenderer(this, 0, 93);
		LFoot.addBox(-0.5066667F, 12.5F, -5F, 3, 2, 3);
		LFoot.setRotationPoint(3F, 9.5F, 3F);

		LLegC = new ModelRenderer(this, 0, 83);
		LLegC.addBox(0F, 6.2F, 0.5F, 2, 8, 2);
		LLegC.setRotationPoint(3F, 9.5F, 3F);
		setRotation(LLegC, -0.2860688F, 0F, 0F);

		LLegA = new ModelRenderer(this, 0, 64);
		LLegA.addBox(-0.5F, -1.5F, -3.5F, 3, 8, 5);
		LLegA.setRotationPoint(3F, 9.5F, 3F);
		setRotation(LLegA, -0.8126625F, 0F, 0F);

		rArmA = new ModelRenderer(this, 0, 108);
		rArmA.addBox(-5F, -3F, -2F, 5, 5, 5);
		rArmA.setRotationPoint(-4F, -4F, -2F);
		setRotation(rArmA, 0.6320364F, 0F, 0F);
		
		rArmB = new ModelRenderer(this, 48, 77);
		rArmB.addBox(-3.5F, 1F, -1.5F, 4, 8, 4);
		rArmB.setRotationPoint(-4F, -4F, -2F);
		setRotation(rArmB, 0.2617994F, 0F, 0.3490659F);
		
		rArmC = new ModelRenderer(this, 48, 112);
		rArmC.addBox(-6F, 5F, 3F, 4, 7, 4);
		rArmC.setRotationPoint(-4F, -4F, -2F);
		setRotation(rArmC, -0.3490659F, 0F, 0F);
		
		rArmD = new ModelRenderer(this, 32, 118);
		rArmD.addBox(-6F, 12.5F, -1.5F, 4, 3, 4);
		rArmD.setRotationPoint(-4F, -4F, -2F);

		lArmA = new ModelRenderer(this, 0, 98);
		lArmA.addBox(0F, -3F, -2F, 5, 5, 5);
		lArmA.setRotationPoint(4F, -4F, -2F);
		setRotation(lArmA, 0.6320364F, 0F, 0F);
		
		lArmB = new ModelRenderer(this, 48, 89);
		lArmB.addBox(-0.5F, 1F, -1.5F, 4, 8, 4);
		lArmB.setRotationPoint(4F, -4F, -2F);
		setRotation(lArmB, 0.2617994F, 0F, -0.3490659F);
		
		lArmC = new ModelRenderer(this, 48, 101);
		lArmC.addBox(2F, 5F, 3F, 4, 7, 4);
		lArmC.setRotationPoint(4F, -4F, -2F);
		setRotation(lArmC, -0.3490659F, 0F, 0F);

		lArmD = new ModelRenderer(this, 32, 111);
		lArmD.addBox(2F, 12.5F, -1.5F, 4, 3, 4);
		lArmD.setRotationPoint(4F, -4F, -2F);

		RFinger1 = new ModelRenderer(this, 8, 120);
		RFinger1.addBox(-0.5F, 0F, -0.5F, 1, 3, 1);
		RFinger1.setRotationPoint(-6.5F, 11.5F, -0.5F);

		RFinger1 = new ModelRenderer(this, 8, 120);
		RFinger1.addBox(-3F, 15.5F, 1F, 1, 3, 1);
		RFinger1.setRotationPoint(-4F, -4F, -2F);

		RFinger2 = new ModelRenderer(this, 12, 124);
		RFinger2.addBox(-3.5F, 15.5F, -1.5F, 1, 3, 1);
		RFinger2.setRotationPoint(-4F, -4F, -2F);

		RFinger3 = new ModelRenderer(this, 12, 119);
		RFinger3.addBox(-4.8F, 15.5F, -1.5F, 1, 4, 1);
		RFinger3.setRotationPoint(-4F, -4F, -2F);

		RFinger4 = new ModelRenderer(this, 16, 119);
		RFinger4.addBox(-6F, 15.5F, -0.5F, 1, 4, 1);
		RFinger4.setRotationPoint(-4F, -4F, -2F);

		RFinger5 = new ModelRenderer(this, 16, 124);
		RFinger5.addBox(-6F, 15.5F, 1F, 1, 3, 1);
		RFinger5.setRotationPoint(-4F, -4F, -2F);

		LFinger1 = new ModelRenderer(this, 8, 124);
		LFinger1.addBox(2F, 15.5F, 1F, 1, 3, 1);
		LFinger1.setRotationPoint(4F, -4F, -2F);

		LFinger2 = new ModelRenderer(this, 0, 124);
		LFinger2.addBox(2.5F, 15.5F, -1.5F, 1, 3, 1);
		LFinger2.setRotationPoint(4F, -4F, -2F);

		LFinger3 = new ModelRenderer(this, 0, 119);
		LFinger3.addBox(3.8F, 15.5F, -1.5F, 1, 4, 1);
		LFinger3.setRotationPoint(4F, -4F, -2F);

		LFinger4 = new ModelRenderer(this, 4, 119);
		LFinger4.addBox(5F, 15.5F, -0.5F, 1, 4, 1);
		LFinger4.setRotationPoint(4F, -4F, -2F);

		LFinger5 = new ModelRenderer(this, 4, 124);
		LFinger5.addBox(5F, 15.5F, 1F, 1, 3, 1);
		LFinger5.setRotationPoint(4F, -4F, -2F);
		
		/*-------------------------------------*/
		bipedLeftArm = new ModelRenderer(this, 0, 98);
		bipedLeftArm.addBox(0F, -3F, -2F, 5, 5, 5);
		bipedLeftArm.setRotationPoint(4F, -4F, -2F);
		setRotation(bipedLeftArm, 0.6320364F, 0F, 0F);
		
		bipedRightArm = new ModelRenderer(this, 0, 108);
		bipedRightArm.addBox(-5F, -3F, -2F, 5, 5, 5);
		bipedRightArm.setRotationPoint(-4F, -4F, -2F);
		setRotation(bipedRightArm, 0.6320364F, 0F, 0F);
		
		lArmEmbedB = new ModelRenderer(this, 48, 89);
		lArmEmbedB.addBox(-0.5F, 2F, -1.5F, 4, 8, 4);
		lArmEmbedB.setRotationPoint(4F, -4F, -2F);
		setRotation(lArmEmbedB, 0.2617994F, 0F, -0.3490659F);
		
		rArmEmbedB = new ModelRenderer(this, 48, 77);
		rArmEmbedB.addBox(-3.5F, 2F, -1.5F, 4, 8, 4);
		rArmEmbedB.setRotationPoint(-4F, -4F, -2F);
		setRotation(rArmEmbedB, 0.2617994F, 0F, 0.3490659F);

	}

	protected Iterable<ModelRenderer> getBodyParts() {
		return // Iterables.concat(

		// super.getBodyParts(),

		ImmutableList.of(
				this.bipedLeftArm,
				this.bipedRightArm,
				//lArmEmbedB,
				//rArmEmbedB,
				
				this.bipedBody, this.Abdomen, this.TailA, this.TailB, this.TailC, this.TailD, this.RLegA,
				this.RFoot, this.RLegB, this.RLegC, this.LLegA, this.LFoot, this.LLegB, this.LLegC, this.rArmA,
				this.rArmB, this.rArmC, this.lArmA, this.lArmB, this.lArmC, this.lArmD, this.rArmD, this.RFinger1,
				this.RFinger2, this.RFinger2, this.RFinger3, this.RFinger4, this.RFinger5, this.LFinger1, this.LFinger2,
				this.LFinger2, this.LFinger3, this.LFinger4, this.LFinger5);
		// );
	}

	protected Iterable<ModelRenderer> getHeadParts() {
		return ImmutableList.of(this.bipedHead, /* this.bipedHeadwear, */ this.Nose, this.Snout, this.TeethU,
				this.TeethL, this.Mouth, this.LEar, this.REar, this.Neck, this.Neck2, this.SideburnL, this.SideburnR);
	}

	private void setRotation(ModelRenderer model, float x, float y, float z) {
		model.rotateAngleX = x;
		model.rotateAngleY = y;
		model.rotateAngleZ = z;
	}

	public void setRotationAngles(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch) {

		// super.setRotationAngles(entityIn, limbSwing, limbSwingAmount, ageInTicks,
		// netHeadYaw, headPitch);
		
		//HELPER
		
		
		float radianF = 57.29578F;
		float RLegXRot = MathHelper.cos((limbSwing * 0.6662F) + 3.141593F) * 0.8F * limbSwingAmount;
		float LLegXRot = MathHelper.cos(limbSwing * 0.6662F) * 0.8F * limbSwingAmount;

		bipedHead.rotateAngleY = netHeadYaw / radianF; // this moves head to left and right
		bipedHead.rotateAngleX = headPitch / radianF; // this moves head up and down

		if (!isSneak) {
			bipedHead.rotationPointY = -8F;
			bipedHead.rotationPointZ = -6F;
			bipedHead.rotateAngleX = headPitch / radianF;
			Neck.rotateAngleX = -34 / radianF;
			Neck.rotationPointY = -5F;
			Neck.rotationPointZ = -2F;
			Neck2.rotationPointY = -1F;
			Neck2.rotationPointZ = -6F;
			bipedBody.rotationPointY = -6F;
			bipedBody.rotationPointZ = -2.5F;
			bipedBody.rotateAngleX = 36 / radianF;
			Abdomen.rotateAngleX = 15 / radianF;
			LLegA.rotationPointZ = 3F;

			lArmA.rotationPointY = -4F;
			lArmA.rotationPointZ = -2F;

			TailA.rotationPointY = 9.5F;
			TailA.rotationPointZ = 6F;

		} else {
			bipedHead.rotationPointY = 0F;
			bipedHead.rotationPointZ = -11F;
			bipedHead.rotateAngleX = (15F + headPitch) / radianF;

			Neck.rotateAngleX = -10 / radianF;
			Neck.rotationPointY = 2F;
			Neck.rotationPointZ = -6F;
			Neck2.rotationPointY = 9F;
			Neck2.rotationPointZ = -9F;
			bipedBody.rotationPointY = 1F;
			bipedBody.rotationPointZ = -7.5F;
			bipedBody.rotateAngleX = 60 / radianF;
			Abdomen.rotateAngleX = 75 / radianF;
			LLegA.rotationPointZ = 7F;
			lArmA.rotationPointY = 4.5F; 
			lArmA.rotationPointZ = -6F;
			TailA.rotationPointY = 7.5F;
			TailA.rotationPointZ = 10F;
		}

		Nose.rotationPointY = bipedHead.rotationPointY;
		Snout.rotationPointY = bipedHead.rotationPointY;
		TeethU.rotationPointY = bipedHead.rotationPointY;
		LEar.rotationPointY = bipedHead.rotationPointY;
		REar.rotationPointY = bipedHead.rotationPointY;
		TeethL.rotationPointY = bipedHead.rotationPointY;
		Mouth.rotationPointY = bipedHead.rotationPointY;
		SideburnL.rotationPointY = bipedHead.rotationPointY;
		SideburnR.rotationPointY = bipedHead.rotationPointY;

		Nose.rotationPointZ = bipedHead.rotationPointZ;
		Snout.rotationPointZ = bipedHead.rotationPointZ;
		TeethU.rotationPointZ = bipedHead.rotationPointZ;
		LEar.rotationPointZ = bipedHead.rotationPointZ;
		REar.rotationPointZ = bipedHead.rotationPointZ;
		TeethL.rotationPointZ = bipedHead.rotationPointZ;
		Mouth.rotationPointZ = bipedHead.rotationPointZ;
		SideburnL.rotationPointZ = bipedHead.rotationPointZ;
		SideburnR.rotationPointZ = bipedHead.rotationPointZ;

		lArmB.rotationPointY = lArmA.rotationPointY;
		lArmC.rotationPointY = lArmA.rotationPointY;
		lArmD.rotationPointY = lArmA.rotationPointY;
		LFinger1.rotationPointY = lArmA.rotationPointY;
		LFinger2.rotationPointY = lArmA.rotationPointY;
		LFinger3.rotationPointY = lArmA.rotationPointY;
		LFinger4.rotationPointY = lArmA.rotationPointY;
		LFinger5.rotationPointY = lArmA.rotationPointY;
		rArmA.rotationPointY = lArmA.rotationPointY;
		rArmB.rotationPointY = lArmA.rotationPointY;
		rArmC.rotationPointY = lArmA.rotationPointY;
		rArmD.rotationPointY = lArmA.rotationPointY;
		RFinger1.rotationPointY = lArmA.rotationPointY;
		RFinger2.rotationPointY = lArmA.rotationPointY;
		RFinger3.rotationPointY = lArmA.rotationPointY;
		RFinger4.rotationPointY = lArmA.rotationPointY;
		RFinger5.rotationPointY = lArmA.rotationPointY;

		lArmB.rotationPointZ = lArmA.rotationPointZ;
		lArmC.rotationPointZ = lArmA.rotationPointZ;
		lArmD.rotationPointZ = lArmA.rotationPointZ;
		LFinger1.rotationPointZ = lArmA.rotationPointZ;
		LFinger2.rotationPointZ = lArmA.rotationPointZ;
		LFinger3.rotationPointZ = lArmA.rotationPointZ;
		LFinger4.rotationPointZ = lArmA.rotationPointZ;
		LFinger5.rotationPointZ = lArmA.rotationPointZ;
		rArmA.rotationPointZ = lArmA.rotationPointZ;
		rArmB.rotationPointZ = lArmA.rotationPointZ;
		rArmC.rotationPointZ = lArmA.rotationPointZ;
		rArmD.rotationPointZ = lArmA.rotationPointZ;
		RFinger1.rotationPointZ = lArmA.rotationPointZ;
		RFinger2.rotationPointZ = lArmA.rotationPointZ;
		RFinger3.rotationPointZ = lArmA.rotationPointZ;
		RFinger4.rotationPointZ = lArmA.rotationPointZ;
		RFinger5.rotationPointZ = lArmA.rotationPointZ;

		RLegA.rotationPointZ = LLegA.rotationPointZ;
		RLegB.rotationPointZ = LLegA.rotationPointZ;
		RLegC.rotationPointZ = LLegA.rotationPointZ;
		RFoot.rotationPointZ = LLegA.rotationPointZ;
		LLegB.rotationPointZ = LLegA.rotationPointZ;
		LLegC.rotationPointZ = LLegA.rotationPointZ;
		LFoot.rotationPointZ = LLegA.rotationPointZ;

		TailB.rotationPointY = TailA.rotationPointY;
		TailB.rotationPointZ = TailA.rotationPointZ;
		TailC.rotationPointY = TailA.rotationPointY;
		TailC.rotationPointZ = TailA.rotationPointZ;
		TailD.rotationPointY = TailA.rotationPointY;
		TailD.rotationPointZ = TailA.rotationPointZ;

		Nose.rotateAngleY = bipedHead.rotateAngleY;
		Snout.rotateAngleY = bipedHead.rotateAngleY;
		TeethU.rotateAngleY = bipedHead.rotateAngleY;
		LEar.rotateAngleY = bipedHead.rotateAngleY;
		REar.rotateAngleY = bipedHead.rotateAngleY;
		TeethL.rotateAngleY = bipedHead.rotateAngleY;
		Mouth.rotateAngleY = bipedHead.rotateAngleY;

		TeethL.rotateAngleX = bipedHead.rotateAngleX + 2.530727F;
		Mouth.rotateAngleX = bipedHead.rotateAngleX + 2.530727F;

		SideburnL.rotateAngleX = -0.2094395F + bipedHead.rotateAngleX;
		SideburnL.rotateAngleY = 0.418879F + bipedHead.rotateAngleY;
		SideburnR.rotateAngleX = -0.2094395F + bipedHead.rotateAngleX;
		SideburnR.rotateAngleY = -0.418879F + bipedHead.rotateAngleY;

		Nose.rotateAngleX = 0.2792527F + bipedHead.rotateAngleX;
		Snout.rotateAngleX = bipedHead.rotateAngleX;
		TeethU.rotateAngleX = bipedHead.rotateAngleX;

		LEar.rotateAngleX = bipedHead.rotateAngleX;
		REar.rotateAngleX = bipedHead.rotateAngleX;

		RLegA.rotateAngleX = -0.8126625F + RLegXRot;
		RLegB.rotateAngleX = -0.8445741F + RLegXRot;
		RLegC.rotateAngleX = -0.2860688F + RLegXRot;
		RFoot.rotateAngleX = RLegXRot;

		LLegA.rotateAngleX = -0.8126625F + LLegXRot;
		LLegB.rotateAngleX = -0.8445741F + LLegXRot;
		LLegC.rotateAngleX = -0.2860688F + LLegXRot;
		LFoot.rotateAngleX = LLegXRot;

		rArmA.rotateAngleZ = -(MathHelper.cos(ageInTicks * 0.09F) * 0.05F) + 0.05F;
		lArmA.rotateAngleZ = (MathHelper.cos(ageInTicks * 0.09F) * 0.05F) - 0.05F;
		rArmA.rotateAngleX = LLegXRot;// MathHelper.sin(f2 * 0.067F) * 0.05F;
		lArmA.rotateAngleX = RLegXRot;// MathHelper.sin(f2 * 0.067F) * 0.05F;

		rArmB.rotateAngleZ = 0.3490659F + rArmA.rotateAngleZ;
		lArmB.rotateAngleZ = -0.3490659F + lArmA.rotateAngleZ;
		rArmB.rotateAngleX = 0.2617994F + rArmA.rotateAngleX;
		lArmB.rotateAngleX = 0.2617994F + lArmA.rotateAngleX;

		rArmC.rotateAngleZ = rArmA.rotateAngleZ;
		lArmC.rotateAngleZ = lArmA.rotateAngleZ;
		rArmC.rotateAngleX = -0.3490659F + rArmA.rotateAngleX;
		lArmC.rotateAngleX = -0.3490659F + lArmA.rotateAngleX;

		rArmD.rotateAngleZ = rArmA.rotateAngleZ;
		lArmD.rotateAngleZ = lArmA.rotateAngleZ;
		rArmD.rotateAngleX = rArmA.rotateAngleX;
		lArmD.rotateAngleX = lArmA.rotateAngleX;

		RFinger1.rotateAngleX = rArmA.rotateAngleX;
		RFinger2.rotateAngleX = rArmA.rotateAngleX;
		RFinger3.rotateAngleX = rArmA.rotateAngleX;
		RFinger4.rotateAngleX = rArmA.rotateAngleX;
		RFinger5.rotateAngleX = rArmA.rotateAngleX;

		LFinger1.rotateAngleX = lArmA.rotateAngleX;
		LFinger2.rotateAngleX = lArmA.rotateAngleX;
		LFinger3.rotateAngleX = lArmA.rotateAngleX;
		LFinger4.rotateAngleX = lArmA.rotateAngleX;
		LFinger5.rotateAngleX = lArmA.rotateAngleX;

		RFinger1.rotateAngleZ = rArmA.rotateAngleZ;
		RFinger2.rotateAngleZ = rArmA.rotateAngleZ;
		RFinger3.rotateAngleZ = rArmA.rotateAngleZ;
		RFinger4.rotateAngleZ = rArmA.rotateAngleZ;
		RFinger5.rotateAngleZ = rArmA.rotateAngleZ;

		LFinger1.rotateAngleZ = lArmA.rotateAngleZ;
		LFinger2.rotateAngleZ = lArmA.rotateAngleZ;
		LFinger3.rotateAngleZ = lArmA.rotateAngleZ;
		LFinger4.rotateAngleZ = lArmA.rotateAngleZ;
		LFinger5.rotateAngleZ = lArmA.rotateAngleZ;

		
		setRotationAnglesHelper(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		
		
	}

	protected void setRotationAnglesHelper(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch) {

		boolean flagelytra = entityIn.getTicksElytraFlying() > 4;
		boolean flagSwimming = entityIn.isActualySwimming();
		this.bipedHead.rotateAngleY = netHeadYaw * ((float) Math.PI / 180F);
		if (flagelytra) {
			this.bipedHead.rotateAngleX = (-(float) Math.PI / 4F);
		} else if (this.swimAnimation > 0.0F) {
			if (flagSwimming) {
				this.bipedHead.rotateAngleX = this.rotLerpRad(this.swimAnimation, this.bipedHead.rotateAngleX,
						(-(float) Math.PI / 4F));
			} else {
				this.bipedHead.rotateAngleX = this.rotLerpRad(this.swimAnimation, this.bipedHead.rotateAngleX,
						headPitch * ((float) Math.PI / 180F));
			}
		} else {
			this.bipedHead.rotateAngleX = headPitch * ((float) Math.PI / 180F);
		}

		this.bipedBody.rotateAngleY = 0.0F;
		
		this.bipedRightArm.rotationPointZ = 0.0F;
		this.bipedRightArm.rotationPointX = -5.0F;
		this.bipedLeftArm.rotationPointZ = 0.0F;
		this.bipedLeftArm.rotationPointX = 5.0F;

		float f = 1.0F;
		if (flagelytra) {
			f = (float) entityIn.getMotion().lengthSquared();
			f = f / 0.2F;
			f = f * f * f;
		}

		if (f < 1.0F) {
			f = 1.0F;
		}
		
		//animate arms while moving
		this.bipedRightArm.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 2.0F * limbSwingAmount * 0.5F / f;
		this.bipedLeftArm.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount * 0.5F / f;
		this.bipedRightArm.rotateAngleZ = 0.0F;
		this.bipedLeftArm.rotateAngleZ = 0.0F;
		
		
		
		
		this.bipedRightLeg.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount / f;
		this.bipedLeftLeg.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount / f;
		this.bipedRightLeg.rotateAngleY = 0.0F;
		this.bipedLeftLeg.rotateAngleY = 0.0F;
		this.bipedRightLeg.rotateAngleZ = 0.0F;
		this.bipedLeftLeg.rotateAngleZ = 0.0F;
		if (this.isSitting) {
			this.bipedRightArm.rotateAngleX += (-(float) Math.PI / 5F);
			this.bipedLeftArm.rotateAngleX += (-(float) Math.PI / 5F);
			this.bipedRightLeg.rotateAngleX = -1.4137167F;
			this.bipedRightLeg.rotateAngleY = ((float) Math.PI / 10F);
			this.bipedRightLeg.rotateAngleZ = 0.07853982F;
			this.bipedLeftLeg.rotateAngleX = -1.4137167F;
			this.bipedLeftLeg.rotateAngleY = (-(float) Math.PI / 10F);
			this.bipedLeftLeg.rotateAngleZ = -0.07853982F;
		}

		this.bipedRightArm.rotateAngleY = 0.0F;
		this.bipedLeftArm.rotateAngleY = 0.0F;
		
		
		boolean flag2 = entityIn.getPrimaryHand() == HandSide.RIGHT;
		boolean flag3 = flag2 ? this.leftArmPose.func_241657_a_() : this.rightArmPose.func_241657_a_();
		if (flag2 != flag3) {
			this.func_241655_c_(entityIn);
			this.func_241654_b_(entityIn);
		} else {
			this.func_241654_b_(entityIn);
			this.func_241655_c_(entityIn);
		}

		
		//OnSwingAttack
		this.func_230486_a_(entityIn, ageInTicks);

		//animate arms while idle
		//ModelHelper.func_239101_a_(this.bipedRightArm, this.bipedLeftArm, ageInTicks);

		animateSwimming(entityIn, limbSwing);
		this.bipedHeadwear.copyModelAngles(this.bipedHead);
	}

	/**
	 * @param entityIn
	 * @param limbSwing
	 */
	private void animateSwimming(T entityIn, float limbSwing) {
		if (this.swimAnimation > 0.0F) {
			float f1 = limbSwing % 26.0F;
			HandSide handside = this.getMainHand(entityIn);
			float f2 = handside == HandSide.RIGHT && this.swingProgress > 0.0F ? 0.0F : this.swimAnimation;
			float f3 = handside == HandSide.LEFT && this.swingProgress > 0.0F ? 0.0F : this.swimAnimation;
			if (f1 < 14.0F) {
				this.bipedLeftArm.rotateAngleX = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleX, 0.0F);
				this.bipedRightArm.rotateAngleX = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleX, 0.0F);
				this.bipedLeftArm.rotateAngleY = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleY, (float) Math.PI);
				this.bipedRightArm.rotateAngleY = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleY, (float) Math.PI);
				this.bipedLeftArm.rotateAngleZ = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleZ,
						(float) Math.PI + 1.8707964F * this.getArmAngleSq(f1) / this.getArmAngleSq(14.0F));
				this.bipedRightArm.rotateAngleZ = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleZ,
						(float) Math.PI - 1.8707964F * this.getArmAngleSq(f1) / this.getArmAngleSq(14.0F));
			} else if (f1 >= 14.0F && f1 < 22.0F) {
				float f6 = (f1 - 14.0F) / 8.0F;
				this.bipedLeftArm.rotateAngleX = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleX,
						((float) Math.PI / 2F) * f6);
				this.bipedRightArm.rotateAngleX = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleX,
						((float) Math.PI / 2F) * f6);
				this.bipedLeftArm.rotateAngleY = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleY, (float) Math.PI);
				this.bipedRightArm.rotateAngleY = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleY, (float) Math.PI);
				this.bipedLeftArm.rotateAngleZ = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleZ,
						5.012389F - 1.8707964F * f6);
				this.bipedRightArm.rotateAngleZ = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleZ,
						1.2707963F + 1.8707964F * f6);
			} else if (f1 >= 22.0F && f1 < 26.0F) {
				float f4 = (f1 - 22.0F) / 4.0F;
				this.bipedLeftArm.rotateAngleX = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleX,
						((float) Math.PI / 2F) - ((float) Math.PI / 2F) * f4);
				this.bipedRightArm.rotateAngleX = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleX,
						((float) Math.PI / 2F) - ((float) Math.PI / 2F) * f4);
				this.bipedLeftArm.rotateAngleY = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleY, (float) Math.PI);
				this.bipedRightArm.rotateAngleY = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleY, (float) Math.PI);
				this.bipedLeftArm.rotateAngleZ = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleZ, (float) Math.PI);
				this.bipedRightArm.rotateAngleZ = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleZ, (float) Math.PI);
			}

			float f7 = 0.3F;
			float f5 = 0.33333334F;
			this.bipedLeftLeg.rotateAngleX = MathHelper.lerp(this.swimAnimation, this.bipedLeftLeg.rotateAngleX,
					0.3F * MathHelper.cos(limbSwing * 0.33333334F + (float) Math.PI));
			this.bipedRightLeg.rotateAngleX = MathHelper.lerp(this.swimAnimation, this.bipedRightLeg.rotateAngleX,
					0.3F * MathHelper.cos(limbSwing * 0.33333334F));
		}
	}

	private float getArmAngleSq(float limbSwing) {
		return -65.0F * limbSwing + limbSwing * limbSwing;
	}

	private void func_241654_b_(T p_241654_1_) {
		switch (this.rightArmPose) {
		case EMPTY:
			this.rArmD.rotateAngleY = 0.0F;
			break;
		case BLOCK:
			this.rArmD.rotateAngleX = this.rArmD.rotateAngleX * 0.5F - 0.9424779F;
			this.rArmD.rotateAngleY = (-(float) Math.PI / 6F);
			break;
		case ITEM:
			this.rArmD.rotateAngleX = this.rArmD.rotateAngleX * 0.5F - ((float) Math.PI / 10F);
			this.rArmD.rotateAngleY = 0.0F;
			break;
		case THROW_SPEAR:
			this.rArmD.rotateAngleX = this.rArmD.rotateAngleX * 0.5F - (float) Math.PI;
			this.rArmD.rotateAngleY = 0.0F;
			break;
		case BOW_AND_ARROW:
			this.rArmD.rotateAngleY = -0.1F + this.bipedHead.rotateAngleY;
			this.lArmD.rotateAngleY = 0.1F + this.bipedHead.rotateAngleY + 0.4F;
			this.rArmD.rotateAngleX = (-(float) Math.PI / 2F) + this.bipedHead.rotateAngleX;
			this.lArmD.rotateAngleX = (-(float) Math.PI / 2F) + this.bipedHead.rotateAngleX;
			break;
		case CROSSBOW_CHARGE:
			ModelHelper.func_239102_a_(this.rArmD, this.lArmD, p_241654_1_, true);
			break;
		case CROSSBOW_HOLD:
			ModelHelper.func_239104_a_(this.rArmD, this.lArmD, this.bipedHead, true);
		}

	}

	private void func_241655_c_(T p_241655_1_) {
		switch (this.leftArmPose) {
		case EMPTY:
			this.lArmD.rotateAngleY = 0.0F;
			break;
		case BLOCK:
			this.lArmD.rotateAngleX = this.lArmD.rotateAngleX * 0.5F - 0.9424779F;
			this.lArmD.rotateAngleY = ((float) Math.PI / 6F);
			break;
		case ITEM:
			this.lArmD.rotateAngleX = this.lArmD.rotateAngleX * 0.5F - ((float) Math.PI / 10F);
			this.lArmD.rotateAngleY = 0.0F;
			break;
		case THROW_SPEAR:
			this.lArmD.rotateAngleX = this.lArmD.rotateAngleX * 0.5F - (float) Math.PI;
			this.lArmD.rotateAngleY = 0.0F;
			break;
		case BOW_AND_ARROW:
			this.rArmD.rotateAngleY = -0.1F + this.bipedHead.rotateAngleY - 0.4F;
			this.lArmD.rotateAngleY = 0.1F + this.bipedHead.rotateAngleY;
			this.rArmD.rotateAngleX = (-(float) Math.PI / 2F) + this.bipedHead.rotateAngleX;
			this.lArmD.rotateAngleX = (-(float) Math.PI / 2F) + this.bipedHead.rotateAngleX;
			break;
		case CROSSBOW_CHARGE:
			ModelHelper.func_239102_a_(this.rArmD, this.lArmD, p_241655_1_, false);
			break;
		case CROSSBOW_HOLD:
			ModelHelper.func_239104_a_(this.rArmD, this.lArmD, this.bipedHead, false);
		}
	}

	
	protected void func_230486_a_(T entityin, float ageInTicks) {
		if (!(this.swingProgress <= 0.0F)) {
			HandSide handside = this.getMainHand(entityin);
			ModelRenderer modelrenderer = this.getArmForSide(handside);
			float f = this.swingProgress;
			this.bipedBody.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Abdomen.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Neck.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Neck2.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.bipedHead.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Nose.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Snout.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.TeethL.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.TeethU.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Mouth.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.SideburnL.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.SideburnR.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.REar.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.LEar.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;

			if (handside == HandSide.LEFT) {
				this.bipedBody.rotateAngleY *= -1.0F;
				this.Abdomen.rotateAngleY *= -1.0F;
				this.Neck.rotateAngleY *= -1.0F;
				this.Neck2.rotateAngleY *= -1.0F;
				this.bipedHead.rotateAngleY *= -1.0F;
				this.Nose.rotateAngleY *= -1.0F;
				this.Snout.rotateAngleY *= -1.0F;
				this.TeethL.rotateAngleY *= -1.0F;
				this.TeethU.rotateAngleY *= -1.0F;
				this.Mouth.rotateAngleY *= -1.0F;
				this.SideburnL.rotateAngleY *= -1.0F;
				this.SideburnR.rotateAngleY *= -1.0F;
				this.REar.rotateAngleY *= -1.0F;
				this.LEar.rotateAngleY *= -1.0F;
			}


			
			
			/*this.lArmA.setRotationPoint(5F, -4F, 0F);
			setRotation(lArmA, 0.6320364F, 0F, 0F);
			
			
			this.rArmA.setRotationPoint(-5.0F, -4.0F, 0.0F);
			setRotation(rArmA, 0.6320364F, 0F, 0F);	*/		
			
			/*this.rArmA.rotationPointZ = MathHelper.sin(this.bipedBody.rotateAngleY) * 5.0F;
			this.rArmA.rotationPointX = -MathHelper.cos(this.bipedBody.rotateAngleY) * 5.0F;
			this.lArmA.rotationPointZ = -MathHelper.sin(this.bipedBody.rotateAngleY) * 5.0F;
			this.lArmA.rotationPointX = MathHelper.cos(this.bipedBody.rotateAngleY) * 5.0F;
			
			this.rArmA.rotateAngleY += this.bipedBody.rotateAngleY;
			this.lArmA.rotateAngleY += this.bipedBody.rotateAngleY;
			this.lArmA.rotateAngleX += this.bipedBody.rotateAngleY;*/
			
			this.bipedRightArm.rotationPointZ = MathHelper.sin(this.bipedBody.rotateAngleY) * 5.0F;
			this.bipedRightArm.rotationPointX = -MathHelper.cos(this.bipedBody.rotateAngleY) * 5.0F;
			this.bipedLeftArm.rotationPointZ = -MathHelper.sin(this.bipedBody.rotateAngleY) * 5.0F;
			this.bipedLeftArm.rotationPointX = MathHelper.cos(this.bipedBody.rotateAngleY) * 5.0F;

			this.bipedRightArm.rotateAngleY += this.bipedBody.rotateAngleY;
			this.bipedLeftArm.rotateAngleY += this.bipedBody.rotateAngleY;
			this.bipedLeftArm.rotateAngleX += this.bipedBody.rotateAngleY;
			
			
			
			DevilRpg.LOGGER.info("----1ero");
			DevilRpg.LOGGER.info("----2do");
			
			//Animate item hand
			f = 1.0F - this.swingProgress;
			f = f * f;
			f = f * f;
			f = 1.0F - f;
			float f1 = MathHelper.sin(f * (float) Math.PI);
			float f2 = MathHelper.sin(this.swingProgress * (float) Math.PI) * -(this.bipedHead.rotateAngleX - 0.7F) * 0.75F;
			modelrenderer.rotateAngleX = (float) ((double) modelrenderer.rotateAngleX - ((double) f1 * 1.2D + (double) f2));
			modelrenderer.rotateAngleY += this.bipedBody.rotateAngleY * 2.0F;
			modelrenderer.rotateAngleZ += MathHelper.sin(this.swingProgress * (float) Math.PI) * -0.4F;

		}
	}
	
	
	protected void func_230486_a_Copy(T entityin, float ageInTicks) {
		if (!(this.swingProgress <= 0.0F)) {
			HandSide handside = this.getMainHand(entityin);
			ModelRenderer modelrenderer = this.getArmForSide(handside);
			float f = this.swingProgress;
			this.bipedBody.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Abdomen.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Neck.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Neck2.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.bipedHead.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Nose.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Snout.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.TeethL.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.TeethU.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.SideburnL.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.SideburnR.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.REar.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.LEar.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;

			if (handside == HandSide.LEFT) {
				this.bipedBody.rotateAngleY *= -1.0F;
				this.Abdomen.rotateAngleY *= -1.0F;
				this.Neck.rotateAngleY *= -1.0F;
				this.Neck2.rotateAngleY *= -1.0F;
				this.bipedHead.rotateAngleY *= -1.0F;
				this.Nose.rotateAngleY *= -1.0F;
				this.Snout.rotateAngleY *= -1.0F;
				this.TeethL.rotateAngleY *= -1.0F;
				this.TeethU.rotateAngleY *= -1.0F;
				this.SideburnL.rotateAngleY *= -1.0F;
				this.SideburnR.rotateAngleY *= -1.0F;
				this.REar.rotateAngleY *= -1.0F;
				this.LEar.rotateAngleY *= -1.0F;
			}

			this.bipedRightArm.rotationPointZ = MathHelper.sin(this.bipedBody.rotateAngleY) * 5.0F;
			this.bipedRightArm.rotationPointX = -MathHelper.cos(this.bipedBody.rotateAngleY) * 5.0F;
			this.bipedLeftArm.rotationPointZ = -MathHelper.sin(this.bipedBody.rotateAngleY) * 5.0F;
			this.bipedLeftArm.rotationPointX = MathHelper.cos(this.bipedBody.rotateAngleY) * 5.0F;

			this.bipedRightArm.rotateAngleY += this.bipedBody.rotateAngleY;
			this.bipedLeftArm.rotateAngleY += this.bipedBody.rotateAngleY;
			this.bipedLeftArm.rotateAngleX += this.bipedBody.rotateAngleY;

			f = 1.0F - this.swingProgress;
			f = f * f;
			f = f * f;
			f = 1.0F - f;
			float f1 = MathHelper.sin(f * (float) Math.PI);
			float f2 = MathHelper.sin(this.swingProgress * (float) Math.PI) * -(this.bipedHead.rotateAngleX - 0.7F)
					* 0.75F;
			modelrenderer.rotateAngleX = (float) ((double) modelrenderer.rotateAngleX
					- ((double) f1 * 1.2D + (double) f2));
			modelrenderer.rotateAngleY += this.bipedBody.rotateAngleY * 2.0F;
			modelrenderer.rotateAngleZ += MathHelper.sin(this.swingProgress * (float) Math.PI) * -0.4F;

		}
	}

	public void setVisible(boolean visible) {
		super.setVisible(visible);

		bipedHead.showModel = visible;
		Nose.showModel = visible;
		Snout.showModel = visible;
		TeethU.showModel = visible;
		TeethL.showModel = visible;
		Mouth.showModel = visible;
		LEar.showModel = visible;
		REar.showModel = visible;
		Neck.showModel = visible;
		Neck2.showModel = visible;
		SideburnL.showModel = visible;
		SideburnR.showModel = visible;
		bipedBody.showModel = visible;
		Abdomen.showModel = visible;
		TailA.showModel = visible;
		TailC.showModel = visible;
		TailB.showModel = visible;
		TailD.showModel = visible;
		RLegA.showModel = visible;
		RFoot.showModel = visible;
		RLegB.showModel = visible;
		RLegC.showModel = visible;
		LLegB.showModel = visible;
		LFoot.showModel = visible;
		LLegC.showModel = visible;
		LLegA.showModel = visible;
		rArmB.showModel = visible;
		rArmC.showModel = visible;
		lArmB.showModel = visible;
		rArmD.showModel = visible;
		rArmA.showModel = visible;
		lArmA.showModel = visible;
		lArmC.showModel = visible;
		lArmD.showModel = visible;
		RFinger1.showModel = visible;
		RFinger2.showModel = visible;
		RFinger3.showModel = visible;
		RFinger4.showModel = visible;
		RFinger5.showModel = visible;
		LFinger1.showModel = visible;
		LFinger2.showModel = visible;
		LFinger3.showModel = visible;
		LFinger4.showModel = visible;
		LFinger5.showModel = visible;
	}

	public ModelRenderer getRandomModelRenderer(Random randomIn) {
		return this.modelRenderers.get(randomIn.nextInt(this.modelRenderers.size()));
	}

	public void accept(ModelRenderer p_accept_1_) {
		if (this.modelRenderers == null) {
			this.modelRenderers = Lists.newArrayList();
		}

		this.modelRenderers.add(p_accept_1_);
	}

}
