/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import org.lwjgl.glfw.GLFW;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.client.gui.hud.ManaBarRenderer;
import com.chipoodle.devilrpg.client.gui.hud.StatusBarRenderer;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillTransformWerewolf;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Subscribe to events from the FORGE EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Chipoodle
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEventSubscriber {

	//private static GuiManaBar manaBar = new GuiManaBar();
	private static StatusBarRenderer statusBarRenderer = new StatusBarRenderer();
	private static ManaBarRenderer manaBarRenderer = new ManaBarRenderer();

	/*@OnlyIn(Dist.CLIENT)
	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onRenderExperienceBar(RenderGameOverlayEvent.Post event) {
		//manaBar.draw(event);
	}*/

	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onEvent(RenderGameOverlayEvent.Pre event) {
		switch (event.getType()) {
		case HEALTH:
			statusBarRenderer.renderStatusBar(event.getWindow().getScaledWidth(),event.getWindow().getScaledHeight()); 
			manaBarRenderer.renderStatusBar(event.getWindow().getScaledWidth(),event.getWindow().getScaledHeight()); 
			event.setCanceled(true);
			break;

		case ARMOR:
			/*
			 * Don't render the vanilla armor bar, it's part of the status bar in the HEALTH
			 * event
			 */
			event.setCanceled(true);
			break;

		default: // If it's not one of the above cases, do nothing
			break;
		}
	}

	/*
	 * The RenderGameOverlayEvent.Post event is called after each game overlay
	 * element is rendered. Similar to the RenderGameOverlayEvent.Pre event, it is
	 * called multiple times.
	 *
	 * If you want something to be rendered over an existing vanilla element, you
	 * would render it here.
	 */
	@SubscribeEvent(receiveCanceled = true)
	public static void onEvent(RenderGameOverlayEvent.Post event) {
		switch (event.getType()) {
		case HEALTH:
			break;
		default: // If it's not one of the above cases, do nothing
			break;
		}
	}

	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onMouseEvent(InputEvent.MouseInputEvent event) {

		Minecraft mc = Minecraft.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null)
			return;

		LazyOptional<IBaseAuxiliarCapability> aux = player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
		if (aux == null || !aux.isPresent() || !aux.map(x -> x.isWerewolfTransformation()).orElse(true))
			return;

		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (event.getAction() == GLFW.GLFW_PRESS) {
				DevilRpg.LOGGER.info("pressed");
				aux.ifPresent(werwolf -> werwolf.setWerewolfAttack(true, player));
			}
		}

		if (event.getAction() == GLFW.GLFW_RELEASE) {
			DevilRpg.LOGGER.info("released");
			aux.ifPresent(werwolf -> werwolf.setWerewolfAttack(false, player));
		}

	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			LazyOptional<IBaseSkillCapability> skill = event.player
					.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
			if (skill != null) {
				skill.ifPresent(x -> ((SkillTransformWerewolf) x.create(SkillEnum.TRANSFORM_WEREWOLF))
						.playerTickEventAttack(event.player));

			}
		}
	}
}
