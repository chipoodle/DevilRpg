package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.WerewolfArrowLayer;
import com.chipoodle.devilrpg.client.render.entity.layer.WerewolfItemInHandLayer;
import com.chipoodle.devilrpg.client.render.entity.layer.WerewolfSpinAttackEffectLayer;
import com.chipoodle.devilrpg.client.render.entity.model.WerewolfTransformedModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class WerewolfRenderer extends LivingEntityRenderer<AbstractClientPlayer, WerewolfTransformedModel<AbstractClientPlayer>> {

    public static final ResourceLocation WEREWOLF_TEXTURE = new ResourceLocation(DevilRpg.MODID + ":/textures/entity/werewolf/wolftimber.png");

    public WerewolfRenderer(EntityRendererProvider.Context context, boolean p_174558_) {
        super(context, new WerewolfTransformedModel<>(context.bakeLayer(WerewolfTransformedModel.WEREWOLF_LAYER_LOCATION)), 0.5F);
        //this.addLayer(new WerewolfArmorLayer<>(this, new WerewolfTransformedModel(context.bakeLayer( ModelLayers.PLAYER_INNER_ARMOR)), new WerewolfTransformedModel(context.bakeLayer( ModelLayers.PLAYER_OUTER_ARMOR))));
        //this.addLayer(new WerewolfArmorLayer<>(this, new WerewolfTransformedModel(context.bakeLayer(WerewolfTransformedModel.WEREWOLF_INNER_ARMOR_LAYER_LOCATION)), new WerewolfTransformedModel(context.bakeLayer(WerewolfTransformedModel.WEREWOLF_OUTER_ARMOR_LAYER_LOCATION))));
        this.addLayer(new WerewolfItemInHandLayer<>(this, context.getItemInHandRenderer(), context.getEntityRenderDispatcher()));
        this.addLayer(new WerewolfArrowLayer<>(context, this));
        //boolean b = this.addLayer(new Deadmau5EarsLayer(this));
        //this.addLayer(new CapeLayer(this));
        //this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
        //this.addLayer(new ParrotOnShoulderLayer<>(this, context.getModelSet()));
        this.addLayer(new WerewolfSpinAttackEffectLayer<>(this, context.getModelSet()));
        //this.addLayer(new BeeStingerLayer<>(this));
    }

    private static HumanoidModel.ArmPose getArmPose(AbstractClientPlayer p_117795_, InteractionHand p_117796_) {
        ItemStack itemstack = p_117795_.getItemInHand(p_117796_);
        if (itemstack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        } else {
            if (p_117795_.getUsedItemHand() == p_117796_ && p_117795_.getUseItemRemainingTicks() > 0) {
                UseAnim useanim = itemstack.getUseAnimation();
                if (useanim == UseAnim.BLOCK) {
                    return HumanoidModel.ArmPose.BLOCK;
                }

                if (useanim == UseAnim.BOW) {
                    return HumanoidModel.ArmPose.BOW_AND_ARROW;
                }

                if (useanim == UseAnim.SPEAR) {
                    return HumanoidModel.ArmPose.THROW_SPEAR;
                }

                if (useanim == UseAnim.CROSSBOW && p_117796_ == p_117795_.getUsedItemHand()) {
                    return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                }

                if (useanim == UseAnim.SPYGLASS) {
                    return HumanoidModel.ArmPose.SPYGLASS;
                }

                if (useanim == UseAnim.TOOT_HORN) {
                    return HumanoidModel.ArmPose.TOOT_HORN;
                }
            } else if (!p_117795_.swinging && itemstack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(itemstack)) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }

            HumanoidModel.ArmPose forgeArmPose = net.minecraftforge.client.extensions.common.IClientItemExtensions.of(itemstack).getArmPose(p_117795_, p_117796_, itemstack);
            if (forgeArmPose != null) return forgeArmPose;

            return HumanoidModel.ArmPose.ITEM;
        }
    }

    public void render(@NotNull AbstractClientPlayer player, float i, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int packedLight) {
        this.setModelProperties(player);
        super.render(player, i, partialTicks, poseStack, multiBufferSource, packedLight);
    }

    public Vec3 getRenderOffset(AbstractClientPlayer p_117785_, float p_117786_) {
        return p_117785_.isCrouching() ? new Vec3(0.0D, -0.125D, 0.0D) : super.getRenderOffset(p_117785_, p_117786_);
    }

    private void setModelProperties(AbstractClientPlayer p_117819_) {
        WerewolfTransformedModel<AbstractClientPlayer> werewolfHumanModel = this.getModel();
        if (p_117819_.isSpectator()) {
            //WerewolfHumanModel.setAllVisible(false);
            werewolfHumanModel.head.visible = true;
            //WerewolfHumanModel.hat.visible = true;
        } else {
            //WerewolfHumanModel.setAllVisible(true);
			/*WerewolfHumanModel.hat.visible = p_117819_.isModelPartShown(WerewolfHumanModelPart.HAT);
			WerewolfHumanModel.jacket.visible = p_117819_.isModelPartShown(WerewolfHumanModelPart.JACKET);
			WerewolfHumanModel.leftPants.visible = p_117819_.isModelPartShown(WerewolfHumanModelPart.LEFT_PANTS_LEG);
			WerewolfHumanModel.rightPants.visible = p_117819_.isModelPartShown(WerewolfHumanModelPart.RIGHT_PANTS_LEG);
			WerewolfHumanModel.leftSleeve.visible = p_117819_.isModelPartShown(WerewolfHumanModelPart.LEFT_SLEEVE);
			WerewolfHumanModel.rightSleeve.visible = p_117819_.isModelPartShown(WerewolfHumanModelPart.RIGHT_SLEEVE);*/
            //WerewolfHumanModel.crouching = p_117819_.isCrouching();
            HumanoidModel.ArmPose humanoidmodel$armpose = getArmPose(p_117819_, InteractionHand.MAIN_HAND);
            HumanoidModel.ArmPose humanoidmodel$armpose1 = getArmPose(p_117819_, InteractionHand.OFF_HAND);
            /*if (humanoidmodel$armpose.isTwoHanded()) {
                humanoidmodel$armpose1 = p_117819_.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
            }*/

            if (p_117819_.getMainArm() == HumanoidArm.RIGHT) {
                werewolfHumanModel.rightArmPose = humanoidmodel$armpose;
                werewolfHumanModel.leftArmPose = humanoidmodel$armpose1;
            } else {
                werewolfHumanModel.rightArmPose = humanoidmodel$armpose1;
                werewolfHumanModel.leftArmPose = humanoidmodel$armpose;
            }
        }

    }

    public @NotNull ResourceLocation getTextureLocation(AbstractClientPlayer p_117783_) {
        return WEREWOLF_TEXTURE;
    }

    protected void scale(@NotNull AbstractClientPlayer p_117798_, @NotNull PoseStack p_117799_, float p_117800_) {
        /*float f = 0.9375F;
        p_117799_.scale(f, f, f);*/
    }

    protected void renderNameTag(AbstractClientPlayer p_117808_, Component p_117809_, PoseStack p_117810_, MultiBufferSource p_117811_, int p_117812_) {
        double d0 = this.entityRenderDispatcher.distanceToSqr(p_117808_);
        p_117810_.pushPose();
        if (d0 < 100.0D) {
            Scoreboard scoreboard = p_117808_.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(2);
            if (objective != null) {
                Score score = scoreboard.getOrCreatePlayerScore(p_117808_.getScoreboardName(), objective);
                super.renderNameTag(p_117808_, Component.literal(Integer.toString(score.getScore())).append(" ").append(objective.getDisplayName()), p_117810_, p_117811_, p_117812_);
                p_117810_.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
            }
        }

        super.renderNameTag(p_117808_, p_117809_, p_117810_, p_117811_, p_117812_);
        p_117810_.popPose();
    }

    public void renderRightHand(PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, AbstractClientPlayer player) {
        this.renderHand(poseStack, multiBufferSource, packedLight, player, (this.model).rightArm, null/*(this.model).rightSleeve*/);
    }

    public void renderLeftHand(PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, AbstractClientPlayer player) {
        this.renderHand(poseStack, multiBufferSource, packedLight, player, (this.model).leftArm, null/*(this.model).leftSleeve*/);
    }

    private void renderHand(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve) {
        WerewolfTransformedModel<AbstractClientPlayer> werewolfHumanModel = this.getModel();
        this.setModelProperties(player);
        werewolfHumanModel.attackTime = 0.0F;
        //werewolfHumanModel.crouching = false;
        //werewolfHumanModel.swimAmount = 0.0F;
        werewolfHumanModel.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        arm.xRot = 0.0F;
        arm.render(poseStack, bufferSource.getBuffer(RenderType.entitySolid(WerewolfRenderer.WEREWOLF_TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        arm.xRot = 0.0F;
        arm.render(poseStack, bufferSource.getBuffer(RenderType.entityTranslucent(WerewolfRenderer.WEREWOLF_TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
    }

    protected void setupRotations(AbstractClientPlayer entity, @NotNull PoseStack cameraPosition, float cameraRotation, float entityRotation, float entityPitch) {
        float f = entity.getSwimAmount(entityPitch);
        if (entity.isFallFlying()) {
            super.setupRotations(entity, cameraPosition, cameraRotation, entityRotation, entityPitch);
            float f1 = (float) entity.getFallFlyingTicks() + entityPitch;
            float f2 = Mth.clamp(f1 * f1 / 100.0F, 0.0F, 1.0F);
            if (!entity.isAutoSpinAttack()) {
                cameraPosition.mulPose(Axis.XP.rotationDegrees(f2 * (-90.0F - entity.getXRot())));
            }

            Vec3 vec3 = entity.getViewVector(entityPitch);
            Vec3 vec31 = entity.getDeltaMovement();
            double d0 = vec31.horizontalDistanceSqr();
            double d1 = vec3.horizontalDistanceSqr();
            if (d0 > 0.0D && d1 > 0.0D) {
                double d2 = (vec31.x * vec3.x + vec31.z * vec3.z) / Math.sqrt(d0 * d1);
                double d3 = vec31.x * vec3.z - vec31.z * vec3.x;
                cameraPosition.mulPose(Axis.YP.rotation((float) (Math.signum(d3) * Math.acos(d2))));
            }
        } else if (f > 0.0F) {
            super.setupRotations(entity, cameraPosition, cameraRotation, entityRotation, entityPitch);
            float f3 = entity.isInWater() || entity.isInFluidType((fluidType, height) -> entity.canSwimInFluidType(fluidType)) ? -90.0F - entity.getXRot() : -90.0F;
            float f4 = Mth.lerp(f, 0.0F, f3);
            cameraPosition.mulPose(Axis.XP.rotationDegrees(f4));
            if (entity.isVisuallySwimming()) {
                cameraPosition.translate(0.0F, -1.0F, 0.3F);
            }
        } else {
            super.setupRotations(entity, cameraPosition, cameraRotation, entityRotation, entityPitch);
        }

    }

}