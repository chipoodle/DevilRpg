/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import java.util.function.BiConsumer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityAttacher;
import com.chipoodle.devilrpg.entity.ISoulEntity;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PotionClientServerHandler;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;

import net.minecraftforge.event.entity.living.LivingFallEvent;

import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;


/**
 *
 * @author Christian
 */

@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE)
public class SkillForgeEventSubscriber {

	/**
	 * Increase jump height by 1 when Werewolf form
	 *
	 */
	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onLivingJumpEvent(LivingJumpEvent event) {
		if (event.getEntity() instanceof Player) {
			BiConsumer<LivingJumpEvent, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
				Vec3 motion = eve.getEntity().getDeltaMovement();

				LazyOptional<PlayerSkillCapabilityInterface> skillCap = event.getEntity()
						.getCapability(PlayerSkillCapability.INSTANCE);
				int points = skillCap.map(x -> x.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF)).get();
				double jumpFactor = (points * 0.005) + 0.03f; // max 0.13
				eve.getEntity().setDeltaMovement(motion.x(), motion.y() + jumpFactor, motion.z());
			};
			EventUtils.onWerewolfTransformation((Player) event.getEntity(), c, event);
		}
	}

	/**
	 * Increase fall damage threshold by 1 block when in werewolf form
	 *
	 */
	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onLivingFallEvent(LivingFallEvent event) {
		if (event.getEntity() instanceof Player) {
			BiConsumer<LivingFallEvent, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
				if (eve.getDistance() > 1) {
					eve.setDistance(eve.getDistance() - 1);
				}
			};
			EventUtils.onWerewolfTransformation((Player) event.getEntity(), c, event);
		}
	}

	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		BiConsumer<PlayerInteractEvent.LeftClickBlock, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
			eve.getEntity().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
	}

	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		BiConsumer<PlayerInteractEvent.RightClickBlock, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
			eve.getEntity().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		BiConsumer<PlayerInteractEvent.EntityInteract, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
			eve.getEntity().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		BiConsumer<PlayerInteractEvent.EntityInteractSpecific, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
			eve.getEntity().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
	}

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		BiConsumer<AttackEntityEvent, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
			eve.getEntity().swinging = false;
			//eve.setCanceled(true);
		};
		EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
	}


	/*@SubscribeEvent
	public static void onLivingUpdateEvent(LivingUpdateEvent event) {

		 * Collection<EffectInstance> activePotionEffects =
		 * event.getEntityLiving().getActivePotionEffects();
		 * DevilRpg.LOGGER.info("---->Entity: "+event.getEntityLiving().getType() +
		 * "active potion effects: "+activePotionEffects);

	}*/

	@SubscribeEvent
	public static void onCriticalHitEvent(CriticalHitEvent event) {
		/*
		 * event.getPlayer() .sendMessage(new StringTextComponent( "Critical hit on " +
		 * event.getTarget().getName().getStringTruncated(10) + " by " +
		 * event.getPlayer().getName().getStringTruncated(10)),
		 * event.getPlayer().getUUID());
		 */

	}

	/**
	 * Updates potion effects on client
	 *
	 */
	@SubscribeEvent
	public static void onPotionEvent(MobEffectEvent event) {

		if(!(event instanceof MobEffectEvent.Added) && !(event instanceof MobEffectEvent.Expired))
			return;
		
		if (event.getEntity() instanceof ISoulEntity && event.getEntity() instanceof ITamableEntity && event.getEffectInstance() != null) {
			ITamableEntity minion = (ITamableEntity) event.getEntity();
			LivingEntity owner = minion.getOwner();
			if (owner instanceof ServerPlayer && !event.getEntity().level.isClientSide()) {
				MobEffectInstance potionEffect = event.getEffectInstance();
				CompoundTag effectInstanceNbt = potionEffect.save(new CompoundTag());
				effectInstanceNbt.putUUID(PotionClientServerHandler.MINION_ID_KEY, event.getEntity().getUUID());
				effectInstanceNbt.putString(PotionClientServerHandler.EFFECT_EVENT_TYPE, event.getClass().getSimpleName());
				ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> ((ServerPlayer) owner)),
						new PotionClientServerHandler(effectInstanceNbt));
			}
		}

	}
	
}
