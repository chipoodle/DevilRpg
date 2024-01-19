package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulBearGelLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SoulBearModelHeart;
import com.chipoodle.devilrpg.entity.SoulBear;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulBearRenderer extends MobRenderer<SoulBear, SoulBearModelHeart<SoulBear>> {
    //private static final ResourceLocation POLAR_BEAR_TEXTURE = new ResourceLocation("textures/entity/bear/polarbear.png");
    public static final ResourceLocation POLAR_BEAR_TEXTURE = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soul_heart_white.png");

    public SoulBearRenderer(EntityRendererProvider.Context entityRendererProviderContext) {
        super(entityRendererProviderContext, new SoulBearModelHeart<>(entityRendererProviderContext.bakeLayer(SoulBearModelHeart.LAYER_LOCATION)), 0.9F);
        this.addLayer(new SoulBearGelLayer<>(this, entityRendererProviderContext.getModelSet()));
    }

    protected int getBlockLightLevel(SoulBear entityIn, BlockPos pos) {
        return 1;
    }

    public ResourceLocation getTextureLocation(SoulBear entity) {
        return POLAR_BEAR_TEXTURE;
    }

    protected void scale(SoulBear entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(1.2F, 1.2F, 1.2F);
        super.scale(entitylivingbaseIn, matrixStackIn, partialTickTime);
    }
}
