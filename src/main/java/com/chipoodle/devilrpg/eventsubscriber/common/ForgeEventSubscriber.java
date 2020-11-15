/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import java.util.HashMap;
import java.util.UUID;

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
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerExperienceClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerSkillClientServerHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
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

		DevilRpg.LOGGER.info("----------------------->PlayerForgeEventSubscriber.attachCapability()");
		event.addCapability(MANA_CAP, new PlayerManaCapabilityProvider());
		event.addCapability(SKILL_CAP, new PlayerSkillCapabilityProvider());
		event.addCapability(EXP_CAP, new PlayerExperienceCapabilityProvider());
		event.addCapability(AUX_CAP, new PlayerAuxiliarCapabilityProvider());
		event.addCapability(MINION_CAP, new PlayerMinionCapabilityProvider());
		LOGGER.info("------------------------>Capabilities attached");

	}

	@SubscribeEvent
	public static void onPlayerLogsIn(PlayerEvent.PlayerLoggedInEvent event) {
		DevilRpg.LOGGER.info("----------------------->PlayerForgeEventSubscriber.onPlayerLogsIn()");
		PlayerEntity player = event.getPlayer();
		LazyOptional<IBaseManaCapability> mana = player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);

		String message1 = String.format("Hello there, you have mana %f left.",
				mana.map(x -> x.getMana()).orElse(Float.NaN));
		player.sendMessage(new StringTextComponent(message1),player.getUniqueID());
	}

	@SubscribeEvent
	public static void onPlayerClone(PlayerEvent.Clone e) {
		if (e.isWasDeath()) {
			DevilRpg.LOGGER.info("----------------------->PlayerForgeEventSubscriber.onPlayerClone()");
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

		DevilRpg.LOGGER.info("----------------------->PlayerForgeEventSubscriber.onPlayerRespawn()");
		PlayerEntity player = event.getPlayer();

		if (player.world.isRemote) {
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

		DevilRpg.LOGGER.info("----------------------->PlayerForgeEventSubscriber.onEntityJoinWorld()");
		final PlayerEntity player = (PlayerEntity) event.getEntity();
		LazyOptional<IBaseManaCapability> mana = player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);
		LazyOptional<IBaseSkillCapability> skill = player.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		LazyOptional<IBaseExperienceCapability> exp = player
				.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		LazyOptional<IBaseAuxiliarCapability> aux = player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
		LazyOptional<IBaseMinionCapability> min = player.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);

		Minecraft mainThread = Minecraft.getInstance();
		if (mana.isPresent()) {
			if (!player.world.isRemote) {
				mainThread.enqueue(new Runnable() {
					public void run() {
						ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
								new PlayerManaClientServerHandler(mana.map(x -> x.getNBTData()).orElse(null)));
					}
				});
			}

		}
		if (skill.isPresent()) {
			if (!player.world.isRemote) {
				mainThread.enqueue(new Runnable() {
					public void run() {
						skill.ifPresent(x->{
							HashMap<String, UUID> attributeModifiers = x.getAttributeModifiers();
							UUID hlthAttMod = attributeModifiers.get(Attributes.MAX_HEALTH.getAttributeName());
							UUID spdAttMod = attributeModifiers.get(Attributes.MOVEMENT_SPEED.getAttributeName());
							
							if(hlthAttMod != null) {
								DevilRpg.LOGGER.info("||-------------> removing health id: "+hlthAttMod);
								player.getAttribute(Attributes.MAX_HEALTH).removeModifier(hlthAttMod);
							}
							if(spdAttMod!= null) {
								player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(spdAttMod);
							}
							ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
									new PlayerSkillClientServerHandler(x.getNBTData()));
						});		
					}
				});
			}

		}
		if (exp.isPresent()) {
			if (!player.world.isRemote) {
				mainThread.enqueue(new Runnable() {
					public void run() {
						ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
								new PlayerExperienceClientServerHandler(exp.map(x -> x.getNBTData()).orElse(null)));
					}
				});
			}

		}

		if (aux.isPresent()) {
			if (!player.world.isRemote) {
				mainThread.enqueue(new Runnable() {
					public void run() {
						aux.ifPresent(x -> x.setWerewolfAttack(false, player));
						aux.ifPresent(x -> x.setWerewolfTransformation(false, player));
						//player.getAttribute(SharedMonsterAttributes.MAX_HEALTH).removeAllModifiers();
						//player.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).removeAllModifiers();
						// ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() ->
						// (ServerPlayerEntity) player), new PlayerAuxiliarClientServerHandler(aux.map(x
						// -> x.getNBTData()).orElse(null)));

					}
				});
			}
		}

		if (min.isPresent()) {
			if (!player.world.isRemote) {
				mainThread.enqueue(new Runnable() {
					public void run() {
						min.ifPresent(x -> {
							x.removeAllSoulWolf(player);
							x.removeAllSoulBear(player);
							x.removeAllWisp(player);
						});

						/*
						 * ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() ->
						 * (ServerPlayerEntity) player), new PlayerMinionClientServerHandler(min.map(x
						 * -> x.getNBTData()).orElse(null)));
						 */
					}
				});
			}

		}
	}

	@SubscribeEvent
	public static void onPlayerSleep(PlayerSleepInBedEvent event) {
		DevilRpg.LOGGER.info("----------------------->PlayerForgeEventSubscriber.onPlayerSleep()");
		PlayerEntity player = event.getPlayer();

		if (player.world.isRemote) {
			return;
		}

		LazyOptional<IBaseManaCapability> mana = player.getCapability(PlayerManaCapabilityProvider.MANA_CAP, null);
		mana.ifPresent((cap) -> cap.setMana(mana.map(x -> x.getMaxMana()).orElse(Float.NaN), player));

		String message = String.format(
				"You refreshed yourself in the bed. You recovered mana and you have %f mana left.",
				mana.map(x -> x.getMaxMana()).orElse(Float.NaN));
		player.sendMessage(new StringTextComponent(message),player.getUniqueID());
	}

	@SubscribeEvent
	public static void playerLevelChange(PlayerXpEvent.LevelChange e) {
		PlayerEntity player = e.getPlayer();
		DevilRpg.LOGGER.info("----------------------->PlayerForgeEventSubscriber.playerLevelChange()" + " Client? "
				+ player.world.isRemote + " level? " + player.experienceLevel + " " + player.experienceTotal);

		LazyOptional<IBaseExperienceCapability> exp = player
				.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		exp.ifPresent(x -> x.setCurrentLevel(player.experienceLevel + e.getLevels(), player));
	}

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public void onPlayerPickupXP(PlayerXpEvent.PickupXp e) {
		e.getOrb().xpValue *= 2;
	}

	@SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
	public static void onEvent(LivingJumpEvent event) {
		if (event.getEntity() instanceof PlayerEntity) {
			// DevilRpg.LOGGER.info("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%Boing");
		}
	}

	@SubscribeEvent
	public static void entityInteract(PlayerInteractEvent.EntityInteract event) {
		// event.getPlayer().sendMessage(new
		// StringTextComponent("------>PlayerInteractEvent.EntityInteract::
		// "+event.getTarget()));	
	}

	@SubscribeEvent
	public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		LazyOptional<IBaseAuxiliarCapability> aux = event.getPlayer()
				.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);

		boolean transformation = aux.map(x -> x.isWerewolfTransformation()).orElse(false);
		if (transformation) {
			event.getPlayer().isSwingInProgress = false;
			aux.ifPresent(x -> {
				Hand h = x.isSwingingMainHand() ? Hand.MAIN_HAND : Hand.OFF_HAND;
				event.getPlayer().swingArm(h);
				x.setSwingingMainHand(!x.isSwingingMainHand(), event.getPlayer());
			});
		}
	}

	@SubscribeEvent
	public static void entityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		LazyOptional<IBaseAuxiliarCapability> aux = event.getPlayer()
				.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);

		boolean transformation = aux.map(x -> x.isWerewolfTransformation()).orElse(false);
		if (transformation) {
			event.getPlayer().isSwingInProgress = false;
			aux.ifPresent(x -> {
				Hand h = x.isSwingingMainHand() ? Hand.MAIN_HAND : Hand.OFF_HAND;
				event.getPlayer().swingArm(h);
				x.setSwingingMainHand(!x.isSwingingMainHand(), event.getPlayer());
			});
		}
		//event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		LazyOptional<IBaseAuxiliarCapability> aux = event.getPlayer()
				.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);

		boolean transformation = aux.map(x -> x.isWerewolfTransformation()).orElse(false);
		// DevilRpg.LOGGER.info("------> PlayerInteractEvent.AttackEntityEvent.");
		if (transformation) {
			event.getPlayer().isSwingInProgress = false;
			event.setCanceled(true);
		}
	}
}
