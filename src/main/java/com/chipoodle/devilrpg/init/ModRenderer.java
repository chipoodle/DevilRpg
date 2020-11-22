package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.client.render.entity.SoulBearRenderer;
import com.chipoodle.devilrpg.client.render.entity.SoulFireBallRenderer;
import com.chipoodle.devilrpg.client.render.entity.SoulWispRenderer;
import com.chipoodle.devilrpg.client.render.entity.SoulWolfRenderer;
import com.chipoodle.devilrpg.client.render.entity.layer.WerewolfLayer;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ModRenderer {
	
	public static void init() {
		RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.SOUL_WOLF.get(), SoulWolfRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.SOUL_BEAR.get(), SoulBearRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.SOUL_FIREBALL.get(), SoulFireBallRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.WISP.get(), SoulWispRenderer::new);
        //RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.WISP.get(), SoulWispHumanoidRenderer::new);
        
	}

}
