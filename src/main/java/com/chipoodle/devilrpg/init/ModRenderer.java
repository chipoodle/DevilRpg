package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.client.render.entity.SoulBearRenderer;
import com.chipoodle.devilrpg.client.render.entity.SoulFireBallRenderer;
import com.chipoodle.devilrpg.client.render.entity.SoulWispArcherRenderer;
import com.chipoodle.devilrpg.client.render.entity.SoulWispBombRenderer;
import com.chipoodle.devilrpg.client.render.entity.SoulWispRenderer;
import com.chipoodle.devilrpg.client.render.entity.SoulWolfRenderer;

import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ModRenderer {
	
	public static void init() {
		
		
		
		RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.SOUL_WOLF.get(), SoulWolfRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.SOUL_BEAR.get(), SoulBearRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.SOUL_ICEBALL.get(), SoulFireBallRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.WISP.get(), SoulWispRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.WISP_BOMB.get(), SoulWispBombRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.WISP_ARCHER.get(), SoulWispArcherRenderer::new);
        //RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.WISP.get(), SoulWispHumanoidRenderer::new);
        
	}

}
