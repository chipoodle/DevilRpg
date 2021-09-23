/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

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
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
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
	public static WerewolfRenderer newWolf = null;
	private static Class<?>[] tipos = { double.class, double.class, double.class };
	private static Method method = null;

	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onEvent(RenderGameOverlayEvent.Pre event) {
		switch (event.getType()) {
		case HEALTH:
			healthBarRenderer.renderBar(event.getMatrixStack(), event.getWindow().getGuiScaledWidth(),
					event.getWindow().getGuiScaledHeight());
			manaBarRenderer.renderBar(event.getMatrixStack(), event.getWindow().getGuiScaledWidth(),
					event.getWindow().getGuiScaledHeight());
			minionPortraitRenderer.renderPortraits(event.getMatrixStack(), event.getWindow().getGuiScaledWidth(),
					event.getWindow().getGuiScaledHeight());
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

	/**
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
	public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
		// event.getPlayer().sendMessage(new StringTextComponent("------>
		// PlayerInteractEvent.LeftClickEmpty"));
		
		/*BiConsumer<PlayerInteractEvent.LeftClickEmpty, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			eve.getPlayer().isSwingInProgress = false;
		};
		EventUtils.onTransformation(event.getPlayer(), c, event);*/
		
	}

	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onMouseRawEvent(InputEvent.RawMouseEvent event) {

		Minecraft mc = Minecraft.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player != null) {
			LazyOptional<IBaseAuxiliarCapability> aux = player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
			if (aux == null || !aux.isPresent() || !aux.map(x -> x.isWerewolfTransformation()).orElse(true))
				return;

			player.swinging = false;
			if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
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

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onPlayerTickAttack(TickEvent.PlayerTickEvent event) {
		if (event.side.equals(LogicalSide.CLIENT)) {
			if (event.phase == TickEvent.Phase.START) {

				LazyOptional<IBaseSkillCapability> skillCapability = event.player
						.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
				LazyOptional<IBaseAuxiliarCapability> auxCapability = event.player
						.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
				if (skillCapability != null && auxCapability != null) {
					boolean werewolfTransformation = auxCapability.map(x -> x.isWerewolfTransformation()).orElse(false);
					boolean werewolfAttack = auxCapability.map(x -> x.isWerewolfAttack()).orElse(false);
					if (werewolfTransformation && werewolfAttack) {
						//DevilRpg.LOGGER.info("clientForgeEvent.onPlayerTickAttack");
						skillCapability
								.ifPresent(x -> ((SkillShapeshiftWerewolf) x.create(SkillEnum.TRANSFORM_WEREWOLF))
										.playerTickEventAttack(event.player, auxCapability));
					}

				}
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerTickMana(TickEvent.PlayerTickEvent event) {
		if (event.side.equals(LogicalSide.CLIENT)) {
			if (event.phase == TickEvent.Phase.START) {
				LazyOptional<IBaseManaCapability> manaCapability = event.player
						.getCapability(PlayerManaCapabilityProvider.MANA_CAP);
				// Mana
				manaCapability.ifPresent(m -> m.onPlayerTickEventRegeneration(event.player));

			}
		}
	}

	/*
	 * @SubscribeEvent public static void onDrawHighlightEvent(DrawHighlightEvent
	 * event) { ClientPlayerEntity player = Minecraft.getInstance().player;
	 * //player.isSwingInProgress = false; }
	 */

	/* public static Entity camera = null; */

	/**
	 * Cancels the default player's model rendering
	 * 
	 * @param event
	 */
	@SubscribeEvent
	public static void onPlayerRender(RenderPlayerEvent.Pre event) {
		BiConsumer<RenderPlayerEvent.Pre, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			eve.setCanceled(true);
			if (newWolf == null) {
				newWolf = new WerewolfRenderer(eve.getRenderer().getDispatcher());
			}
			newWolf.render(eve.getPlayer(), 0, eve.getPartialRenderTick(), eve.getMatrixStack(), eve.getBuffers(),
					eve.getLight());
		};
		if (!EventUtils.onWerewolfTransformation(event.getPlayer(), c, event)) {
			newWolf = null;
		}
	}

	@SubscribeEvent
	public static void onRenderHandEvent(RenderHandEvent event) {

	}

	/**
	 * 
	 * @param event
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	@SubscribeEvent
	public static void onCameraSetup(CameraSetup event) throws NoSuchMethodException, SecurityException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		
		PlayerEntity player = Minecraft.getInstance().player;
		if (!Minecraft.getInstance().options.getCameraType().equals(PointOfView.FIRST_PERSON)) {
			BiConsumer<CameraSetup, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			};
			if (EventUtils.onWerewolfTransformation(player, c, event)) {
				if (method == null) {
					method = ActiveRenderInfo.class.getDeclaredMethod("move", tipos);
					method.setAccessible(true);
				}
				method.invoke(event.getInfo(), 0.5D, 1.5D, 0.0D);
			}
		}
	}

	/**
	 * renders custom 3d person view camera
	 * 
	 * @param <T>
	 * 
	 * @param event
	 */
	@SubscribeEvent
	public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {

	}

}
