/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.manabar.GuiManaBar;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Subscribe to events from the FORGE EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Cadiboo
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEventSubscriber {

	private static GuiManaBar manaBar = new GuiManaBar();

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onRenderExperienceBar(RenderGameOverlayEvent.Post event) {
		manaBar.draw(event);
	}

	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public void onEvent(LivingJumpEvent event) {
		if (event.getEntity() instanceof PlayerEntity) {
			DevilRpg.LOGGER.info("Boing");
		}
	}
}
