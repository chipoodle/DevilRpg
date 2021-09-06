package com.chipoodle.devilrpg.client.render.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.ArrowWerewolfLayer;
import com.chipoodle.devilrpg.client.render.entity.model.WerewolfTransformedModel;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WerewolfRenderer extends LivingRenderer<PlayerEntity, WerewolfTransformedModel<PlayerEntity>> {

	private final ResourceLocation werewolfTexture = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/werewolf/wolftimber.png");

	public WerewolfRenderer(EntityRendererManager renderManager) {
		this(renderManager, false);
	}

	public WerewolfRenderer(EntityRendererManager renderManager, boolean useSmallArms) {
		super(renderManager, new WerewolfTransformedModel<PlayerEntity>(0.0F, useSmallArms), 0.5F);
		// super(renderManager);

		// this.addLayer(new BipedArmorLayer<>(this, new BipedModel(0.5F), new
		// BipedModel(1.0F)));
		// this.addLayer(new HeldItemLayer<>(this));
		this.addLayer(new ArrowWerewolfLayer<>(this));
		// this.addLayer(new Deadmau5HeadLayer(this));
		// this.addLayer(new CapeLayer(this));
		// this.addLayer(new HeadLayer<>(this));
		// this.addLayer(new ElytraLayer<>(this));
		// this.addLayer(new ParrotVariantLayer<>(this));
		// this.addLayer(new SpinAttackEffectLayer<>(this));
		// this.addLayer(new BeeStingerLayer<>(this));

		// this.addLayer(new WerewolfMoModel<PlayerEntity>(this));
	}

	public void render(PlayerEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
			IRenderTypeBuffer bufferIn, int packedLightIn) {
		super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);

		this.setModelProperties(entityIn);

		/*
		 * if (net.minecraftforge.common.MinecraftForge.EVENT_BUS .post(new
		 * net.minecraftforge.client.event.RenderPlayerEvent.Pre(entityIn, this,
		 * partialTicks, matrixStackIn, bufferIn, packedLightIn))) return;
		 * super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn,
		 * packedLightIn); net.minecraftforge.common.MinecraftForge.EVENT_BUS .post(new
		 * net.minecraftforge.client.event.RenderPlayerEvent.Post(entityIn, this,
		 * partialTicks, matrixStackIn, bufferIn, packedLightIn));
		 */

	}

	public Vector3d getRenderOffset(PlayerEntity entityIn, float partialTicks) {
		// return super.getRenderOffset(entityIn, partialTicks);
		return entityIn.isCrouching() ? new Vector3d(0.0D, -0.125D, 0.0D)
				: super.getRenderOffset(entityIn, partialTicks);

	}

	private void setModelProperties(PlayerEntity clientPlayer) {
		WerewolfTransformedModel<PlayerEntity> playermodel = this.getModel();
		//playermodel.bipedHead = playermodel.Head;
		if (clientPlayer.isSpectator()) {
			playermodel.setAllVisible(false);
			playermodel.head.visible = true;
			playermodel.hat.visible = true;
		} else {
			playermodel.setAllVisible(true);
			// playermodel.bipedHeadwear.showModel =
			// clientPlayer.isWearing(PlayerModelPart.HAT);
			/*
			 * playermodel.bipedBodyWear.showModel =
			 * clientPlayer.isWearing(PlayerModelPart.JACKET);
			 * playermodel.bipedLeftLegwear.showModel =
			 * clientPlayer.isWearing(PlayerModelPart.LEFT_PANTS_LEG);
			 * playermodel.bipedRightLegwear.showModel =
			 * clientPlayer.isWearing(PlayerModelPart.RIGHT_PANTS_LEG);
			 * playermodel.bipedLeftArmwear.showModel =
			 * clientPlayer.isWearing(PlayerModelPart.LEFT_SLEEVE);
			 * playermodel.bipedRightArmwear.showModel =
			 * clientPlayer.isWearing(PlayerModelPart.RIGHT_SLEEVE);
			 */
			playermodel.crouching = clientPlayer.isCrouching();

			/*
			 * BipedModel.ArmPose bipedmodel$armpose = getArmPose(clientPlayer,
			 * Hand.MAIN_HAND); BipedModel.ArmPose bipedmodel$armpose1 =
			 * getArmPose(clientPlayer, Hand.OFF_HAND); if
			 * (bipedmodel$armpose.isTwoHanded()) { bipedmodel$armpose1 =
			 * clientPlayer.getHeldItemOffhand().isEmpty() ? BipedModel.ArmPose.EMPTY :
			 * BipedModel.ArmPose.ITEM; }
			 * 
			 * if (clientPlayer.getPrimaryHand() == HandSide.RIGHT) {
			 * playermodel.rightArmPose = bipedmodel$armpose; playermodel.leftArmPose =
			 * bipedmodel$armpose1; } else { playermodel.rightArmPose = bipedmodel$armpose1;
			 * playermodel.leftArmPose = bipedmodel$armpose; }
			 */
		}

	}

	/*
	 * private static BipedModel.ArmPose getArmPose(PlayerEntity p_241741_0_,
	 * Hand p_241741_1_) { ItemStack itemstack =
	 * p_241741_0_.getHeldItem(p_241741_1_); if (itemstack.isEmpty()) { return
	 * BipedModel.ArmPose.EMPTY; } else { if (p_241741_0_.getActiveHand() ==
	 * p_241741_1_ && p_241741_0_.getItemInUseCount() > 0) { UseAction useaction =
	 * itemstack.getUseAction(); if (useaction == UseAction.BLOCK) { return
	 * BipedModel.ArmPose.BLOCK; }
	 * 
	 * if (useaction == UseAction.BOW) { return BipedModel.ArmPose.BOW_AND_ARROW; }
	 * 
	 * if (useaction == UseAction.SPEAR) { return BipedModel.ArmPose.THROW_SPEAR; }
	 * 
	 * if (useaction == UseAction.CROSSBOW && p_241741_1_ ==
	 * p_241741_0_.getActiveHand()) { return BipedModel.ArmPose.CROSSBOW_CHARGE; } }
	 * else if (!p_241741_0_.isSwingInProgress && itemstack.getItem() ==
	 * Items.CROSSBOW && CrossbowItem.isCharged(itemstack)) { return
	 * BipedModel.ArmPose.CROSSBOW_HOLD; }
	 * 
	 * return BipedModel.ArmPose.ITEM; } }
	 */

	protected void scale(PlayerEntity entitylivingbaseIn, MatrixStack matrixStackIn,
			float partialTickTime) {

		super.scale(entitylivingbaseIn, matrixStackIn, partialTickTime);
		/*
		 * float f = 0.9375F; matrixStackIn.scale(0.9375F, 0.9375F, 0.9375F);
		 */
	}

	protected void renderNameTag(PlayerEntity entityIn, ITextComponent displayNameIn, MatrixStack matrixStackIn,
			IRenderTypeBuffer bufferIn, int packedLightIn) {
		super.renderNameTag(entityIn, displayNameIn, matrixStackIn, bufferIn, packedLightIn);

		double d0 = this.entityRenderDispatcher.distanceToSqr(entityIn);
		matrixStackIn.pushPose();
		if (d0 < 100.0D) {
			Scoreboard scoreboard = entityIn.getScoreboard();
			ScoreObjective scoreobjective = scoreboard.getDisplayObjective(2);
			if (scoreobjective != null) {
				Score score = scoreboard.getOrCreatePlayerScore(entityIn.getScoreboardName(), scoreobjective);
				/*super.renderName(entityIn, (new StringTextComponent(Integer.toString(score.getScorePoints())))
						.appendString(" ").append(scoreobjective.getDisplayName()), matrixStackIn, bufferIn,
						packedLightIn);*/
				matrixStackIn.translate(0.0D, (double) (9.0F * 1.15F * 0.025F), 0.0D);
			}
		}

		super.renderNameTag(entityIn, displayNameIn, matrixStackIn, bufferIn, packedLightIn);
		matrixStackIn.popPose();

	}

	public void renderRightHand(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int combinedLightIn,
			PlayerEntity playerIn) {
		/*
		 * this.renderItem(matrixStackIn, bufferIn, combinedLightIn, playerIn,
		 * (this.entityModel).bipedRightArm, (this.entityModel).bipedRightArmwear);
		 */
	}

	public void renderLeftHand(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int combinedLightIn,
			PlayerEntity playerIn) {
		/*
		 * this.renderItem(matrixStackIn, bufferIn, combinedLightIn, playerIn,
		 * (this.entityModel).bipedLeftArm, (this.entityModel).bipedLeftArmwear);
		 */
	}

	/*
	 * private void renderItem(MatrixStack matrixStackIn, IRenderTypeBuffer
	 * bufferIn, int combinedLightIn, PlayerEntity playerIn, ModelRenderer
	 * rendererArmIn, ModelRenderer rendererArmwearIn) {
	 * WerewolfTransformedModel<PlayerEntity> playermodel = this.getEntityModel();
	 * this.setModelVisibilities(playerIn); playermodel.swingProgress = 0.0F;
	 * playermodel.isSneak = false; playermodel.swimAnimation = 0.0F;
	 * playermodel.setRotationAngles(playerIn, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
	 * rendererArmIn.rotateAngleX = 0.0F; rendererArmIn.render(matrixStackIn,
	 * bufferIn.getBuffer(RenderType.getEntitySolid(((AbstractClientPlayerEntity)
	 * playerIn).getLocationSkin())), combinedLightIn, OverlayTexture.NO_OVERLAY);
	 * rendererArmwearIn.rotateAngleX = 0.0F;
	 * rendererArmwearIn.render(matrixStackIn,
	 * bufferIn.getBuffer(RenderType.getEntityTranslucent(((
	 * AbstractClientPlayerEntity) playerIn).getLocationSkin())), combinedLightIn,
	 * OverlayTexture.NO_OVERLAY); }
	 */

	protected void setupRotations(PlayerEntity entityLiving, MatrixStack matrixStackIn, float ageInTicks,
			float rotationYaw, float partialTicks) {

		boolean cameraFollows = true;

		if (cameraFollows) {
			float f = entityLiving.getSwimAmount(partialTicks);
			if (entityLiving.isFallFlying()) {
				super.setupRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
				float f1 = (float) entityLiving.getFallFlyingTicks() + partialTicks;
				float f2 = MathHelper.clamp(f1 * f1 / 100.0F, 0.0F, 1.0F);
				if (!entityLiving.isAutoSpinAttack()) {
					matrixStackIn.mulPose(Vector3f.XP.rotationDegrees(f2 * (-90.0F - entityLiving.xRot)));
				}

				Vector3d vector3d = entityLiving.getViewVector(partialTicks);
				Vector3d vector3d1 = entityLiving.getDeltaMovement();
				double d0 = Entity.getHorizontalDistanceSqr(vector3d1);
				double d1 = Entity.getHorizontalDistanceSqr(vector3d);
				if (d0 > 0.0D && d1 > 0.0D) {
					double d2 = (vector3d1.x * vector3d.x + vector3d1.z * vector3d.z) / Math.sqrt(d0 * d1);
					double d3 = vector3d1.x * vector3d.z - vector3d1.z * vector3d.x;
					matrixStackIn.mulPose(Vector3f.YP.rotation((float) (Math.signum(d3) * Math.acos(d2))));
				}
			} else if (f > 0.0F) {
				super.setupRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
				float f3 = entityLiving.isInWater() ? -90.0F - entityLiving.xRot : -90.0F;
				float f4 = MathHelper.lerp(f, 0.0F, f3);
				matrixStackIn.mulPose(Vector3f.XP.rotationDegrees(f4));
				if (entityLiving.isVisuallySwimming()) {
					matrixStackIn.translate(0.0D, -1.0D, (double) 0.3F);
				}
			} else {
				super.setupRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
			}
		}

	}

	@Override
	public ResourceLocation getTextureLocation(PlayerEntity entity) {
		return werewolfTexture;
	}
}
