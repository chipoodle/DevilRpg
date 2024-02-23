package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.client.render.entity.model.SoulWispModel;
import com.chipoodle.devilrpg.entity.SoulWispHealth;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class SoulWispHealthRenderer extends MobRenderer<SoulWispHealth, SoulWispModel<SoulWispHealth>> {
    private static final ResourceLocation ALLAY_TEXTURE = new ResourceLocation("textures/entity/allay/allay.png");

    public SoulWispHealthRenderer(EntityRendererProvider.Context p_234551_) {
        super(p_234551_, new SoulWispModel<>(p_234551_.bakeLayer(SoulWispModel.HEALTH_LAYER_LOCATION)), 0.4F);
        this.addLayer(new ItemInHandLayer<>(this, p_234551_.getItemInHandRenderer()));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull SoulWispHealth p_234558_) {
        return ALLAY_TEXTURE;
    }

    protected int getBlockLightLevel(@NotNull SoulWispHealth p_234560_, @NotNull BlockPos p_234561_) {
        return 1;
    }

    @Override
    public void render(@NotNull SoulWispHealth soulWispHealth, float p_115456_, float p_115457_, @NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int p_115460_) {
        super.render(soulWispHealth, p_115456_, p_115457_, poseStack, multiBufferSource, p_115460_);
    }
}
