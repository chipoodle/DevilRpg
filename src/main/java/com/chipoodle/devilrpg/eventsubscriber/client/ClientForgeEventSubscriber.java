/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import java.util.List;
import java.util.stream.Collectors;

import org.lwjgl.glfw.GLFW;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.client.gui.manabar.GuiManaBar;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.WerewolfAttackServerHandler;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillTransformWerewolf;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.util.Hand;
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
 * @author Cadiboo
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEventSubscriber {

	private static GuiManaBar manaBar = new GuiManaBar();
	private static boolean startingHit = false;

	public ClientForgeEventSubscriber() {
		startingHit = false;
	}

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onRenderExperienceBar(RenderGameOverlayEvent.Post event) {
		manaBar.draw(event);
	}

	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onMouseEvent(InputEvent.MouseInputEvent event) {

		Minecraft mc = Minecraft.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null)
			return;

		LazyOptional<IBaseSkillCapability> skill = player.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		if (skill == null || !skill.isPresent())
			return;
		
		PlayerSkillCapability e = (PlayerSkillCapability)skill.map(x -> x).orElse(null);
		SkillTransformWerewolf tr = (SkillTransformWerewolf) e.getSingletonSkillFactory().getExistingSkill(SkillEnum.TRANSFORM_WEREWOLF);
		
		//tr.isActive()

		if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (event.getAction() == GLFW.GLFW_PRESS) {
				DevilRpg.LOGGER.info("pressed");
				startingHit = true;
			}
		}

		if (event.getAction() == GLFW.GLFW_RELEASE) {
			DevilRpg.LOGGER.info("released");
			startingHit = false;
		}

	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			event.player.isSwingInProgress = false;

			if (startingHit) {
				if (event.player != null && event.player.world.isRemote && event.player.ticksExisted % 10L == 0L) {
					int distance = 1;
					double radius = 2;
					if (event.player != null) {
						List<LivingEntity> targetList = TargetUtils
								.acquireAllLookTargets(event.player, distance, radius).stream()
								.filter(x -> !(x instanceof TameableEntity) || !x.isOnSameTeam(event.player))
								.collect(Collectors.toList());

						LivingEntity target = targetList.stream()
								.filter(x -> !x.equals(event.player.getLastAttackedEntity())).findAny()
								.orElseGet(() -> targetList.stream().findAny().orElse(null));

						if (target != null) {
							if (targetList != null && !targetList.isEmpty()) {
								// DevilRpg.LOGGER.info("------> hitting.");
								event.player.setLastAttackedEntity(target);
								Hand h = event.player.ticksExisted % 20L == 0L ? Hand.MAIN_HAND : Hand.OFF_HAND;
								event.player.swingArm(h);
								/*
								 * event.player.world.playSound((PlayerEntity) null, event.player.getPosX(),
								 * event.player.getPosY(), event.player.getPosZ(),
								 * SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, event.player.getSoundCategory(), 1.0F,
								 * 1.0F);
								 */
								ModNetwork.CHANNEL
										.sendToServer(new WerewolfAttackServerHandler(target.getEntityId(), h));
							}
						}
					}
				}
			}
		}
	}
}
