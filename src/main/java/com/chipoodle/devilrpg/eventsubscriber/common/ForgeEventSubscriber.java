/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityProvider;
import com.chipoodle.devilrpg.capability.experience.IBaseExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityProvider;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;
import com.chipoodle.devilrpg.capability.minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerExperienceClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerSkillClientServerHandler;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.network.PacketDistributor;

/**
 *
 * @author Dra. Magdalena
 */

@EventBusSubscriber(modid = DevilRpg.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEventSubscriber {

	public static final ResourceLocation MANA_CAP = new ResourceLocation(DevilRpg.MODID, "mana");
	public static final ResourceLocation SKILL_CAP = new ResourceLocation(DevilRpg.MODID, "skill");
	public static final ResourceLocation EXP_CAP = new ResourceLocation(DevilRpg.MODID, "experience");
	public static final ResourceLocation AUX_CAP = new ResourceLocation(DevilRpg.MODID, "auxiliar");
	public static final ResourceLocation MINION_CAP = new ResourceLocation(DevilRpg.MODID, "minion");

	@SubscribeEvent
	public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
		if (!(event.getObject() instanceof PlayerEntity)) {
			return;
		}

		DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.attachCapability()");
		event.addCapability(MANA_CAP, new PlayerManaCapabilityProvider());
		event.addCapability(SKILL_CAP, new PlayerSkillCapabilityProvider());
		event.addCapability(EXP_CAP, new PlayerExperienceCapabilityProvider());
		event.addCapability(AUX_CAP, new PlayerAuxiliarCapabilityProvider());
		event.addCapability(MINION_CAP, new PlayerMinionCapabilityProvider());
		LOGGER.info("------------------------>Capabilities attached");

	}

	@SubscribeEvent
	public static void onPlayerLogsIn(PlayerEvent.PlayerLoggedInEvent event) {
		DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onPlayerLogsIn()");
		PlayerEntity player = event.getPlayer();
		LazyOptional<IBaseManaCapability> mana = player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);

		String message1 = String.format("Mana desiponible: %f ",
				mana.map(x -> x.getMana()).orElse(Float.NaN));
		player.sendMessage(new StringTextComponent(message1), player.getUUID());
	}

	@SubscribeEvent
	public static void onPlayerClone(PlayerEvent.Clone e) {
		if (e.isWasDeath()) {
			DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onPlayerClone()");
			e.getOriginal().getCapability(PlayerManaCapabilityProvider.MANA_CAP).ifPresent(originalCap -> {
				e.getPlayer().getCapability(PlayerManaCapabilityProvider.MANA_CAP).ifPresent(actualCap -> {
					PlayerEntity originalPlayer = e.getOriginal();
					PlayerEntity actualPlayer = e.getPlayer();
					actualCap.setNBTData(originalCap.getNBTData());
				});
			});
			e.getOriginal().getCapability(PlayerSkillCapabilityProvider.SKILL_CAP).ifPresent(originalCap -> {
				e.getPlayer().getCapability(PlayerSkillCapabilityProvider.SKILL_CAP).ifPresent(actualCap -> {
					PlayerEntity originalPlayer = e.getOriginal();
					PlayerEntity actualPlayer = e.getPlayer();
					actualCap.setNBTData(originalCap.getNBTData());
				});
			});
			e.getOriginal().getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP).ifPresent(originalCap -> {
				e.getPlayer().getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP).ifPresent(actualCap -> {
					PlayerEntity originalPlayer = e.getOriginal();
					PlayerEntity actualPlayer = e.getPlayer();
					actualCap.setNBTData(originalCap.getNBTData());
				});
			});
			e.getOriginal().getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP).ifPresent(originalCap -> {
				e.getPlayer().getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP).ifPresent(actualCap -> {
					PlayerEntity originalPlayer = e.getOriginal();
					PlayerEntity actualPlayer = e.getPlayer();
					actualCap.setNBTData(originalCap.getNBTData());
				});
			});
			e.getOriginal().getCapability(PlayerMinionCapabilityProvider.MINION_CAP).ifPresent(originalCap -> {
				e.getPlayer().getCapability(PlayerMinionCapabilityProvider.MINION_CAP).ifPresent(actualCap -> {
					PlayerEntity originalPlayer = e.getOriginal();
					PlayerEntity actualPlayer = e.getPlayer();
					actualCap.setNBTData(originalCap.getNBTData());
				});
			});
		}

	}

	@SubscribeEvent
	public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {

		DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onPlayerRespawn()");
		PlayerEntity player = event.getPlayer();

		if (player.level.isClientSide) {
			return;
		}
		LazyOptional<IBaseManaCapability> mana = player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);
		LazyOptional<IBaseSkillCapability> skill = player.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		LazyOptional<IBaseExperienceCapability> exp = player
				.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		LazyOptional<IBaseAuxiliarCapability> aux = player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
		LazyOptional<IBaseMinionCapability> min = player.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);
	}

	/**
	 * Restore client player capabilities' values on join
	 * 
	 * @param event
	 */
	@SubscribeEvent
	public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
		if (!(event.getEntity() instanceof PlayerEntity))
			return;

		DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld()");
		final PlayerEntity player = (PlayerEntity) event.getEntity();
		LazyOptional<IBaseManaCapability> mana = player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);
		LazyOptional<IBaseSkillCapability> skill = player.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		LazyOptional<IBaseExperienceCapability> exp = player
				.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		LazyOptional<IBaseAuxiliarCapability> aux = player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
		LazyOptional<IBaseMinionCapability> min = player.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);

		Minecraft mainThread = Minecraft.getInstance();
		if (mana.isPresent()) {
			if (!player.level.isClientSide) {
				mainThread.tell(() -> {
					ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
							new PlayerManaClientServerHandler(mana.map(x -> x.getNBTData()).orElse(null)));

				});
			}

		}
		if (skill.isPresent()) {
			if (!player.level.isClientSide) {
				mainThread.tell(new Runnable() {
					public void run() {
						skill.ifPresent(presentSkill -> {
							HashMap<String, UUID> attributeModifiers = presentSkill.getAttributeModifiers();
							UUID hlthAttMod = attributeModifiers.get(Attributes.MAX_HEALTH.getDescriptionId());
							UUID spdAttMod = attributeModifiers.get(Attributes.MOVEMENT_SPEED.getDescriptionId());
							
							if (hlthAttMod != null) {
								DevilRpg.LOGGER.info("||-------------> removing health id: " + hlthAttMod);
								player.getAttribute(Attributes.MAX_HEALTH).removeModifier(hlthAttMod);
							}
							if (spdAttMod != null) {
								player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(spdAttMod);
							}
							ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
									new PlayerSkillClientServerHandler(presentSkill.getNBTData()));
						});
					}
				});
			}
		}

		if (exp.isPresent()) {
			if (!player.level.isClientSide) {
				mainThread.tell(new Runnable() {

					public void run() {
						ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
								new PlayerExperienceClientServerHandler(exp.map(x -> x.getNBTData()).orElse(null)));
					}
				});
			}
		}

		if (aux.isPresent()) {
			if (!player.level.isClientSide) {
				Minecraft.getInstance().tell(() -> {
					aux.ifPresent(x -> x.setWerewolfAttack(false, player));
					aux.ifPresent(x -> x.setWerewolfTransformation(false, player));
				});
			}
		}

		if (min.isPresent()) {
			if (!player.level.isClientSide) {
				Minecraft.getInstance().tell(() -> {
					min.ifPresent(x -> {
						x.removeAllSoulWolf(player);
						x.removeAllSoulBear(player);
						x.removeAllWisp(player);
					});

				});
			}

		}
	}

	@SubscribeEvent
	public static void onPlayerSleep(PlayerSleepInBedEvent event) {
		DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onPlayerSleep()");
		PlayerEntity player = event.getPlayer();

		if (player.level.isClientSide) {
			return;
		}

		LazyOptional<IBaseManaCapability> mana = player.getCapability(PlayerManaCapabilityProvider.MANA_CAP, null);
		mana.ifPresent((cap) -> cap.setMana(mana.map(x -> x.getMaxMana()).orElse(Float.NaN), player));

		String message = String.format(
				"You refreshed yourself in the bed. You recovered mana and you have %f mana left.",
				mana.map(x -> x.getMaxMana()).orElse(Float.NaN));
		player.sendMessage(new StringTextComponent(message), player.getUUID());
	}

	@SubscribeEvent
	public static void playerLevelChange(PlayerXpEvent.LevelChange e) {
		PlayerEntity player = e.getPlayer();
		DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.playerLevelChange()" + " Client? "
				+ player.level.isClientSide + " level? " + player.experienceLevel + " " + player.totalExperience);

		LazyOptional<IBaseExperienceCapability> exp = player
				.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		exp.ifPresent(x -> x.setCurrentLevel(player.experienceLevel + e.getLevels(), player));
	}

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public void onPlayerPickupXP(PlayerXpEvent.PickupXp e) {
		e.getOrb().value *= 0.5;
	}

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
			EventUtils.onTransformation((PlayerEntity) event.getEntity(), c, event);
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
			EventUtils.onTransformation((PlayerEntity) event.getEntity(), c, event);
		}
	}

	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		BiConsumer<PlayerInteractEvent.LeftClickBlock, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			eve.getPlayer().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onTransformation(event.getPlayer(), c, event);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void entityInteract(PlayerInteractEvent.EntityInteract event) {
		BiConsumer<PlayerInteractEvent.EntityInteract, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			eve.getPlayer().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onTransformation(event.getPlayer(), c, event);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void entityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		BiConsumer<PlayerInteractEvent.EntityInteractSpecific, LazyOptional<IBaseAuxiliarCapability>> c = (eve,
				auxiliar) -> {
			eve.getPlayer().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onTransformation(event.getPlayer(), c, event);
	}

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		BiConsumer<AttackEntityEvent, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			eve.getPlayer().swinging = false;
			eve.setCanceled(true);
		};
		EventUtils.onTransformation(event.getPlayer(), c, event);
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
}
