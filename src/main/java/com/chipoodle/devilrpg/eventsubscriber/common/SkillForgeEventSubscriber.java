/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import java.util.function.BiConsumer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.entity.ISoulEntity;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PotionClientServerHandler;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.network.PacketDistributor;

/**
 *
 * @author Christian
 */

@EventBusSubscriber(modid = DevilRpg.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SkillForgeEventSubscriber {

	/**
	 * Increase jump height by 1 when Werewolf form
	 * 
	 * @param event
	 */
	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onLivingJumpEvent(LivingJumpEvent event) {
		if (event.getEntity() instanceof PlayerEntity) {
			BiConsumer<LivingJumpEvent, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
				Vector3d motion = eve.getEntity().getDeltaMovement();

				LazyOptional<IBaseSkillCapability> skillCap = event.getEntity()
						.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
				int points = skillCap.map(x -> x.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF)).get();
				double jumpFactor = (points * 0.005) + 0.03f; // max 0.13
				eve.getEntity().setDeltaMovement(motion.x(), motion.y() + jumpFactor, motion.z());
			};
			EventUtils.onWerewolfTransformation((PlayerEntity) event.getEntity(), c, event);
		}
	}

	/**
	 * Increase fall damage threshold by 1 block when in werewolf form
	 * 
	 * @param event
	 */
	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onLivingFallEvent(LivingFallEvent event) {
		if (event.getEntity() instanceof PlayerEntity) {
			BiConsumer<LivingFallEvent, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
				if (eve.getDistance() > 1) {
					eve.setDistance(eve.getDistance() - 1);
				}
			};
			EventUtils.onWerewolfTransformation((PlayerEntity) event.getEntity(), c, event);
		}
	}

	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		BiConsumer<PlayerInteractEvent.LeftClickBlock, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			eve.getPlayer().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onWerewolfTransformation(event.getPlayer(), c, event);
	}

	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		BiConsumer<PlayerInteractEvent.RightClickBlock, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			eve.getPlayer().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onWerewolfTransformation(event.getPlayer(), c, event);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void entityInteract(PlayerInteractEvent.EntityInteract event) {
		BiConsumer<PlayerInteractEvent.EntityInteract, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			eve.getPlayer().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onWerewolfTransformation(event.getPlayer(), c, event);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void entityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		BiConsumer<PlayerInteractEvent.EntityInteractSpecific, LazyOptional<IBaseAuxiliarCapability>> c = (eve,
				auxiliar) -> {
			eve.getPlayer().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onWerewolfTransformation(event.getPlayer(), c, event);
	}

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		BiConsumer<AttackEntityEvent, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			eve.getPlayer().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onWerewolfTransformation(event.getPlayer(), c, event);
	}

	/**
	 * Updates potion effects on client
	 * 
	 * @param event
	 */
	@SubscribeEvent
	public static void onLivingUpdateEvent(LivingUpdateEvent event) {
		/*
		 * Collection<EffectInstance> activePotionEffects =
		 * event.getEntityLiving().getActivePotionEffects();
		 * DevilRpg.LOGGER.info("---->Entity: "+event.getEntityLiving().getType() +
		 * "active potion effects: "+activePotionEffects);
		 */
	}

	@SubscribeEvent
	public static void onCriticalHitEvent(CriticalHitEvent event) {
		/*
		 * event.getPlayer() .sendMessage(new StringTextComponent( "Critical hit on " +
		 * event.getTarget().getName().getStringTruncated(10) + " by " +
		 * event.getPlayer().getName().getStringTruncated(10)),
		 * event.getPlayer().getUUID());
		 */

	}

	@SubscribeEvent
	public static void onPotionEvent(PotionEvent event) {

		if(!(event instanceof PotionEvent.PotionAddedEvent) && !(event instanceof PotionEvent.PotionExpiryEvent))
			return;
		
		if (event.getEntity() instanceof ISoulEntity && event.getEntity() instanceof ITamableEntity) {
			ITamableEntity minion = (ITamableEntity) event.getEntity();
			LivingEntity owner = minion.getOwner();
			if (owner != null && owner instanceof ServerPlayerEntity && !event.getEntity().level.isClientSide()) {
				EffectInstance potionEffect = event.getPotionEffect();
				CompoundNBT effectInstanceNbt = potionEffect.save(new CompoundNBT());
				effectInstanceNbt.putUUID(PotionClientServerHandler.MINION_ID_KEY, event.getEntity().getUUID());
				effectInstanceNbt.putString(PotionClientServerHandler.EFFECT_EVENT_TYPE, event.getClass().getSimpleName());
				ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> ((ServerPlayerEntity) owner)),
						new PotionClientServerHandler(effectInstanceNbt));
			}
		}

	}
	
}
