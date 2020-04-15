package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.client.render.entity.SoulWolfRenderer;
import com.chipoodle.devilrpg.client.render.entity.WispRenderer;

import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ModRenderer {
	
	public static void init() {
		//RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.WILD_BOAR.get(), WildBoarRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.SOUL_WOLF.get(), SoulWolfRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.WISP.get(), WispRenderer::new);
	}

}
