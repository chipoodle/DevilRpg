package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.client.render.entity.model.SoulWispModel;
import com.chipoodle.devilrpg.entity.SoulWispArcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoulWispArcherRenderer extends MobRenderer<SoulWispArcher, SoulWispModel<SoulWispArcher>>  {
	private static final ResourceLocation ALLAY_TEXTURE = new ResourceLocation("textures/entity/allay/allay.png");

	public SoulWispArcherRenderer(EntityRendererProvider.Context p_234551_) {
		super(p_234551_, new SoulWispModel(p_234551_.bakeLayer(SoulWispModel.ARCHER_LAYER_LOCATION)), 0.4F);
		this.addLayer(new ItemInHandLayer<>(this, p_234551_.getItemInHandRenderer()));
	}

	@Override
	public ResourceLocation getTextureLocation(SoulWispArcher p_234558_) {
		return ALLAY_TEXTURE;
	}

	protected int getBlockLightLevel(SoulWispArcher p_234560_, BlockPos p_234561_) {
		return 15;
	}
}
