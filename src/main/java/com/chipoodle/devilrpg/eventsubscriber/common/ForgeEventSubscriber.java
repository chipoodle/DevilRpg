/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import java.util.List;
import java.util.stream.Collectors;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.experience.IBaseExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityProvider;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerExperienceClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerSkillClientServerHandler;
import com.chipoodle.devilrpg.network.handler.WerewolfAttackServerHandler;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
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

	@SubscribeEvent
	public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
		if (!(event.getObject() instanceof PlayerEntity)) {
			return;
		}
		DevilRpg.LOGGER.info("----------------------->PlayerForgeEventSubscriber.attachCapability()");
		event.addCapability(MANA_CAP, new PlayerManaCapabilityProvider());
		event.addCapability(SKILL_CAP, new PlayerSkillCapabilityProvider());
		event.addCapability(EXP_CAP, new PlayerExperienceCapabilityProvider());
		LOGGER.info("------------------------>Capabilities attached");

	}

	@SubscribeEvent
	public static void onPlayerLogsIn(PlayerEvent.PlayerLoggedInEvent event) {
		DevilRpg.LOGGER.info("----------------------->PlayerForgeEventSubscriber.onPlayerLogsIn()");
		PlayerEntity player = event.getPlayer();
		LazyOptional<IBaseManaCapability> mana = player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);

		String message1 = String.format("Hello there, you have mana %f left.",
				mana.map(x -> x.getMana()).orElse(Float.NaN));
		player.sendMessage(new StringTextComponent(message1));

		LazyOptional<IBaseSkillCapability> skill = player.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		String message2 = String.format("You have skills.", skill);
		player.sendMessage(new StringTextComponent(message2));

		LazyOptional<IBaseExperienceCapability> exp = player
				.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		String message3 = String.format("You are lvl. %d ", exp.map(x -> x.getCurrentLevel()).orElse(-1));
		player.sendMessage(new StringTextComponent(message3));

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
	}

	@SubscribeEvent
	public static void onPlayerSleep(PlayerSleepInBedEvent event) {
		DevilRpg.LOGGER.info("----------------------->PlayerForgeEventSubscriber.onPlayerSleep()");
		PlayerEntity player = event.getPlayer();

		if (player.world.isRemote) {
			return;
		}

		LazyOptional<IBaseManaCapability> mana = player.getCapability(PlayerManaCapabilityProvider.MANA_CAP, null);
		mana.ifPresent((cap) -> cap.setMana(mana.map(x -> x.getMaxMana()).orElse(Float.NaN)));

		String message = String.format(
				"You refreshed yourself in the bed. You recovered mana and you have %f mana left.",
				mana.map(x -> x.getMaxMana()).orElse(Float.NaN));
		player.sendMessage(new StringTextComponent(message));
	}

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

		Minecraft mainThread = Minecraft.getInstance();
		if (!mana.isPresent()) {

		} else {
			if (!player.world.isRemote) {
				mainThread.enqueue(new Runnable() {
					public void run() {
						ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
								new PlayerManaClientServerHandler(mana.map(x -> x.getNBTData()).orElse(null)));
					}
				});
			}
		}
		if (!skill.isPresent()) {

		} else {
			if (!player.world.isRemote) {
				mainThread.enqueue(new Runnable() {
					public void run() {
						ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
								new PlayerSkillClientServerHandler(skill.map(x -> x.getNBTData()).orElse(null)));
					}
				});
			}
		}
		if (!exp.isPresent()) {

		} else {
			if (!player.world.isRemote) {
				mainThread.enqueue(new Runnable() {
					public void run() {
						ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
								new PlayerExperienceClientServerHandler(exp.map(x -> x.getNBTData()).orElse(null)));
					}
				});
			}

		}

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

	/*
	 * @SubscribeEvent public static void onPlayerTick(TickEvent.PlayerTickEvent
	 * event) { // DevilRpg.LOGGER.info(
	 * "----------------------->PlayerForgeEventSubscriber.PlayerTickEvent()" // +
	 * " Client? "+ event.player.world.isRemote); if (event.phase ==
	 * TickEvent.Phase.START) { if (event.player != null) { if
	 * (event.player.ticksExisted % 10L == 0L) { //
	 * TickEventHandler.setMultiHit(calculoHit.attack(event.player)); //
	 * DevilRpg.LOGGER.info("------> hitting."); } } } }
	 */

	@SubscribeEvent
	public static void interactLeftClick(PlayerInteractEvent.LeftClickBlock event) {
		// DevilRpg.LOGGER.info("------> PlayerInteractEvent.LeftClickBlock.");
		event.getPlayer().isSwingInProgress = false;
		// event.setCanceled(true);
	}

	@SubscribeEvent
	public static void entityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		// DevilRpg.LOGGER.info("------> PlayerInteractEvent.EntityInteractSpecific.");
		event.getPlayer().isSwingInProgress = false;
		// event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		// DevilRpg.LOGGER.info("------> PlayerInteractEvent.AttackEntityEvent.");
		event.getPlayer().isSwingInProgress = false;
		event.setCanceled(true);

		/*
		 * DevilRpg.LOGGER.info(
		 * "----------------------->PlayerForgeEventSubscriber.AttackEntityEvent()" +
		 * " Client? " + event.getPlayer().world.isRemote); if
		 * (!event.getEntityLiving().getHeldItemMainhand().isEmpty()) {
		 * event.setCanceled(true); if (event.getTarget().canBeAttackedWithItem()) { if
		 * (!event.getTarget().hitByEntity(event.getEntity())) {
		 * event.getPlayer().setLastAttackedEntity(event.getTarget());
		 * event.getPlayer().world.playSound((PlayerEntity) null,
		 * event.getPlayer().getPosX(), event.getPlayer().getPosY(),
		 * event.getPlayer().getPosZ(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK,
		 * event.getPlayer().getSoundCategory(), 1.0F, 1.0F);
		 * event.getTarget().attackEntityFrom(DamageSource.causePlayerDamage(event.
		 * getPlayer()), event.getPlayer().getCooledAttackStrength(1F));
		 * event.getPlayer().addExhaustion(0.1F);
		 * 
		 * } } }
		 */

	}
}
