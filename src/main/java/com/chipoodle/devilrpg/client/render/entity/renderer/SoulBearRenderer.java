package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulBearArmorLayer;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulBearGelLayer;
import com.chipoodle.devilrpg.client.render.entity.layer.SoulBearSkinLayer;
import com.chipoodle.devilrpg.client.render.entity.model.SoulBearModelHeart;
import com.chipoodle.devilrpg.entity.SoulBear;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class SoulBearRenderer extends MobRenderer<SoulBear, SoulBearModelHeart<SoulBear>> {
    //private static final ResourceLocation POLAR_BEAR_TEXTURE = new ResourceLocation("textures/entity/bear/polarbear.png");
    public static final ResourceLocation POLAR_BEAR_TEXTURE = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soul_heart_white.png");

    public SoulBearRenderer(EntityRendererProvider.Context entityRendererProviderContext) {
        super(entityRendererProviderContext, new SoulBearModelHeart<>(entityRendererProviderContext.bakeLayer(SoulBearModelHeart.LAYER_LOCATION)), 0.9F);
        this.addLayer(new SoulBearGelLayer<>(this, entityRendererProviderContext.getModelSet()));
        this.addLayer(new SoulBearSkinLayer<>(this, entityRendererProviderContext.getModelSet()));
        this.addLayer(new SoulBearArmorLayer<>(this, entityRendererProviderContext.getModelSet()));

    }

    protected int getBlockLightLevel(@NotNull SoulBear entityIn, @NotNull BlockPos pos) {
        return 1;
    }

    public @NotNull ResourceLocation getTextureLocation(@NotNull SoulBear entity) {
        return POLAR_BEAR_TEXTURE;
    }

    protected void scale(@NotNull SoulBear entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(1.2F, 1.2F, 1.2F);
        super.scale(entitylivingbaseIn, matrixStackIn, partialTickTime);
    }
}
