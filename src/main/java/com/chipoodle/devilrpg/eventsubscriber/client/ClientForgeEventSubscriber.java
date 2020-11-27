/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityProvider;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.client.gui.hud.HealthBarRenderer;
import com.chipoodle.devilrpg.client.gui.hud.ManaBarRenderer;
import com.chipoodle.devilrpg.client.gui.hud.MinionPortraitRenderer;
import com.chipoodle.devilrpg.client.render.entity.WerewolfRenderer;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillShapeshiftWerewolf;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Subscribe to events from the FORGE EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Chipoodle
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEventSubscriber {
	private static HealthBarRenderer healthBarRenderer = new HealthBarRenderer();
	private static ManaBarRenderer manaBarRenderer = new ManaBarRenderer();
	private static MinionPortraitRenderer minionPortraitRenderer = new MinionPortraitRenderer();

	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onEvent(RenderGameOverlayEvent.Pre event) {
		switch (event.getType()) {
		case HEALTH:
			healthBarRenderer.renderBar(event.getMatrixStack(), event.getWindow().getScaledWidth(),
					event.getWindow().getScaledHeight());
			manaBarRenderer.renderBar(event.getMatrixStack(), event.getWindow().getScaledWidth(),
					event.getWindow().getScaledHeight());
			minionPortraitRenderer.renderPortraits(event.getMatrixStack(), event.getWindow().getScaledWidth(),
					event.getWindow().getScaledHeight());
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

	/**
	 * Non cancellable event
	 * 
	 * @param event
	 */
	@SubscribeEvent
	public static void leftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
		// event.getPlayer().sendMessage(new StringTextComponent("------>
		// PlayerInteractEvent.LeftClickEmpty"));
	}

	/*
	 * @OnlyIn(Dist.CLIENT)
	 * 
	 * @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	 * public static void onMouseEvent(InputEvent.MouseInputEvent event) {
	 * 
	 * Minecraft mc = Minecraft.getInstance(); ClientPlayerEntity player =
	 * mc.player; if (player == null) return;
	 * 
	 * LazyOptional<IBaseAuxiliarCapability> aux =
	 * player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP); if (aux ==
	 * null || !aux.isPresent() || !aux.map(x ->
	 * x.isWerewolfTransformation()).orElse(true)) return; else
	 * player.isSwingInProgress = false;
	 * 
	 * if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) { if (event.getAction()
	 * == GLFW.GLFW_PRESS) { DevilRpg.LOGGER.info("pressed"); aux.ifPresent(werwolf
	 * -> werwolf.setWerewolfAttack(true, player)); } }
	 * 
	 * if (event.getAction() == GLFW.GLFW_RELEASE) {
	 * DevilRpg.LOGGER.info("released"); aux.ifPresent(werwolf ->
	 * werwolf.setWerewolfAttack(false, player)); }
	 * 
	 * }
	 */

	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onMouseRawEvent(InputEvent.RawMouseEvent event) {

		Minecraft mc = Minecraft.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player != null) {
			LazyOptional<IBaseAuxiliarCapability> aux = player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
			if (aux == null || !aux.isPresent() || !aux.map(x -> x.isWerewolfTransformation()).orElse(true))
				return;

			player.isSwingInProgress = false;
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
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.side.equals(LogicalSide.CLIENT)) {
			if (event.phase == TickEvent.Phase.START) {
				LazyOptional<IBaseSkillCapability> skill = event.player
						.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
				LazyOptional<IBaseAuxiliarCapability> aux = event.player
						.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
				LazyOptional<IBaseManaCapability> mana = event.player
						.getCapability(PlayerManaCapabilityProvider.MANA_CAP);
				if (skill != null && aux != null) {
					boolean werewolfTransformation = aux.map(x -> x.isWerewolfTransformation()).orElse(false);
					boolean werewolfAttack = aux.map(x -> x.isWerewolfAttack()).orElse(false);
					if (werewolfTransformation && werewolfAttack) {
						skill.ifPresent(x -> ((SkillShapeshiftWerewolf) x.create(SkillEnum.TRANSFORM_WEREWOLF))
								.playerTickEventAttack(event.player, aux));
					}
				}
				// Mana
				mana.ifPresent(m -> m.onPlayerTickEventRegeneration(event.player));

			}
		}
	}

	@OnlyIn(Dist.CLIENT)
	public static WerewolfRenderer newWolf = null;

	/**
	 * Cancels the default player's model rendering
	 * 
	 * @param event
	 */
	@SubscribeEvent
	public static void onPlayerRender(RenderPlayerEvent.Pre event) {
		Consumer<RenderPlayerEvent.Pre> c = eve -> {
			eve.setCanceled(true);
			if (newWolf == null) {
				newWolf = new WerewolfRenderer(eve.getRenderer().getRenderManager());
			}
			newWolf.render(eve.getPlayer(), 0, eve.getPartialRenderTick(), eve.getMatrixStack(), eve.getBuffers(),
					eve.getLight());
		};
		EventUtils.onTransformation(event.getPlayer(), c, event);
	}

	@SubscribeEvent
	public static void onRenderHandEvent(RenderHandEvent event) {

	}
}
