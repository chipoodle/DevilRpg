package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.client.render.entity.model.WerewolfTransformedModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

public class WerewolfItemInHandLayer<T extends Player, M extends WerewolfTransformedModel<T> & ArmedModel & HeadedModel> extends ItemInHandLayer<T, M> {
    private static final float X_ROT_MIN = (-(float) Math.PI / 6F);
    private static final float X_ROT_MAX = ((float) Math.PI / 2F);
    private final ItemInHandRenderer itemInHandRenderer;
    private final EntityRenderDispatcher entityRenderDispatcher;

    public WerewolfItemInHandLayer(RenderLayerParent<T, M> p_234866_, ItemInHandRenderer p_234867_, EntityRenderDispatcher entityRenderDispatcher
    ) {
        super(p_234866_, p_234867_);
        this.itemInHandRenderer = p_234867_;
        this.entityRenderDispatcher = entityRenderDispatcher;
    }

    protected void renderArmWithItem(@NotNull LivingEntity entity, ItemStack itemS, @NotNull ItemDisplayContext displayContext, @NotNull HumanoidArm humanoidArm, @NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int packedLight) {
        if (itemS.is(Items.SPYGLASS) && entity.getUseItem() == itemS && entity.swingTime == 0) {
            this.renderArmWithSpyglass(entity, itemS, humanoidArm, poseStack, multiBufferSource, packedLight);
        } else {
            super.renderArmWithItem(entity, itemS, displayContext, humanoidArm, poseStack, multiBufferSource, packedLight);
        }

    }

    private void renderArmWithSpyglass(LivingEntity p_174518_, ItemStack p_174519_, HumanoidArm p_174520_, PoseStack p_174521_, MultiBufferSource p_174522_, int p_174523_) {
        p_174521_.pushPose();
        ModelPart modelpart = this.getParentModel().getHead();
        float f = modelpart.xRot;
        modelpart.xRot = Mth.clamp(modelpart.xRot, (-(float) Math.PI / 6F), ((float) Math.PI / 2F));
        modelpart.translateAndRotate(p_174521_);
        modelpart.xRot = f;
        CustomHeadLayer.translateToHead(p_174521_, false);
        boolean flag = p_174520_ == HumanoidArm.LEFT;
        p_174521_.translate((flag ? -2.5F : 2.5F) / 16.0F, -0.0625F, 0.0F);
        this.itemInHandRenderer.renderItem(p_174518_, p_174519_, ItemDisplayContext.HEAD, false, p_174521_, p_174522_, p_174523_);
        p_174521_.popPose();
    }

    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int packedLight, T player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean flag = player.getMainArm() == HumanoidArm.RIGHT;
        ItemStack itemstack = flag ? player.getOffhandItem() : player.getMainHandItem();
        ItemStack itemstack1 = flag ? player.getMainHandItem() : player.getOffhandItem();
        /*poseStack.pushPose();
        //TODO: Aqui es donde se cabia la traslación y la ecala de los items para que cuadren con la posición de la mano


        this.renderArmWithItem(player, itemstack1, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT, poseStack, multiBufferSource, packedLight);
        this.renderArmWithItem(player, itemstack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT, poseStack, multiBufferSource, packedLight);
        poseStack.popPose();*/

       // this.renderBothHands(poseStack, multiBufferSource, packedLight, player);


    }

    /*public void renderBothHands(PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, LivingEntity player) {
        this.renderArmWithItem(player, player.getOffhandItem(), ItemDisplayContext.FIRST_PERSON_LEFT_HAND, HumanoidArm.LEFT, poseStack, multiBufferSource, packedLight);
        this.renderArmWithItem(player, player.getMainHandItem(), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, HumanoidArm.RIGHT, poseStack, multiBufferSource, packedLight);
    }*/

}