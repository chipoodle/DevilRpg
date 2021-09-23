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
import com.chipoodle.devilrpg.capability.IGenericCapability;
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
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerExperienceClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerSkillClientServerHandler;
import com.chipoodle.devilrpg.skillsystem.skillinstance.MinionPassiveAttributes;
import com.chipoodle.devilrpg.util.EventUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
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
public class PlayerCapabilityForgeEventSubscriber {

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
		/*
		 * LazyOptional<IBaseManaCapability> mana =
		 * player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);
		 * 
		 * String message1 = String.format("Mana disponible: %f ", mana.map(x ->
		 * x.getMana()).orElse(Float.NaN)); player.sendMessage(new
		 * StringTextComponent(message1), player.getUUID());
		 */

		IBaseManaCapability manaCap = IGenericCapability.getUnwrappedCapability(player,
				PlayerManaCapabilityProvider.MANA_CAP);
		String message1 = String.format("Mana disponible: %f ", manaCap.getMana());
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
	 * Restore client player capabilities' values on join. Applies passive skills to entities
	 * 
	 * @param event
	 */
	@SubscribeEvent
	public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
		if ((event.getEntity() instanceof PlayerEntity)) {
			onPlayerJoinWorld(event);
		}

		if (event.getEntity() instanceof TameableEntity) {
			DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld() == TameableEntity:{} ", event.getEntity().getScoreboardName());
			new MinionPassiveAttributes((TameableEntity) event.getEntity());
		}

	}

	private static void onPlayerJoinWorld(EntityJoinWorldEvent event) {
		final PlayerEntity player = (PlayerEntity) event.getEntity();

		if (player.level.isClientSide)
			return;

		DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld()");
		BiConsumer<PlayerEntity, LazyOptional<IBaseManaCapability>> manaBiConsumer = (aPlayer, theMana) -> {
			ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) aPlayer),
					new PlayerManaClientServerHandler(theMana.map(IBaseManaCapability::getNBTData).orElse(null)));
			DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld().manaBiConsumer");
		};
		EventUtils.onJoin(player, manaBiConsumer, PlayerManaCapabilityProvider.MANA_CAP);

		BiConsumer<PlayerEntity, LazyOptional<IBaseSkillCapability>> skillBiConsumer = (aPlayer, theSkill) -> {
			theSkill.ifPresent(presentSkill -> {
				HashMap<String, UUID> attributeModifiers = presentSkill.getAttributeModifiers();
				UUID hlthAttMod = attributeModifiers.get(Attributes.MAX_HEALTH.getDescriptionId());
				UUID spdAttMod = attributeModifiers.get(Attributes.MOVEMENT_SPEED.getDescriptionId());

				if (hlthAttMod != null) {
					// DevilRpg.LOGGER.info("||-------------> removing health id: " + hlthAttMod);
					aPlayer.getAttribute(Attributes.MAX_HEALTH).removeModifier(hlthAttMod);
				}
				if (spdAttMod != null) {
					aPlayer.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(spdAttMod);
				}
				ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) aPlayer),
						new PlayerSkillClientServerHandler(presentSkill.getNBTData()));
			});
			DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld().skillBiConsumer");
		};
		EventUtils.onJoin(player, skillBiConsumer, PlayerSkillCapabilityProvider.SKILL_CAP);

		BiConsumer<PlayerEntity, LazyOptional<IBaseExperienceCapability>> expBiConsumer = (aPlayer, theExp) -> {
			ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) aPlayer),
					new PlayerExperienceClientServerHandler(theExp.map(x -> x.getNBTData()).orElse(null)));
			DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld().expBiConsumer");
		};
		EventUtils.onJoin(player, expBiConsumer, PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);

		BiConsumer<PlayerEntity, LazyOptional<IBaseAuxiliarCapability>> auxBiConsumer = (aPlayer, theAux) -> {
			theAux.ifPresent(x -> {
				x.setWerewolfAttack(false, aPlayer);
				x.setWerewolfTransformation(false, aPlayer);
			});
			DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld().auxBiConsumer");
		};
		EventUtils.onJoin(player, auxBiConsumer, PlayerAuxiliarCapabilityProvider.AUX_CAP);

		BiConsumer<PlayerEntity, LazyOptional<IBaseMinionCapability>> minBiConsumer = (aPlayer, theMin) -> {
			theMin.ifPresent(x -> {
				x.removeAllSoulWolf(player);
				x.removeAllSoulBear(player);
				x.removeAllWisp(player);
			});
			DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld().minBiConsumer");
		};
		EventUtils.onJoin(player, minBiConsumer, PlayerMinionCapabilityProvider.MINION_CAP);
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
		DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.playerLevelChange() Client? "
				+ player.level.isClientSide + " level? " + player.experienceLevel + " " + player.totalExperience);

		IBaseExperienceCapability expCap = IGenericCapability.getUnwrappedCapability(player,
				PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		expCap.setCurrentLevel(player.experienceLevel + e.getLevels(), player);

		/*
		 * LazyOptional<IBaseExperienceCapability> exp = player
		 * .getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		 * exp.ifPresent(x -> x.setCurrentLevel(player.experienceLevel + e.getLevels(),
		 * player));
		 */
	}

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public void onPlayerPickupXP(PlayerXpEvent.PickupXp e) {
		e.getOrb().value *= 0.5;
	}

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void playerSetSpawnEvent(PlayerSetSpawnEvent event) {
		DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.playerSetSpawnEvent()");
		if (event.getNewSpawn() != null) {
			DevilRpg.LOGGER.info(" SPAWN POINT FIRST {}", event.getNewSpawn());

		}
	}
}
