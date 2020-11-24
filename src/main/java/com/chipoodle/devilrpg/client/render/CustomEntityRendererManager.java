package com.chipoodle.devilrpg.client.render;

import net.minecraft.client.GameSettings;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CustomEntityRendererManager extends EntityRendererManager{

	public CustomEntityRendererManager(TextureManager textureManagerIn, ItemRenderer itemRendererIn,
			IReloadableResourceManager resourceManagerIn, FontRenderer fontRendererIn, GameSettings gameSettingsIn) {
		super(textureManagerIn, itemRendererIn, resourceManagerIn, fontRendererIn, gameSettingsIn);
		
	}

}
