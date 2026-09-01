package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWispModel;
import com.chipoodle.devilrpg.entity.SoulWispForester;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class SoulWispForesterRenderer extends MobRenderer<SoulWispForester, SoulWispModel<SoulWispForester>> {
    //private static final ResourceLocation ALLAY_TEXTURE = new ResourceLocation("textures/entity/allay/allay.png");
    public static final ResourceLocation ALLAY_TEXTURE = ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "textures/entity/soulwisp/allay_green.png");

    public SoulWispForesterRenderer(EntityRendererProvider.Context p_234551_) {
        super(p_234551_, new SoulWispModel<>(p_234551_.bakeLayer(SoulWispModel.FORESTER_LAYER_LOCATION)), 0.4F);
        this.addLayer(new ItemInHandLayer<>(this, p_234551_.getItemInHandRenderer()));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull SoulWispForester p_234558_) {
        return ALLAY_TEXTURE;
    }

    protected int getBlockLightLevel(@NotNull SoulWispForester p_234560_, @NotNull BlockPos p_234561_) {
        return 1;
    }
}
