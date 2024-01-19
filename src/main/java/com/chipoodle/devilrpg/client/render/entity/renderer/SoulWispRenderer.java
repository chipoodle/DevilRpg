package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.client.render.entity.model.SoulWispModel;
import com.chipoodle.devilrpg.entity.SoulWisp;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWispRenderer extends MobRenderer<SoulWisp, SoulWispModel<SoulWisp>> {
    private static final ResourceLocation ALLAY_TEXTURE = new ResourceLocation("textures/entity/allay/allay.png");

    public SoulWispRenderer(EntityRendererProvider.Context p_234551_) {
        super(p_234551_, new SoulWispModel(p_234551_.bakeLayer(SoulWispModel.DEFAULT_LAYER_LOCATION)), 0.4F);
        this.addLayer(new ItemInHandLayer<>(this, p_234551_.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(SoulWisp p_234558_) {
        return ALLAY_TEXTURE;
    }

    protected int getBlockLightLevel(SoulWisp p_234560_, BlockPos p_234561_) {
        return 1;
    }

}
