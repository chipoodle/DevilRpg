package com.chipoodle.devilrpg.client.render.entity.model;

import java.util.List;
import java.util.Random;

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
	// public ModelRenderer head;
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
	// public ModelRenderer body;
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

	// public ModelRenderer rightArm;
	// public ModelRenderer rightArm;
	public ModelRenderer rArmB;
	public ModelRenderer rArmC;
	public ModelRenderer rArmD;
	// public ModelRenderer leftArm;
	// public ModelRenderer rightArm;
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

	public WerewolfTransformedModel(float modelSize, boolean smallArmsIn) {
		super(modelSize);

		texWidth = 64;
		texHeight = 128;

		head = new ModelRenderer(this, 0, 0);
		head.addBox(-4F, -3F, -6F, 8, 8, 6);
		head.setPos(0F, -8F, -6F);
		head.setTexSize(64, 128);

		Nose = new ModelRenderer(this, 44, 33);
		Nose.addBox(-1.5F, -1.7F, -12.3F, 3, 2, 7);
		Nose.setPos(0F, -8F, -6F);
		setRotation(Nose, 0.2792527F, 0F, 0F);

		Snout = new ModelRenderer(this, 0, 25);
		Snout.addBox(-2F, 2F, -12F, 4, 2, 6);
		Snout.setPos(0F, -8F, -6F);

		TeethU = new ModelRenderer(this, 46, 18);
		TeethU.addBox(-2F, 4.01F, -12F, 4, 2, 5);
		TeethU.setPos(0F, -8F, -6F);

		TeethL = new ModelRenderer(this, 20, 109);
		TeethL.addBox(-1.5F, -12.5F, 2.01F, 3, 5, 2);
		TeethL.setPos(0F, -8F, -6F);
		setRotation(TeethL, 2.530727F, 0F, 0F);

		Mouth = new ModelRenderer(this, 42, 69);
		Mouth.addBox(-1.5F, -12.5F, 0F, 3, 9, 2);
		Mouth.setPos(0F, -8F, -6F);
		setRotation(Mouth, 2.530727F, 0F, 0F);

		LEar = new ModelRenderer(this, 13, 14);
		LEar.addBox(0.5F, -7.5F, -1F, 3, 5, 1);
		LEar.setPos(0F, -8F, -6F);
		setRotation(LEar, 0F, 0F, 0.1745329F);

		REar = new ModelRenderer(this, 22, 0);
		REar.addBox(-3.5F, -7.5F, -1F, 3, 5, 1);
		REar.setPos(0F, -8F, -6F);
		setRotation(REar, 0F, 0F, -0.1745329F);

		Neck = new ModelRenderer(this, 28, 0);
		Neck.addBox(-3.5F, -3F, -7F, 7, 8, 7);
		Neck.setPos(0F, -5F, -2F);
		setRotation(Neck, -0.6025001F, 0F, 0F);

		Neck2 = new ModelRenderer(this, 0, 14);
		Neck2.addBox(-1.5F, -2F, -5F, 3, 4, 7);
		Neck2.setPos(0F, -1F, -6F);
		setRotation(Neck2, -0.4537856F, 0F, 0F);

		SideburnL = new ModelRenderer(this, 28, 33);
		SideburnL.addBox(3F, 0F, -2F, 2, 6, 6);
		SideburnL.setPos(0F, -8F, -6F);
		setRotation(SideburnL, -0.2094395F, 0.418879F, -0.0872665F);

		SideburnR = new ModelRenderer(this, 28, 45);
		SideburnR.addBox(-5F, 0F, -2F, 2, 6, 6);
		SideburnR.setPos(0F, -8F, -6F);
		setRotation(SideburnR, -0.2094395F, -0.418879F, 0.0872665F);

		body = new ModelRenderer(this, 20, 15);
		body.addBox(-4F, 0F, -7F, 8, 8, 10);
		body.setPos(0F, -6F, -2.5F);
		setRotation(body, 0.641331F, 0F, 0F);

		Abdomen = new ModelRenderer(this, 0, 40);
		Abdomen.addBox(-3F, -8F, -8F, 6, 14, 8);
		Abdomen.setPos(0F, 4.5F, 5F);
		setRotation(Abdomen, 0.2695449F, 0F, 0F);

		TailA = new ModelRenderer(this, 52, 42);
		TailA.addBox(-1.5F, -1F, -2F, 3, 4, 3);
		TailA.setPos(0F, 9.5F, 6F);
		setRotation(TailA, 1.064651F, 0F, 0F);

		TailC = new ModelRenderer(this, 48, 59);
		TailC.addBox(-2F, 6.8F, -4.6F, 4, 6, 4);
		TailC.setPos(0F, 9.5F, 6F);
		setRotation(TailC, 1.099557F, 0F, 0F);

		TailB = new ModelRenderer(this, 48, 49);
		TailB.addBox(-2F, 2F, -2F, 4, 6, 4);
		TailB.setPos(0F, 9.5F, 6F);
		setRotation(TailB, 0.7504916F, 0F, 0F);

		TailD = new ModelRenderer(this, 52, 69);
		TailD.addBox(-1.5F, 9.8F, -4.1F, 3, 5, 3);
		TailD.setPos(0F, 9.5F, 6F);
		setRotation(TailD, 1.099557F, 0F, 0F);

		RLegA = new ModelRenderer(this, 12, 64);
		RLegA.addBox(-2.5F, -1.5F, -3.5F, 3, 8, 5);
		RLegA.setPos(-3F, 9.5F, 3F);
		setRotation(RLegA, -0.8126625F, 0F, 0F);

		RFoot = new ModelRenderer(this, 14, 93);
		RFoot.addBox(-2.506667F, 12.5F, -5F, 3, 2, 3);
		RFoot.setPos(-3F, 9.5F, 3F);

		RLegB = new ModelRenderer(this, 14, 76);
		RLegB.addBox(-1.9F, 4.2F, 0.5F, 2, 2, 5);
		RLegB.setPos(-3F, 9.5F, 3F);
		setRotation(RLegB, -0.8445741F, 0F, 0F);

		RLegC = new ModelRenderer(this, 14, 83);
		RLegC.addBox(-2F, 6.2F, 0.5F, 2, 8, 2);
		RLegC.setPos(-3F, 9.5F, 3F);
		setRotation(RLegC, -0.2860688F, 0F, 0F);

		LLegB = new ModelRenderer(this, 0, 76);
		LLegB.addBox(-0.1F, 4.2F, 0.5F, 2, 2, 5);
		LLegB.setPos(3F, 9.5F, 3F);
		setRotation(LLegB, -0.8445741F, 0F, 0F);

		LFoot = new ModelRenderer(this, 0, 93);
		LFoot.addBox(-0.5066667F, 12.5F, -5F, 3, 2, 3);
		LFoot.setPos(3F, 9.5F, 3F);

		LLegC = new ModelRenderer(this, 0, 83);
		LLegC.addBox(0F, 6.2F, 0.5F, 2, 8, 2);
		LLegC.setPos(3F, 9.5F, 3F);
		setRotation(LLegC, -0.2860688F, 0F, 0F);

		LLegA = new ModelRenderer(this, 0, 64);
		LLegA.addBox(-0.5F, -1.5F, -3.5F, 3, 8, 5);
		LLegA.setPos(3F, 9.5F, 3F);
		setRotation(LLegA, -0.8126625F, 0F, 0F);

		/*
		 * rArmA = new ModelRenderer(this, 0, 108); rArmA.addBox(-5F, -3F, -2F, 5, 5,
		 * 5); rArmA.setPos(-4F, -4F, -2F); setRotation(rArmA, 0.6320364F, 0F, 0F);
		 */

		rArmB = new ModelRenderer(this, 48, 77);
		rArmB.addBox(-3.5F, 1F, -1.5F, 4, 8, 4);
		rArmB.setPos(-4F, -4F, -2F);
		setRotation(rArmB, 0.2617994F, 0F, 0.3490659F);

		rArmC = new ModelRenderer(this, 48, 112);
		rArmC.addBox(-6F, 5F, 3F, 4, 7, 4);
		rArmC.setPos(-4F, -4F, -2F);
		setRotation(rArmC, -0.3490659F, 0F, 0F);

		rArmD = new ModelRenderer(this, 32, 118);
		rArmD.addBox(-6F, 12.5F, -1.5F, 4, 3, 4);
		rArmD.setPos(-4F, -4F, -2F);

		/*
		 * lArmA = new ModelRenderer(this, 0, 98); lArmA.addBox(0F, -3F, -2F, 5, 5, 5);
		 * lArmA.setPos(4F, -4F, -2F); setRotation(lArmA, 0.6320364F, 0F, 0F);
		 */

		lArmB = new ModelRenderer(this, 48, 89);
		lArmB.addBox(-0.5F, 1F, -1.5F, 4, 8, 4);
		lArmB.setPos(4F, -4F, -2F);
		setRotation(lArmB, 0.2617994F, 0F, -0.3490659F);

		lArmC = new ModelRenderer(this, 48, 101);
		lArmC.addBox(2F, 5F, 3F, 4, 7, 4);
		lArmC.setPos(4F, -4F, -2F);
		setRotation(lArmC, -0.3490659F, 0F, 0F);

		lArmD = new ModelRenderer(this, 32, 111);
		lArmD.addBox(2F, 12.5F, -1.5F, 4, 3, 4);
		lArmD.setPos(4F, -4F, -2F);

		RFinger1 = new ModelRenderer(this, 8, 120);
		RFinger1.addBox(-0.5F, 0F, -0.5F, 1, 3, 1);
		RFinger1.setPos(-6.5F, 11.5F, -0.5F);

		RFinger1 = new ModelRenderer(this, 8, 120);
		RFinger1.addBox(-3F, 15.5F, 1F, 1, 3, 1);
		RFinger1.setPos(-4F, -4F, -2F);

		RFinger2 = new ModelRenderer(this, 12, 124);
		RFinger2.addBox(-3.5F, 15.5F, -1.5F, 1, 3, 1);
		RFinger2.setPos(-4F, -4F, -2F);

		RFinger3 = new ModelRenderer(this, 12, 119);
		RFinger3.addBox(-4.8F, 15.5F, -1.5F, 1, 4, 1);
		RFinger3.setPos(-4F, -4F, -2F);

		RFinger4 = new ModelRenderer(this, 16, 119);
		RFinger4.addBox(-6F, 15.5F, -0.5F, 1, 4, 1);
		RFinger4.setPos(-4F, -4F, -2F);

		RFinger5 = new ModelRenderer(this, 16, 124);
		RFinger5.addBox(-6F, 15.5F, 1F, 1, 3, 1);
		RFinger5.setPos(-4F, -4F, -2F);

		LFinger1 = new ModelRenderer(this, 8, 124);
		LFinger1.addBox(2F, 15.5F, 1F, 1, 3, 1);
		LFinger1.setPos(4F, -4F, -2F);

		LFinger2 = new ModelRenderer(this, 0, 124);
		LFinger2.addBox(2.5F, 15.5F, -1.5F, 1, 3, 1);
		LFinger2.setPos(4F, -4F, -2F);

		LFinger3 = new ModelRenderer(this, 0, 119);
		LFinger3.addBox(3.8F, 15.5F, -1.5F, 1, 4, 1);
		LFinger3.setPos(4F, -4F, -2F);

		LFinger4 = new ModelRenderer(this, 4, 119);
		LFinger4.addBox(5F, 15.5F, -0.5F, 1, 4, 1);
		LFinger4.setPos(4F, -4F, -2F);

		LFinger5 = new ModelRenderer(this, 4, 124);
		LFinger5.addBox(5F, 15.5F, 1F, 1, 3, 1);
		LFinger5.setPos(4F, -4F, -2F);

		/*-------------------------------------*/
		leftArm = new ModelRenderer(this, 0, 98);
		leftArm.addBox(0F, -3F, -2F, 5, 5, 5);
		leftArm.setPos(4F, -4F, -2F);
		setRotation(leftArm, 0.6320364F, 0F, 0F);

		rightArm = new ModelRenderer(this, 0, 108);
		rightArm.addBox(-5F, -3F, -2F, 5, 5, 5);
		rightArm.setPos(-4F, -4F, -2F);
		setRotation(rightArm, 0.6320364F, 0F, 0F);

	}

	protected Iterable<ModelRenderer> bodyParts() {
		return // Iterables.concat(

		// super.getBodyParts(),

		ImmutableList.of(this.leftArm, this.rightArm, this.body, this.Abdomen, this.TailA, this.TailB, this.TailC,
				this.TailD, this.RLegA, this.RFoot, this.RLegB, this.RLegC, this.LLegA, this.LFoot, this.LLegB,
				this.LLegC,
				// this.rArmA,
				this.rArmB, this.rArmC, this.lArmD,
				// this.lArmA,
				this.lArmB, this.lArmC, this.rArmD, this.RFinger1, this.RFinger2, this.RFinger2, this.RFinger3,
				this.RFinger4, this.RFinger5, this.LFinger1, this.LFinger2, this.LFinger2, this.LFinger3, this.LFinger4,
				this.LFinger5);
		// );
	}

	@Override
	protected Iterable<ModelRenderer> headParts() {
		return ImmutableList.of(this.head, /* this.hat, */ this.Nose, this.Snout, this.TeethU, this.TeethL, this.Mouth,
				this.LEar, this.REar, this.Neck, this.Neck2, this.SideburnL, this.SideburnR);
	}

	private void setRotation(ModelRenderer model, float x, float y, float z) {
		model.xRot = x;
		model.yRot = y;
		model.zRot = z;
	}

	@Override
	public void setupAnim(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {

		float radianF = 57.29578F;
		float RLegXRot = MathHelper.cos((limbSwing * 0.6662F) + 3.141593F) * 0.8F * limbSwingAmount;
		float LLegXRot = MathHelper.cos(limbSwing * 0.6662F) * 0.8F * limbSwingAmount;

		head.yRot = netHeadYaw / radianF; // this moves head to left and right
		head.xRot = headPitch / radianF; // this moves head up and down

		if (!crouching) {
			head.y = -8F;
			head.z = -6F;
			head.xRot = headPitch / radianF;
			Neck.xRot = -34 / radianF;
			Neck.y = -5F;
			Neck.z = -2F;
			Neck2.y = -1F;
			Neck2.z = -6F;
			body.y = -6F;
			body.z = -2.5F;
			body.xRot = 36 / radianF;
			Abdomen.xRot = 15 / radianF;
			LLegA.z = 3F;

			leftArm.y = -4F;
			leftArm.z = -2F;

			TailA.y = 9.5F;
			TailA.z = 6F;

			/*
			 * /////// leftArm.y = -4F; leftArm.z = -2F;
			 */
		} else {
			head.y = 0F;
			head.z = -11F;
			head.xRot = (15F + headPitch) / radianF;

			Neck.xRot = -10 / radianF;
			Neck.y = 2F;
			Neck.z = -6F;
			Neck2.y = 9F;
			Neck2.z = -9F;
			body.y = 1F;
			body.z = -7.5F;
			body.xRot = 60 / radianF;
			Abdomen.xRot = 75 / radianF;
			LLegA.z = 7F;
			leftArm.y = 4.5F;
			leftArm.z = -6F;
			TailA.y = 7.5F;
			TailA.z = 10F;

			/*
			 * ///// leftArm.y = 4.5F; leftArm.z = -6F;
			 */
		}

		Nose.y = head.y;
		Snout.y = head.y;
		TeethU.y = head.y;
		LEar.y = head.y;
		REar.y = head.y;
		TeethL.y = head.y;
		Mouth.y = head.y;
		SideburnL.y = head.y;
		SideburnR.y = head.y;

		Nose.z = head.z;
		Snout.z = head.z;
		TeethU.z = head.z;
		LEar.z = head.z;
		REar.z = head.z;
		TeethL.z = head.z;
		Mouth.z = head.z;
		SideburnL.z = head.z;
		SideburnR.z = head.z;

		setArmsRotationYZ();

		RLegA.z = LLegA.z;
		RLegB.z = LLegA.z;
		RLegC.z = LLegA.z;
		RFoot.z = LLegA.z;
		LLegB.z = LLegA.z;
		LLegC.z = LLegA.z;
		LFoot.z = LLegA.z;

		TailB.y = TailA.y;
		TailB.z = TailA.z;
		TailC.y = TailA.y;
		TailC.z = TailA.z;
		TailD.y = TailA.y;
		TailD.z = TailA.z;

		Nose.yRot = head.yRot;
		Snout.yRot = head.yRot;
		TeethU.yRot = head.yRot;
		LEar.yRot = head.yRot;
		REar.yRot = head.yRot;
		TeethL.yRot = head.yRot;
		Mouth.yRot = head.yRot;

		TeethL.xRot = head.xRot + 2.530727F;
		Mouth.xRot = head.xRot + 2.530727F;

		SideburnL.xRot = -0.2094395F + head.xRot;
		SideburnL.yRot = 0.418879F + head.yRot;
		SideburnR.xRot = -0.2094395F + head.xRot;
		SideburnR.yRot = -0.418879F + head.yRot;

		Nose.xRot = 0.2792527F + head.xRot;
		Snout.xRot = head.xRot;
		TeethU.xRot = head.xRot;

		LEar.xRot = head.xRot;
		REar.xRot = head.xRot;

		RLegA.xRot = -0.8126625F + RLegXRot;
		RLegB.xRot = -0.8445741F + RLegXRot;
		RLegC.xRot = -0.2860688F + RLegXRot;
		RFoot.xRot = RLegXRot;

		LLegA.xRot = -0.8126625F + LLegXRot;
		LLegB.xRot = -0.8445741F + LLegXRot;
		LLegC.xRot = -0.2860688F + LLegXRot;
		LFoot.xRot = LLegXRot;

		/* animation while moving */
		rightArm.zRot = -(MathHelper.cos(ageInTicks * 0.09F) * 0.05F) + 0.05F;
		leftArm.zRot = (MathHelper.cos(ageInTicks * 0.09F) * 0.05F) - 0.05F;
		rightArm.xRot = LLegXRot;// MathHelper.sin(f2 * 0.067F) * 0.05F;
		leftArm.xRot = RLegXRot;// MathHelper.sin(f2 * 0.067F) * 0.05F;

		
		////////// borrar si es que no sirve
		rightArm.y = leftArm.y;
		rightArm.z = leftArm.z;
		rightArm.zRot = -(MathHelper.cos(ageInTicks * 0.09F) * 0.05F) + 0.05F;
		leftArm.zRot = (MathHelper.cos(ageInTicks * 0.09F) * 0.05F) - 0.05F;
		rightArm.xRot = LLegXRot;
		// MathHelper.sin(f2 * 0.067F) * 0.05F; leftArm.xRot = RLegXRot;
		// MathHelper.sin(f2 * 0.067F) * 0.05F;
		//////////
		
		
		rArmB.zRot = 0.3490659F + rightArm.zRot;
		lArmB.zRot = -0.3490659F + leftArm.zRot;
		rArmB.xRot = 0.2617994F + rightArm.xRot;
		lArmB.xRot = 0.2617994F + leftArm.xRot;

		rArmC.zRot = rightArm.zRot;
		lArmC.zRot = leftArm.zRot;
		rArmC.xRot = -0.3490659F + rightArm.xRot;
		lArmC.xRot = -0.3490659F + leftArm.xRot;

		rArmD.zRot = rightArm.zRot;
		lArmD.zRot = leftArm.zRot;
		rArmD.xRot = rightArm.xRot;
		lArmD.xRot = leftArm.xRot;

		RFinger1.xRot = rightArm.xRot;
		RFinger2.xRot = rightArm.xRot;
		RFinger3.xRot = rightArm.xRot;
		RFinger4.xRot = rightArm.xRot;
		RFinger5.xRot = rightArm.xRot;

		LFinger1.xRot = leftArm.xRot;
		LFinger2.xRot = leftArm.xRot;
		LFinger3.xRot = leftArm.xRot;
		LFinger4.xRot = leftArm.xRot;
		LFinger5.xRot = leftArm.xRot;

		RFinger1.zRot = rightArm.zRot;
		RFinger2.zRot = rightArm.zRot;
		RFinger3.zRot = rightArm.zRot;
		RFinger4.zRot = rightArm.zRot;
		RFinger5.zRot = rightArm.zRot;

		LFinger1.zRot = leftArm.zRot;
		LFinger2.zRot = leftArm.zRot;
		LFinger3.zRot = leftArm.zRot;
		LFinger4.zRot = leftArm.zRot;
		LFinger5.zRot = leftArm.zRot;

		setRotationAnglesHelper(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, RLegXRot,
				LLegXRot);

	}

	/**
	 * 
	 */
	private void setArmsRotationYZ() {
		lArmB.y = leftArm.y;
		lArmC.y = leftArm.y;
		lArmD.y = leftArm.y;
		LFinger1.y = leftArm.y;
		LFinger2.y = leftArm.y;
		LFinger3.y = leftArm.y;
		LFinger4.y = leftArm.y;
		LFinger5.y = leftArm.y;
		rightArm.y = leftArm.y;
		rArmB.y = leftArm.y;
		rArmC.y = leftArm.y;
		rArmD.y = leftArm.y;
		RFinger1.y = leftArm.y;
		RFinger2.y = leftArm.y;
		RFinger3.y = leftArm.y;
		RFinger4.y = leftArm.y;
		RFinger5.y = leftArm.y;

		lArmB.z = leftArm.z;
		lArmC.z = leftArm.z;
		lArmD.z = leftArm.z;
		LFinger1.z = leftArm.z;
		LFinger2.z = leftArm.z;
		LFinger3.z = leftArm.z;
		LFinger4.z = leftArm.z;
		LFinger5.z = leftArm.z;
		rightArm.z = leftArm.z;
		rArmB.z = leftArm.z;
		rArmC.z = leftArm.z;
		rArmD.z = leftArm.z;
		RFinger1.z = leftArm.z;
		RFinger2.z = leftArm.z;
		RFinger3.z = leftArm.z;
		RFinger4.z = leftArm.z;
		RFinger5.z = leftArm.z;
	}

	protected void setRotationAnglesHelper(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch, float RLegXRot, float LLegXRot) {

		boolean flagelytra = entityIn.getFallFlyingTicks() > 4;
		boolean flagSwimming = entityIn.isVisuallySwimming();
		this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
		if (flagelytra) {
			this.head.xRot = (-(float) Math.PI / 4F);
		} else if (this.swimAmount > 0.0F) {
			if (flagSwimming) {
				this.head.xRot = this.rotlerpRad(this.swimAmount, this.head.xRot, (-(float) Math.PI / 4F));
			} else {
				this.head.xRot = this.rotlerpRad(this.swimAmount, this.head.xRot, headPitch * ((float) Math.PI / 180F));
			}
		} else {
			this.head.xRot = headPitch * ((float) Math.PI / 180F);
		}
		this.body.yRot = 0.0F;

		this.leftArm.x = 4.0F;
		this.rightArm.z = leftArm.z;
		this.rightArm.x = -4.0F;

		/*
		 * float f = 1.0F; if (flagelytra) { f = (float)
		 * entityIn.getMotion().lengthSquared(); f = f / 0.2F; f = f * f * f; }
		 * 
		 * if (f < 1.0F) { f = 1.0F; }
		 */

		rightArm.zRot = -(MathHelper.cos(ageInTicks * 0.09F) * 0.05F) + 0.05F;
		leftArm.zRot = (MathHelper.cos(ageInTicks * 0.09F) * 0.05F) - 0.05F;
		rightArm.xRot = LLegXRot;// MathHelper.sin(f2 * 0.067F) * 0.05F;
		leftArm.xRot = RLegXRot;// MathHelper.sin(f2 * 0.067F) * 0.05F;

		this.rightArm.zRot = 0.0F;
		this.leftArm.zRot = 0.0F;
		this.rightArm.yRot = 0.0F;
		this.leftArm.yRot = 0.0F;

		// OnSwingAttack
		this.setupAttackAnimation(entityIn, ageInTicks);

		animateSwimming(entityIn, limbSwing);
		this.hat.copyFrom(this.head);

		/*
		 * boolean flag2 = entityIn.getMainArm() == HandSide.RIGHT; boolean flag3 =
		 * flag2 ? this.leftArmPose.isTwoHanded() : this.rightArmPose.isTwoHanded(); if
		 * (flag2 != flag3) { this.poseLeftArm(entityIn); this.poseRightArm(entityIn); }
		 * else { this.poseRightArm(entityIn); this.poseLeftArm(entityIn); }
		 */

	}

	/**
	 * @param entityIn
	 * @param limbSwing
	 */
	private void animateSwimming(T entityIn, float limbSwing) {
		if (this.swimAmount > 0.0F) {
			float f1 = limbSwing % 26.0F;
			HandSide handside = this.getAttackArm(entityIn);
			float f2 = handside == HandSide.RIGHT && this.attackTime > 0.0F ? 0.0F : this.swimAmount;
			float f3 = handside == HandSide.LEFT && this.attackTime > 0.0F ? 0.0F : this.swimAmount;
			if (f1 < 14.0F) {
				this.leftArm.xRot = this.rotlerpRad(f3, this.leftArm.xRot, 0.0F);
				this.rightArm.xRot = MathHelper.lerp(f2, this.rightArm.xRot, 0.0F);
				this.leftArm.yRot = this.rotlerpRad(f3, this.leftArm.yRot, (float) Math.PI);
				this.rightArm.yRot = MathHelper.lerp(f2, this.rightArm.yRot, (float) Math.PI);
				this.leftArm.zRot = this.rotlerpRad(f3, this.leftArm.zRot,
						(float) Math.PI + 1.8707964F * this.getArmAngleSq(f1) / this.getArmAngleSq(14.0F));
				this.rightArm.zRot = MathHelper.lerp(f2, this.rightArm.zRot,
						(float) Math.PI - 1.8707964F * this.getArmAngleSq(f1) / this.getArmAngleSq(14.0F));
			} else if (f1 >= 14.0F && f1 < 22.0F) {
				float f6 = (f1 - 14.0F) / 8.0F;
				this.leftArm.xRot = this.rotlerpRad(f3, this.leftArm.xRot, ((float) Math.PI / 2F) * f6);
				this.rightArm.xRot = MathHelper.lerp(f2, this.rightArm.xRot, ((float) Math.PI / 2F) * f6);
				this.leftArm.yRot = this.rotlerpRad(f3, this.leftArm.yRot, (float) Math.PI);
				this.rightArm.yRot = MathHelper.lerp(f2, this.rightArm.yRot, (float) Math.PI);
				this.leftArm.zRot = this.rotlerpRad(f3, this.leftArm.zRot, 5.012389F - 1.8707964F * f6);
				this.rightArm.zRot = MathHelper.lerp(f2, this.rightArm.zRot, 1.2707963F + 1.8707964F * f6);
			} else if (f1 >= 22.0F && f1 < 26.0F) {
				float f4 = (f1 - 22.0F) / 4.0F;
				this.leftArm.xRot = this.rotlerpRad(f3, this.leftArm.xRot,
						((float) Math.PI / 2F) - ((float) Math.PI / 2F) * f4);
				this.rightArm.xRot = MathHelper.lerp(f2, this.rightArm.xRot,
						((float) Math.PI / 2F) - ((float) Math.PI / 2F) * f4);
				this.leftArm.yRot = this.rotlerpRad(f3, this.leftArm.yRot, (float) Math.PI);
				this.rightArm.yRot = MathHelper.lerp(f2, this.rightArm.yRot, (float) Math.PI);
				this.leftArm.zRot = this.rotlerpRad(f3, this.leftArm.zRot, (float) Math.PI);
				this.rightArm.zRot = MathHelper.lerp(f2, this.rightArm.zRot, (float) Math.PI);
			}

			float f7 = 0.3F;
			float f5 = 0.33333334F;
			this.leftLeg.xRot = MathHelper.lerp(this.swimAmount, this.leftLeg.xRot,
					0.3F * MathHelper.cos(limbSwing * 0.33333334F + (float) Math.PI));
			this.rightLeg.xRot = MathHelper.lerp(this.swimAmount, this.rightLeg.xRot,
					0.3F * MathHelper.cos(limbSwing * 0.33333334F));
		}
	}

	private float getArmAngleSq(float limbSwing) {
		return -65.0F * limbSwing + limbSwing * limbSwing;
	}

	private void poseRightArm(T p_241654_1_) {
		switch (this.rightArmPose) {
		case EMPTY:
			this.rArmD.yRot = 0.0F;
			break;
		case BLOCK:
			this.rArmD.xRot = this.rArmD.xRot * 0.5F - 0.9424779F;
			this.rArmD.yRot = (-(float) Math.PI / 6F);
			break;
		case ITEM:
			this.rArmD.xRot = this.rArmD.xRot * 0.5F - ((float) Math.PI / 10F);
			this.rArmD.yRot = 0.0F;
			break;
		case THROW_SPEAR:
			this.rArmD.xRot = this.rArmD.xRot * 0.5F - (float) Math.PI;
			this.rArmD.yRot = 0.0F;
			break;
		case BOW_AND_ARROW:
			this.rArmD.yRot = -0.1F + this.head.yRot;
			this.lArmD.yRot = 0.1F + this.head.yRot + 0.4F;
			this.rArmD.xRot = (-(float) Math.PI / 2F) + this.head.xRot;
			this.lArmD.xRot = (-(float) Math.PI / 2F) + this.head.xRot;
			break;
		case CROSSBOW_CHARGE:
			ModelHelper.animateCrossbowCharge(this.rArmD, this.lArmD, p_241654_1_, true);
			break;
		case CROSSBOW_HOLD:
			ModelHelper.animateCrossbowHold(this.rArmD, this.lArmD, this.head, true);
		}

	}

	private void poseLeftArm(T p_241655_1_) {
		switch (this.leftArmPose) {
		case EMPTY:
			this.lArmD.yRot = 0.0F;
			break;
		case BLOCK:
			this.lArmD.xRot = this.lArmD.xRot * 0.5F - 0.9424779F;
			this.lArmD.yRot = ((float) Math.PI / 6F);
			break;
		case ITEM:
			this.lArmD.xRot = this.lArmD.xRot * 0.5F - ((float) Math.PI / 10F);
			this.lArmD.yRot = 0.0F;
			break;
		case THROW_SPEAR:
			this.lArmD.xRot = this.lArmD.xRot * 0.5F - (float) Math.PI;
			this.lArmD.yRot = 0.0F;
			break;
		case BOW_AND_ARROW:
			this.rArmD.yRot = -0.1F + this.head.yRot - 0.4F;
			this.lArmD.yRot = 0.1F + this.head.yRot;
			this.rArmD.xRot = (-(float) Math.PI / 2F) + this.head.xRot;
			this.lArmD.xRot = (-(float) Math.PI / 2F) + this.head.xRot;
			break;
		case CROSSBOW_CHARGE:
			ModelHelper.animateCrossbowCharge(this.rArmD, this.lArmD, p_241655_1_, false);
			break;
		case CROSSBOW_HOLD:
			ModelHelper.animateCrossbowHold(this.rArmD, this.lArmD, this.head, false);
		}
	}

	protected void setupAttackAnimation(T entityin, float ageInTicks) {
		if (!(this.attackTime <= 0.0F)) {
			HandSide handside = this.getAttackArm(entityin);
			ModelRenderer modelrenderer = this.getArm(handside);
			float f = this.attackTime;
			this.body.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Abdomen.yRot = -MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.1F;
			this.Neck.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Neck2.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.head.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Nose.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Snout.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.TeethL.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.TeethU.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.Mouth.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.SideburnL.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.SideburnR.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.REar.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;
			this.LEar.yRot = MathHelper.sin(MathHelper.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F;

			if (handside == HandSide.LEFT) {
				this.body.yRot *= -1.0F;
				this.Abdomen.yRot *= -1.0F;
				this.Neck.yRot *= -1.0F;
				this.Neck2.yRot *= -1.0F;
				this.head.yRot *= -1.0F;
				this.Nose.yRot *= -1.0F;
				this.Snout.yRot *= -1.0F;
				this.TeethL.yRot *= -1.0F;
				this.TeethU.yRot *= -1.0F;
				this.Mouth.yRot *= -1.0F;
				this.SideburnL.yRot *= -1.0F;
				this.SideburnR.yRot *= -1.0F;
				this.REar.yRot *= -1.0F;
				this.LEar.yRot *= -1.0F;
			}

			float radianF = 57.29578F;
			float RLegXRot = MathHelper.cos((ageInTicks * 0.6662F) + 3.141593F) * 0.8F;// * limbSwingAmount;
			float LLegXRot = MathHelper.cos(ageInTicks * 0.6662F) * 0.8F;// * limbSwingAmount;
			// rArmA.xRot = LLegXRot;// MathHelper.sin(f2 * 0.067F) * 0.05F;
			// lArmA.xRot = RLegXRot;// MathHelper.sin(f2 * 0.067F) * 0.05F;

			this.rightArm.x = -MathHelper.cos(this.body.yRot) * 3.5F;
			this.leftArm.x = MathHelper.cos(this.body.yRot) * 3.5F;

			if (!crouching) {
				leftArm.y = -4F;
				this.rightArm.z = MathHelper.sin(this.body.yRot) * 5.0F - 4;
				this.leftArm.z = -MathHelper.sin(this.body.yRot) * 5.0F - 4;
			} else {
				leftArm.y = 4.5F;
				this.rightArm.z = MathHelper.sin(this.body.yRot) * 5.0F - 6;
				this.leftArm.z = -MathHelper.sin(this.body.yRot) * 5.0F - 6;
			}

			if (handside.equals(HandSide.RIGHT)) {
				this.rightArm.xRot += this.body.yRot;
				this.rightArm.yRot += this.body.yRot;
				this.rArmB.xRot = this.rightArm.yRot * 2 - 0.8f;
				this.rArmB.yRot = this.rightArm.yRot;
				this.rArmC.xRot = this.rArmB.yRot * 2 - 1.3f;
				this.rArmC.yRot = this.rArmB.yRot;
				this.rArmD.xRot = this.rightArm.yRot * 2 - 1;
				this.rArmD.yRot = this.rightArm.yRot;

				this.RFinger1.xRot = this.rightArm.yRot * 2 - 1;
				this.RFinger1.yRot = this.rightArm.yRot;
				this.RFinger2.xRot = this.rightArm.yRot * 2 - 1;
				this.RFinger2.yRot = this.rightArm.yRot;
				this.RFinger3.xRot = this.rightArm.yRot * 2 - 1;
				this.RFinger3.yRot = this.rightArm.yRot;
				this.RFinger4.xRot = this.rightArm.yRot * 2 - 1;
				this.RFinger4.yRot = this.rightArm.yRot;
				this.RFinger5.xRot = this.rightArm.yRot * 2 - 1;
				this.RFinger5.yRot = this.rightArm.yRot;

			} else {
				this.leftArm.xRot += this.body.yRot;
				this.leftArm.yRot += this.body.yRot;
				this.lArmB.xRot = this.leftArm.yRot * 2 - 0.8f;
				this.lArmB.yRot = this.leftArm.yRot;
				this.lArmC.xRot = this.lArmB.yRot * 2 - 1.3f;
				this.lArmC.yRot = this.lArmB.yRot;
				this.lArmD.xRot = this.leftArm.yRot * 2 - 1;
				this.lArmD.yRot = this.leftArm.yRot;

				this.LFinger1.xRot = this.leftArm.yRot * 2 - 1;
				this.LFinger1.yRot = this.leftArm.yRot;
				this.LFinger2.xRot = this.leftArm.yRot * 2 - 1;
				this.LFinger2.yRot = this.leftArm.yRot;
				this.LFinger3.xRot = this.leftArm.yRot * 2 - 1;
				this.LFinger3.yRot = this.leftArm.yRot;
				this.LFinger4.xRot = this.leftArm.yRot * 2 - 1;
				this.LFinger4.yRot = this.leftArm.yRot;
				this.LFinger5.xRot = this.leftArm.yRot * 2 - 1;
				this.LFinger5.yRot = this.leftArm.yRot;
			}

			// Animate item hand
			f = 1.0F - this.attackTime;
			f = f * f;
			f = f * f;
			f = 1.0F - f;
			float f1 = MathHelper.sin(f * (float) Math.PI);
			float f2 = MathHelper.sin(this.attackTime * (float) Math.PI) * -(this.head.xRot - 0.7F) * 0.75F;
			modelrenderer.xRot = (float) ((double) modelrenderer.xRot - ((double) f1 * 1.2D + (double) f2));
			modelrenderer.yRot += this.body.yRot * 2.0F;
			modelrenderer.zRot += MathHelper.sin(this.attackTime * (float) Math.PI) * -0.4F;

		}
	}

	public void setAllVisible(boolean visible) {
		super.setAllVisible(visible);

		head.visible = visible;
		Nose.visible = visible;
		Snout.visible = visible;
		TeethU.visible = visible;
		TeethL.visible = visible;
		Mouth.visible = visible;
		LEar.visible = visible;
		REar.visible = visible;
		Neck.visible = visible;
		Neck2.visible = visible;
		SideburnL.visible = visible;
		SideburnR.visible = visible;
		body.visible = visible;
		Abdomen.visible = visible;
		TailA.visible = visible;
		TailC.visible = visible;
		TailB.visible = visible;
		TailD.visible = visible;
		RLegA.visible = visible;
		RFoot.visible = visible;
		RLegB.visible = visible;
		RLegC.visible = visible;
		LLegB.visible = visible;
		LFoot.visible = visible;
		LLegC.visible = visible;
		LLegA.visible = visible;
		rArmB.visible = visible;
		rArmC.visible = visible;
		lArmB.visible = visible;
		rArmD.visible = visible;
		rightArm.visible = visible;
		leftArm.visible = visible;
		lArmC.visible = visible;
		lArmD.visible = visible;
		RFinger1.visible = visible;
		RFinger2.visible = visible;
		RFinger3.visible = visible;
		RFinger4.visible = visible;
		RFinger5.visible = visible;
		LFinger1.visible = visible;
		LFinger2.visible = visible;
		LFinger3.visible = visible;
		LFinger4.visible = visible;
		LFinger5.visible = visible;

		leftArm.visible = visible;
		rightArm.visible = visible;
		leftLeg.visible = visible;
		rightLeg.visible = visible;

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
