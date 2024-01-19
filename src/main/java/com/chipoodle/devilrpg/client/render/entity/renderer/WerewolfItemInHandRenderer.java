package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class WerewolfItemInHandRenderer {
    private static final RenderType MAP_BACKGROUND = RenderType.text(new ResourceLocation("textures/map/map_background.png"));
    private static final RenderType MAP_BACKGROUND_CHECKERBOARD = RenderType.text(new ResourceLocation("textures/map/map_background_checkerboard.png"));
    private static final float ITEM_SWING_X_POS_SCALE = -0.4F;
    private static final float ITEM_SWING_Y_POS_SCALE = 0.2F;
    private static final float ITEM_SWING_Z_POS_SCALE = -0.2F;
    private static final float ITEM_HEIGHT_SCALE = -0.6F;
    private static final float ITEM_POS_X = 0.56F;
    private static final float ITEM_POS_Y = -0.52F;
    private static final float ITEM_POS_Z = -0.72F;
    private static final float ITEM_PRESWING_ROT_Y = 45.0F;
    private static final float ITEM_SWING_X_ROT_AMOUNT = -80.0F;
    private static final float ITEM_SWING_Y_ROT_AMOUNT = -20.0F;
    private static final float ITEM_SWING_Z_ROT_AMOUNT = -20.0F;
    private static final float EAT_JIGGLE_X_ROT_AMOUNT = 10.0F;
    private static final float EAT_JIGGLE_Y_ROT_AMOUNT = 90.0F;
    private static final float EAT_JIGGLE_Z_ROT_AMOUNT = 30.0F;
    private static final float EAT_JIGGLE_X_POS_SCALE = 0.6F;
    private static final float EAT_JIGGLE_Y_POS_SCALE = -0.5F;
    private static final float EAT_JIGGLE_Z_POS_SCALE = 0.0F;
    private static final double EAT_JIGGLE_EXPONENT = 27.0D;
    private static final float EAT_EXTRA_JIGGLE_CUTOFF = 0.8F;
    private static final float EAT_EXTRA_JIGGLE_SCALE = 0.1F;
    private static final float ARM_SWING_X_POS_SCALE = -0.3F;
    private static final float ARM_SWING_Y_POS_SCALE = 0.4F;
    private static final float ARM_SWING_Z_POS_SCALE = -0.4F;
    private static final float ARM_SWING_Y_ROT_AMOUNT = 70.0F;
    private static final float ARM_SWING_Z_ROT_AMOUNT = -20.0F;
    private static final float ARM_HEIGHT_SCALE = -0.6F;
    private static final float ARM_POS_SCALE = 0.8F;
    private static final float ARM_POS_X = 0.8F;
    private static final float ARM_POS_Y = -0.75F;
    private static final float ARM_POS_Z = -0.9F;
    private static final float ARM_PRESWING_ROT_Y = 45.0F;
    private static final float ARM_PREROTATION_X_OFFSET = -1.0F;
    private static final float ARM_PREROTATION_Y_OFFSET = 3.6F;
    private static final float ARM_PREROTATION_Z_OFFSET = 3.5F;
    private static final float ARM_POSTROTATION_X_OFFSET = 5.6F;
    private static final int ARM_ROT_X = 200;
    private static final int ARM_ROT_Y = -135;
    private static final int ARM_ROT_Z = 120;
    private static final float MAP_SWING_X_POS_SCALE = -0.4F;
    private static final float MAP_SWING_Z_POS_SCALE = -0.2F;
    private static final float MAP_HANDS_POS_X = 0.0F;
    private static final float MAP_HANDS_POS_Y = 0.04F;
    private static final float MAP_HANDS_POS_Z = -0.72F;
    private static final float MAP_HANDS_HEIGHT_SCALE = -1.2F;
    private static final float MAP_HANDS_TILT_SCALE = -0.5F;
    private static final float MAP_PLAYER_PITCH_SCALE = 45.0F;
    private static final float MAP_HANDS_Z_ROT_AMOUNT = -85.0F;
    private static final float MAPHAND_X_ROT_AMOUNT = 45.0F;
    private static final float MAPHAND_Y_ROT_AMOUNT = 92.0F;
    private static final float MAPHAND_Z_ROT_AMOUNT = -41.0F;
    private static final float MAP_HAND_X_POS = 0.3F;
    private static final float MAP_HAND_Y_POS = -1.1F;
    private static final float MAP_HAND_Z_POS = 0.45F;
    private static final float MAP_SWING_X_ROT_AMOUNT = 20.0F;
    private static final float MAP_PRE_ROT_SCALE = 0.38F;
    private static final float MAP_GLOBAL_X_POS = -0.5F;
    private static final float MAP_GLOBAL_Y_POS = -0.5F;
    private static final float MAP_GLOBAL_Z_POS = 0.0F;
    private static final float MAP_FINAL_SCALE = 0.0078125F;
    private static final int MAP_BORDER = 7;
    private static final int MAP_HEIGHT = 128;
    private static final int MAP_WIDTH = 128;
    private static final float BOW_CHARGE_X_POS_SCALE = 0.0F;
    private static final float BOW_CHARGE_Y_POS_SCALE = 0.0F;
    private static final float BOW_CHARGE_Z_POS_SCALE = 0.04F;
    private static final float BOW_CHARGE_SHAKE_X_SCALE = 0.0F;
    private static final float BOW_CHARGE_SHAKE_Y_SCALE = 0.004F;
    private static final float BOW_CHARGE_SHAKE_Z_SCALE = 0.0F;
    private static final float BOW_CHARGE_Z_SCALE = 0.2F;
    private static final float BOW_MIN_SHAKE_CHARGE = 0.1F;
    private final Minecraft minecraft;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private final ItemRenderer itemRenderer;
    private ItemStack mainHandItem = ItemStack.EMPTY;
    private ItemStack offHandItem = ItemStack.EMPTY;
    private float mainHandHeight;
    private float oMainHandHeight;
    private float offHandHeight;
    private float oOffHandHeight;
    private WerewolfRenderer werewolfenderer;

    public WerewolfItemInHandRenderer(Minecraft p_234241_, EntityRenderDispatcher p_234242_, ItemRenderer p_234243_, WerewolfRenderer werewolfenderer) {
        this.minecraft = p_234241_;
        this.entityRenderDispatcher = p_234242_;
        this.itemRenderer = p_234243_;
        this.werewolfenderer = werewolfenderer;
    }

    @VisibleForTesting
    static WerewolfItemInHandRenderer.HandRenderSelection evaluateWhichHandsToRender(LocalPlayer p_172915_) {
        return WerewolfItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
    }

    private void renderPlayerArm(PoseStack p_109347_, MultiBufferSource p_109348_, int p_109349_, float p_109350_, float p_109351_, HumanoidArm p_109352_) {
        boolean flag = p_109352_ != HumanoidArm.LEFT;
        //boolean flag = p_109352_ != HumanoidArm.RIGHT;
        float f = flag ? 1.0F : -1.0F;
        float f1 = Mth.sqrt(p_109351_);
        float f2 = -0.3F * Mth.sin(f1 * (float) Math.PI);
        float f3 = 0.4F * Mth.sin(f1 * ((float) Math.PI * 2F));
        float f4 = -0.4F * Mth.sin(p_109351_ * (float) Math.PI);
        p_109347_.translate(f * (f2 + 0.64000005F), f3 + -0.6F + p_109350_ * -0.6F, f4 + -0.71999997F);
        p_109347_.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float f5 = Mth.sin(p_109351_ * p_109351_ * (float) Math.PI);
        float f6 = Mth.sin(f1 * (float) Math.PI);
        p_109347_.mulPose(Axis.YP.rotationDegrees(f * f6 * 70.0F));
        p_109347_.mulPose(Axis.ZP.rotationDegrees(f * f5 * -20.0F));
        AbstractClientPlayer abstractclientplayer = this.minecraft.player;
       // RenderSystem.setShaderTexture(0, abstractclientplayer.getSkinTextureLocation());
        RenderSystem.setShaderTexture(0, WerewolfRenderer.WEREWOLF_TEXTURE);
        p_109347_.translate(f * -1.0F, 3.6F, 3.5F);
        p_109347_.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        p_109347_.mulPose(Axis.XP.rotationDegrees(200.0F));
        p_109347_.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        p_109347_.translate(f * 5.6F, 0.0F, 0.0F);
        p_109347_.translate(0.0F, 0.0F, -1.7F);//
        //WerewolfRenderer werewolfRenderer = (WerewolfRenderer) this.entityRenderDispatcher.getRenderer(abstractclientplayer);
        WerewolfRenderer werewolfRenderer = this.werewolfenderer;

        if (flag) {
            werewolfRenderer.renderRightHand(p_109347_, p_109348_, p_109349_, abstractclientplayer);
        } else {
            werewolfRenderer.renderLeftHand(p_109347_, p_109348_, p_109349_, abstractclientplayer);
        }

    }

    private void applyEatTransform(PoseStack p_109331_, float p_109332_, HumanoidArm p_109333_, ItemStack p_109334_) {
        float f = (float) this.minecraft.player.getUseItemRemainingTicks() - p_109332_ + 1.0F;
        float f1 = f / (float) p_109334_.getUseDuration();
        if (f1 < 0.8F) {
            float f2 = Mth.abs(Mth.cos(f / 4.0F * (float) Math.PI) * 0.1F);
            p_109331_.translate(0.0F, f2, 0.0F);
        }

        float f3 = 1.0F - (float) Math.pow((double) f1, 27.0D);
        int i = p_109333_ == HumanoidArm.RIGHT ? 1 : -1;
        p_109331_.translate(f3 * 0.6F * (float) i, f3 * -0.5F, f3 * 0.0F);
        p_109331_.mulPose(Axis.YP.rotationDegrees((float) i * f3 * 90.0F));
        p_109331_.mulPose(Axis.XP.rotationDegrees(f3 * 10.0F));
        p_109331_.mulPose(Axis.ZP.rotationDegrees((float) i * f3 * 30.0F));
    }

    private void applyBrushTransform(PoseStack p_273513_, float p_273245_, HumanoidArm p_273726_, ItemStack p_272809_, float p_273333_) {
        this.applyItemArmTransform(p_273513_, p_273726_, p_273333_);
        float f = (float) this.minecraft.player.getUseItemRemainingTicks() - p_273245_ + 1.0F;
        float f1 = 1.0F - f / (float) p_272809_.getUseDuration();
        float f2 = -90.0F;
        float f3 = 60.0F;
        int i = 45;
        float f4 = 150.0F;
        float f5 = -15.0F;
        float f6 = -15.0F + 75.0F * Mth.cos(f1 * 45.0F * (float) Math.PI);
        if (p_273726_ != HumanoidArm.RIGHT) {
            p_273513_.translate(0.1D, 0.83D, 0.35D);
            p_273513_.mulPose(Axis.XP.rotationDegrees(-80.0F));
            p_273513_.mulPose(Axis.YP.rotationDegrees(-90.0F));
            p_273513_.mulPose(Axis.XP.rotationDegrees(f6));
            p_273513_.translate(-0.3D, 0.22D, 0.35D);
        } else {
            p_273513_.translate(-0.25D, 0.22D, 0.35D);
            p_273513_.mulPose(Axis.XP.rotationDegrees(-80.0F));
            p_273513_.mulPose(Axis.YP.rotationDegrees(90.0F));
            p_273513_.mulPose(Axis.ZP.rotationDegrees(0.0F));
            p_273513_.mulPose(Axis.XP.rotationDegrees(f6));
        }

    }

    private void applyItemArmAttackTransform(PoseStack p_109336_, HumanoidArm p_109337_, float p_109338_) {
        int i = p_109337_ == HumanoidArm.RIGHT ? 1 : -1;
        float f = Mth.sin(p_109338_ * p_109338_ * (float) Math.PI);
        p_109336_.mulPose(Axis.YP.rotationDegrees((float) i * (45.0F + f * -20.0F)));
        float f1 = Mth.sin(Mth.sqrt(p_109338_) * (float) Math.PI);
        p_109336_.mulPose(Axis.ZP.rotationDegrees((float) i * f1 * -20.0F));
        p_109336_.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
        p_109336_.mulPose(Axis.YP.rotationDegrees((float) i * -45.0F));
    }

    private void applyItemArmTransform(PoseStack p_109383_, HumanoidArm p_109384_, float p_109385_) {
        int i = p_109384_ == HumanoidArm.RIGHT ? 1 : -1;
        p_109383_.translate((float) i * 0.56F, -0.52F + p_109385_ * -0.6F, -0.72F);
    }

    public void renderHandsWithItems(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource multiBufferSource, LocalPlayer player, int packedLight) {
        float f = player.getAttackAnim(partialTicks);
        InteractionHand interactionhand = MoreObjects.firstNonNull(player.swingingArm, InteractionHand.MAIN_HAND);
        float f1 = Mth.lerp(partialTicks, player.xRotO, player.getXRot());
        WerewolfItemInHandRenderer.HandRenderSelection iteminhandrenderer$handrenderselection = evaluateWhichHandsToRender(player);
        float f2 = Mth.lerp(partialTicks, player.xBobO, player.xBob);
        float f3 = Mth.lerp(partialTicks, player.yBobO, player.yBob);
        poseStack.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(partialTicks) - f2) * 0.1F));
        poseStack.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partialTicks) - f3) * 0.1F));
        if (iteminhandrenderer$handrenderselection.renderMainHand) {
            float f4 = interactionhand == InteractionHand.MAIN_HAND ? f : 0.0F;
            float f5 = 1.0F - Mth.lerp(partialTicks, this.oMainHandHeight, this.mainHandHeight);
            //if(!net.minecraftforge.client.ForgeHooksClient.renderSpecificFirstPersonHand(InteractionHand.MAIN_HAND, poseStack, multiBufferSource, packedLight, partialTicks, f1, f4, f5, this.mainHandItem))
            this.renderArmWithItem(player, partialTicks, f1, InteractionHand.MAIN_HAND, f4, this.mainHandItem, f5, poseStack, multiBufferSource, packedLight);
        }

        if (iteminhandrenderer$handrenderselection.renderOffHand) {
            float f6 = interactionhand == InteractionHand.OFF_HAND ? f : 0.0F;
            float f7 = 1.0F - Mth.lerp(partialTicks, this.oOffHandHeight, this.offHandHeight);
            //if(!net.minecraftforge.client.ForgeHooksClient.renderSpecificFirstPersonHand(InteractionHand.OFF_HAND, poseStack, multiBufferSource, packedLight, partialTicks, f1, f6, f7, this.offHandItem))
            this.renderArmWithItem(player, partialTicks, f1, InteractionHand.OFF_HAND, f6, this.offHandItem, f7, poseStack, multiBufferSource, packedLight);
        }

        multiBufferSource.endBatch();
    }

    private void renderArmWithItem(AbstractClientPlayer player, float partialTicks, float p_109374_, InteractionHand interactionHand, float p_109376_, ItemStack itemStack, float p_109378_, PoseStack p_109379_, MultiBufferSource multiBufferSource, int packedLight) {
        if (!player.isScoping()) {
            boolean isMainHandFlag = interactionHand == InteractionHand.MAIN_HAND;
            HumanoidArm humanoidarm = isMainHandFlag ? player.getMainArm().getOpposite() : player.getMainArm();
            p_109379_.pushPose();

            if (!player.isInvisible())
                if (isMainHandFlag) {
                    this.renderPlayerArm(p_109379_, multiBufferSource, packedLight, p_109378_, p_109376_, humanoidarm);
                } else {
                    this.renderPlayerArm(p_109379_, multiBufferSource, packedLight, p_109378_, p_109376_, humanoidarm);
                }


            boolean isHumanoidArmRight = humanoidarm == HumanoidArm.LEFT;
            if (player.getUsedItemHand() == interactionHand) {
                int k = isHumanoidArmRight ? 1 : -1;
                switch (itemStack.getUseAnimation()) {
                    case NONE, BLOCK:
                        this.applyItemArmTransform(p_109379_, humanoidarm, p_109378_);
                        break;
                    case EAT:
                    case DRINK:
                        this.applyEatTransform(p_109379_, partialTicks, humanoidarm, itemStack);
                        this.applyItemArmTransform(p_109379_, humanoidarm, p_109378_);
                        break;
                    case BOW:
                        this.applyItemArmTransform(p_109379_, humanoidarm, p_109378_);
                        p_109379_.translate((float) k * -0.2785682F, 0.18344387F, 0.15731531F);
                        p_109379_.mulPose(Axis.XP.rotationDegrees(-13.935F));
                        p_109379_.mulPose(Axis.YP.rotationDegrees((float) k * 35.3F));
                        p_109379_.mulPose(Axis.ZP.rotationDegrees((float) k * -9.785F));
                        float f8 = (float) itemStack.getUseDuration() - ((float) this.minecraft.player.getUseItemRemainingTicks() - partialTicks + 1.0F);
                        float f12 = f8 / 20.0F;
                        f12 = (f12 * f12 + f12 * 2.0F) / 3.0F;
                        if (f12 > 1.0F) {
                            f12 = 1.0F;
                        }

                        if (f12 > 0.1F) {
                            float f15 = Mth.sin((f8 - 0.1F) * 1.3F);
                            float f18 = f12 - 0.1F;
                            float f20 = f15 * f18;
                            p_109379_.translate(f20 * 0.0F, f20 * 0.004F, f20 * 0.0F);
                        }

                        p_109379_.translate(f12 * 0.0F, f12 * 0.0F, f12 * 0.04F);
                        p_109379_.scale(1.0F, 1.0F, 1.0F + f12 * 0.2F);
                        p_109379_.mulPose(Axis.YN.rotationDegrees((float) k * 45.0F));
                        break;
                    case SPEAR:
                        this.applyItemArmTransform(p_109379_, humanoidarm, p_109378_);
                        p_109379_.translate((float) k * -0.5F, 0.7F, 0.1F);
                        p_109379_.mulPose(Axis.XP.rotationDegrees(-55.0F));
                        p_109379_.mulPose(Axis.YP.rotationDegrees((float) k * 35.3F));
                        p_109379_.mulPose(Axis.ZP.rotationDegrees((float) k * -9.785F));
                        float f7 = (float) itemStack.getUseDuration() - ((float) this.minecraft.player.getUseItemRemainingTicks() - partialTicks + 1.0F);
                        float f11 = f7 / 10.0F;
                        if (f11 > 1.0F) {
                            f11 = 1.0F;
                        }

                        if (f11 > 0.1F) {
                            float f14 = Mth.sin((f7 - 0.1F) * 1.3F);
                            float f17 = f11 - 0.1F;
                            float f19 = f14 * f17;
                            p_109379_.translate(f19 * 0.0F, f19 * 0.004F, f19 * 0.0F);
                        }

                        p_109379_.translate(0.0F, 0.0F, f11 * 0.2F);
                        p_109379_.scale(1.0F, 1.0F, 1.0F + f11 * 0.2F);
                        p_109379_.mulPose(Axis.YN.rotationDegrees((float) k * 45.0F));
                        break;
                    case BRUSH:
                        this.applyBrushTransform(p_109379_, partialTicks, humanoidarm, itemStack, p_109378_);
                }
            } else if (player.isAutoSpinAttack()) {
                this.applyItemArmTransform(p_109379_, humanoidarm, p_109378_);
                int j = isHumanoidArmRight ? 1 : -1;
                p_109379_.translate((float) j * -0.4F, 0.8F, 0.3F);
                p_109379_.mulPose(Axis.YP.rotationDegrees((float) j * 65.0F));
                p_109379_.mulPose(Axis.ZP.rotationDegrees((float) j * -85.0F));
            } else {
                float f5 = -0.4F * Mth.sin(Mth.sqrt(p_109376_) * (float) Math.PI);
                float f6 = 0.2F * Mth.sin(Mth.sqrt(p_109376_) * ((float) Math.PI * 2F));
                float f10 = -0.2F * Mth.sin(p_109376_ * (float) Math.PI);
                int l = isHumanoidArmRight ? 1 : -1;
                p_109379_.translate((float) l * f5, f6, f10);
                this.applyItemArmTransform(p_109379_, humanoidarm, p_109378_);
                this.applyItemArmAttackTransform(p_109379_, humanoidarm, p_109376_);
            }

            //this.renderItem(player, itemStack, isHumanoidArmRight ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !isHumanoidArmRight, p_109379_, multiBufferSource, packedLight);


            p_109379_.popPose();
        }
    }

    public void tick() {
        this.oMainHandHeight = this.mainHandHeight;
        this.oOffHandHeight = this.offHandHeight;
        LocalPlayer localplayer = this.minecraft.player;
        ItemStack itemstack = localplayer.getMainHandItem();
        ItemStack itemstack1 = localplayer.getOffhandItem();
        if (ItemStack.matches(this.mainHandItem, itemstack)) {
            this.mainHandItem = itemstack;
        }

        if (ItemStack.matches(this.offHandItem, itemstack1)) {
            this.offHandItem = itemstack1;
        }

        if (localplayer.isHandsBusy()) {
            this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
            this.offHandHeight = Mth.clamp(this.offHandHeight - 0.4F, 0.0F, 1.0F);
        } else {
            float f = localplayer.getAttackStrengthScale(1.0F);
            boolean requipM = net.minecraftforge.client.ForgeHooksClient.shouldCauseReequipAnimation(this.mainHandItem, itemstack, localplayer.getInventory().selected);
            boolean requipO = net.minecraftforge.client.ForgeHooksClient.shouldCauseReequipAnimation(this.offHandItem, itemstack1, -1);

            if (!requipM && this.mainHandItem != itemstack) this.mainHandItem = itemstack;
            if (!requipO && this.offHandItem != itemstack1) this.offHandItem = itemstack1;

            this.mainHandHeight += Mth.clamp((!requipM ? f * f * f : 0.0F) - this.mainHandHeight, -0.4F, 0.4F);
            this.offHandHeight += Mth.clamp((float) (!requipO ? 1 : 0) - this.offHandHeight, -0.4F, 0.4F);
        }

        if (this.mainHandHeight < 0.1F) {
            this.mainHandItem = itemstack;
        }

        if (this.offHandHeight < 0.1F) {
            this.offHandItem = itemstack1;
        }

    }

    public void itemUsed(InteractionHand p_109321_) {
        if (p_109321_ == InteractionHand.MAIN_HAND) {
            this.mainHandHeight = 0.0F;
        } else {
            this.offHandHeight = 0.0F;
        }

    }

    @OnlyIn(Dist.CLIENT)
    @VisibleForTesting
    static enum HandRenderSelection {
        RENDER_BOTH_HANDS(true, true), RENDER_MAIN_HAND_ONLY(true, false), RENDER_OFF_HAND_ONLY(false, true);

        final boolean renderMainHand;
        final boolean renderOffHand;

        private HandRenderSelection(boolean p_172928_, boolean p_172929_) {
            this.renderMainHand = p_172928_;
            this.renderOffHand = p_172929_;
        }

        public static WerewolfItemInHandRenderer.HandRenderSelection onlyForHand(InteractionHand p_172932_) {
            return p_172932_ == InteractionHand.MAIN_HAND ? RENDER_MAIN_HAND_ONLY : RENDER_OFF_HAND_ONLY;
        }
    }
}

