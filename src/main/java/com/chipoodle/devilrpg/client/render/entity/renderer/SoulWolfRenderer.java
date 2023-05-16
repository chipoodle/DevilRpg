package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulWolfGelLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModelHeart;
import com.chipoodle.devilrpg.entity.SoulWolf;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWolfRenderer extends MobRenderer<SoulWolf, SoulWolfModelHeart<SoulWolf>> {
    public static final ResourceLocation HEART_TEXTURES = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soul_heart_white.png");

    public SoulWolfRenderer(EntityRendererProvider.Context entityRendererProviderContext) {
        super(entityRendererProviderContext, new SoulWolfModelHeart<>(entityRendererProviderContext.bakeLayer(SoulWolfModelHeart.LAYER_LOCATION)), 0.5F);
        this.addLayer(new SoulWolfGelLayer<>(this, entityRendererProviderContext.getModelSet()));
    }

    protected float getBob(SoulWolf livingBase, float partialTicks) {
        return livingBase.getTailAngle();
    }

    protected int getBlockLightLevel(SoulWolf entityIn, BlockPos pos) {
        return 2;
    }

    @Override
    public ResourceLocation getTextureLocation(SoulWolf entity) {
        return HEART_TEXTURES;
    }

    @Override
    public void render(SoulWolf p_116531_, float p_116532_, float p_116533_, PoseStack p_116534_, MultiBufferSource p_116535_, int p_116536_) {
        if (p_116531_.isWet()) {
            float f = p_116531_.getWetShade(p_116533_);
            this.model.setColor(f, f, f);
        }

        super.render(p_116531_, p_116532_, p_116533_, p_116534_, p_116535_, p_116536_);
        if (p_116531_.isWet()) {
            this.model.setColor(1.0F, 1.0F, 1.0F);
        }
    }
    @Override
    protected void scale(SoulWolf entityIn, PoseStack matrixStackIn, float partialTickTime) {
        super.scale(entityIn, matrixStackIn, partialTickTime);
    }

}