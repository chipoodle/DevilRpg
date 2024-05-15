package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.client.render.entity.layer.SunflowerShulkerHeadLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SunflowerShulkerModel;
import com.chipoodle.devilrpg.entity.SunflowerShulker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ShulkerHeadLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class SunflowerShulkerRenderer extends MobRenderer<SunflowerShulker, SunflowerShulkerModel<SunflowerShulker>> {
    private static final ResourceLocation DEFAULT_TEXTURE_LOCATION = new ResourceLocation("textures/" + Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION.texture().getPath() + ".png");
    private static final ResourceLocation[] TEXTURE_LOCATION = Sheets.SHULKER_TEXTURE_LOCATION.stream().map((p_115919_) -> {
        return new ResourceLocation("textures/" + p_115919_.texture().getPath() + ".png");
    }).toArray((p_115877_) -> {
        return new ResourceLocation[p_115877_];
    });

    public SunflowerShulkerRenderer(EntityRendererProvider.Context p_174370_) {
        super(p_174370_, new SunflowerShulkerModel<>(p_174370_.bakeLayer(SunflowerShulkerModel.DEFAULT_LAYER_LOCATION)), 0.0F);
        this.addLayer(new SunflowerShulkerHeadLayer(this));
    }

    public static ResourceLocation getTextureLocation(@Nullable DyeColor p_174376_) {
        return p_174376_ == null ? DEFAULT_TEXTURE_LOCATION : TEXTURE_LOCATION[p_174376_.getId()];
    }

    public @NotNull Vec3 getRenderOffset(SunflowerShulker p_115904_, float p_115905_) {
        return p_115904_.getRenderPosition(p_115905_).orElse(super.getRenderOffset(p_115904_, p_115905_));
    }

    public boolean shouldRender(@NotNull SunflowerShulker sunflowerShulker, @NotNull Frustum frustum, double p_115915_, double p_115916_, double p_115917_) {
        return super.shouldRender(sunflowerShulker, frustum, p_115915_, p_115916_, p_115917_) ? true : sunflowerShulker.getRenderPosition(0.0F).filter((p_174374_) -> {
            EntityType<?> entitytype = sunflowerShulker.getType();
            float f = entitytype.getHeight() / 2.0F;
            float f1 = entitytype.getWidth() / 2.0F;
            Vec3 vec3 = Vec3.atBottomCenterOf(sunflowerShulker.blockPosition());
            return frustum.isVisible((new AABB(p_174374_.x, p_174374_.y + (double) f, p_174374_.z, vec3.x, vec3.y + (double) f, vec3.z)).inflate((double) f1, (double) f, (double) f1));
        }).isPresent();
    }

    public @NotNull ResourceLocation getTextureLocation(SunflowerShulker p_115902_) {
        return getTextureLocation(p_115902_.getColor());
    }

    protected void setupRotations(@NotNull SunflowerShulker sunflowerShulker, @NotNull PoseStack poseStack, float p_115909_, float p_115910_, float p_115911_) {
        super.setupRotations(sunflowerShulker, poseStack, p_115909_, p_115910_ + 180.0F, p_115911_);
        poseStack.translate(0.0D, 0.5D, 0.0D);
        poseStack.mulPose(sunflowerShulker.getAttachFace().getOpposite().getRotation());
        poseStack.translate(0.0D, -0.5D, 0.0D);
    }
}