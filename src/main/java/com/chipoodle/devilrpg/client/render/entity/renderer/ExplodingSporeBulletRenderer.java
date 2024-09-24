package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.client.render.entity.model.ExplodingSporeBulletModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class ExplodingSporeBulletRenderer <T extends Entity> extends EntityRenderer<T> {
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation("textures/entity/shulker/spark.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE_LOCATION);
    private final ExplodingSporeBulletModel<T> model;

    public ExplodingSporeBulletRenderer(EntityRendererProvider.Context p_174368_) {
        super(p_174368_);
        this.model = new ExplodingSporeBulletModel<>(p_174368_.bakeLayer(ModelLayers.SHULKER_BULLET));
    }

    protected int getBlockLightLevel(@NotNull T explodingSporeBullet, @NotNull BlockPos blockPos) {
        return 15;
    }

    public void render(T entity, float p_115863_, float p_115864_, PoseStack poseStack, MultiBufferSource multiBufferSource, int p_115867_) {
        poseStack.pushPose();
        float f = Mth.rotLerp(p_115864_, entity.yRotO, entity.getYRot());
        float f1 = Mth.lerp(p_115864_, entity.xRotO, entity.getXRot());
        float f2 = (float) entity.tickCount + p_115864_;
        poseStack.translate(0.0F, 0.15F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(f2 * 0.1F) * 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.cos(f2 * 0.1F) * 180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(f2 * 0.15F) * 360.0F));
        poseStack.scale(-0.5F, -0.5F, 0.5F);
        this.model.setupAnim(entity, 0.0F, 0.0F, 0.0F, f, f1);
        VertexConsumer vertexconsumer = multiBufferSource.getBuffer(this.model.renderType(TEXTURE_LOCATION));
        this.model.renderToBuffer(poseStack, vertexconsumer, p_115867_, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.scale(1.5F, 1.5F, 1.5F);
        VertexConsumer vertexconsumer1 = multiBufferSource.getBuffer(RENDER_TYPE);
        this.model.renderToBuffer(poseStack, vertexconsumer1, p_115867_, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 0.15F);
        poseStack.popPose();
        super.render(entity, p_115863_, p_115864_, poseStack, multiBufferSource, p_115867_);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull T entity) {
        return TEXTURE_LOCATION;
    }
}