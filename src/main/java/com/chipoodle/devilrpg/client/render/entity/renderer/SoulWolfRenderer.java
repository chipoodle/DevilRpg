package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.EnergyShieldLayer;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulWolfGelLayer;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulWolfSkinLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModelHeart;
import com.chipoodle.devilrpg.entity.SoulWolf;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class SoulWolfRenderer extends MobRenderer<SoulWolf, SoulWolfModelHeart<SoulWolf>> {
    public static final ResourceLocation HEART_TEXTURES = ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "textures/entity/soul/soul_heart_white.png");

    public SoulWolfRenderer(EntityRendererProvider.Context entityRendererProviderContext) {
        super(entityRendererProviderContext, new SoulWolfModelHeart<>(entityRendererProviderContext.bakeLayer(SoulWolfModelHeart.LAYER_LOCATION)), 0.5F);
        this.addLayer(new SoulWolfGelLayer<>(this, entityRendererProviderContext.getModelSet()));
        this.addLayer(new EnergyShieldLayer<>(this, entityRendererProviderContext.getModelSet()));
        this.addLayer(new SoulWolfSkinLayer<>(this, entityRendererProviderContext.getModelSet()));
    }

    protected float getBob(SoulWolf livingBase, float partialTicks) {
        return livingBase.getTailAngle();
    }

    protected int getBlockLightLevel(@NotNull SoulWolf entityIn, @NotNull BlockPos pos) {
        return 1;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull SoulWolf entity) {
        return HEART_TEXTURES;
    }

    @Override
    public void render(SoulWolf p_116531_, float p_116532_, float p_116533_, @NotNull PoseStack p_116534_, @NotNull MultiBufferSource p_116535_, int p_116536_) {
        if (p_116531_.isWet()) {
            float f = p_116531_.getWetShade(p_116533_);
            int c = (int) (f * 255.0F);
            this.model.setColor(0xFF000000 | (c << 16) | (c << 8) | c);
        }

        super.render(p_116531_, p_116532_, p_116533_, p_116534_, p_116535_, p_116536_);
        if (p_116531_.isWet()) {
            this.model.setColor(0xFFFFFFFF);
        }
    }

    @Override
    protected void scale(@NotNull SoulWolf entityIn, @NotNull PoseStack matrixStackIn, float partialTickTime) {
        super.scale(entityIn, matrixStackIn, partialTickTime);
    }

}