/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityAttacher;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityAttacher;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityAttacher;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityAttacher;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityAttacher;
import com.chipoodle.devilrpg.entity.ITameableEntity;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerExperienceClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerSkillClientServerHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;


import java.util.HashMap;
import java.util.UUID;
import java.util.function.BiConsumer;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

/**
 * @author Christian
 */

@EventBusSubscriber(modid = DevilRpg.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerCapabilityForgeEventSubscriber {

    public static final ResourceLocation MANA_CAP = new ResourceLocation(DevilRpg.MODID, "mana");
    public static final ResourceLocation SKILL_CAP = new ResourceLocation(DevilRpg.MODID, "skill");
    public static final ResourceLocation EXP_CAP = new ResourceLocation(DevilRpg.MODID, "experience");

    public static final ResourceLocation MINION_CAP = new ResourceLocation(DevilRpg.MODID, "minion");
    public static final ResourceLocation TAMABLE_MINION_CAP = new ResourceLocation(DevilRpg.MODID, "tamable_minion");


    @SubscribeEvent
    public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if ((event.getObject() instanceof PlayerEntity)) {
            DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.attachCapability()");
            event.addCapability(MANA_CAP, new PlayerManaCapabilityAttacher());
            event.addCapability(SKILL_CAP, new PlayerSkillCapabilityAttacher());
            event.addCapability(EXP_CAP, new PlayerExperienceCapabilityAttacher());
            event.addCapability(AUX_CAP, new PlayerAuxiliaryCapability());
            event.addCapability(MINION_CAP, new PlayerMinionCapabilityAttacher());
            event.addCapability(TAMABLE_MINION_CAP, new TamableMinionCapabilityAttacher());
            LOGGER.info("------------------------>Capabilities attached");
        }

        if ((event.getObject() instanceof ITameableEntity)) {
            event.addCapability(TAMABLE_MINION_CAP, new TamableMinionCapabilityAttacher());
            LOGGER.info("------------------------>Capabilities attached");
        }
    }

    @SubscribeEvent
    public static void onPlayerLogsIn(PlayerEvent.PlayerLoggedInEvent event) {
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerLogsIn()");
        PlayerEntity player = event.getPlayer();
        /*
         * LazyOptional<IBaseManaCapability> mana =
         * player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);
         *
         * String message1 = String.format("Mana disponible: %f ", mana.map(x ->
         * x.getMana()).orElse(Float.NaN)); player.sendMessage(new
         * StringTextComponent(message1), player.getUUID());
         */

        PlayerManaCapabilityInterface manaCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                PlayerManaCapabilityAttacher.MANA_CAP);
        String message1 = String.format("Mana disponible: %f ", manaCap.getMana());
        player.sendMessage(new StringTextComponent(message1), player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone e) {
        if (e.isWasDeath()) {

            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerClone()");
            clonePlayerCapability(e, PlayerManaCapabilityAttacher.MANA_CAP);
            clonePlayerCapability(e, PlayerSkillCapabilityAttacher.SKILL_CAP);
            clonePlayerCapability(e, PlayerExperienceCapabilityAttacher.EXPERIENCE_CAP);
            clonePlayerCapability(e, PlayerAuxiliaryCapability.AUX_CAP);
            clonePlayerCapability(e, PlayerMinionCapabilityAttacher.MINION_CAP);
        }

    }

    private static void clonePlayerCapability(PlayerEvent.Clone e, Capability<? extends IGenericCapability> cap) {
        e.getOriginal().getCapability(cap).ifPresent(originalCap -> {
            e.getPlayer().getCapability(cap).ifPresent(actualCap -> {
                PlayerEntity originalPlayer = e.getOriginal();
                PlayerEntity actualPlayer = e.getPlayer();
                actualCap.setNBTData(originalCap.getNBTData());
            });
        });
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {

        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerRespawn()");
        PlayerEntity player = event.getPlayer();

        if (player.level.isClientSide) {
            return;
        }
        LazyOptional<PlayerManaCapabilityInterface> mana = player.getCapability(PlayerManaCapabilityAttacher.MANA_CAP);
        LazyOptional<PlayerSkillCapabilityInterface> skill = player.getCapability(PlayerSkillCapabilityAttacher.SKILL_CAP);
        LazyOptional<PlayerExperienceCapabilityInterface> exp = player
                .getCapability(PlayerExperienceCapabilityAttacher.EXPERIENCE_CAP);
        LazyOptional<PlayerAuxiliaryCapabilityInterface> aux = player.getCapability(PlayerAuxiliaryCapability.AUX_CAP);
        LazyOptional<PlayerMinionCapabilityInterface> min = player.getCapability(PlayerMinionCapabilityAttacher.MINION_CAP);
    }

    /**
     * Restore client player capabilities' values on join. Applies passive skills to entities
     *
     * @param event
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof ITameableEntity) {
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onEntityJoinWorld() == ITameable:{} ", event.getEntity().getScoreboardName());
            //new MinionPassiveAttributes((ITameableEntity) event.getEntity());
            TamableMinionCapabilityInterface minionPassiveCap
                    = IGenericCapability.getUnwrappedMinionCapability((ITameableEntity) event.getEntity(),
                    TamableMinionCapabilityAttacher.TAMABLE_MINION_CAP);
            minionPassiveCap.applyPassives((ITameableEntity) event.getEntity());
        }

    }

    /**
     * Restore client player capabilities' values on join. Applies passive skills to player
     *
     * @param event
     */
    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinWorldEvent event) {
        if (!(event.getEntity() instanceof PlayerEntity)) {
            return;
        }
        final PlayerEntity player = (PlayerEntity) event.getEntity();


        if (player.level.isClientSide)
            return;
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onEntityJoinWorld()");
        BiConsumer<PlayerEntity, LazyOptional<PlayerManaCapabilityInterface>> manaBiConsumer = sendManaNBTData();
        EventUtils.onJoin(player, manaBiConsumer, PlayerManaCapabilityAttacher.MANA_CAP);

        BiConsumer<PlayerEntity, LazyOptional<PlayerSkillCapabilityInterface>> skillBiConsumer = removeStoredSkillAttributes();
        EventUtils.onJoin(player, skillBiConsumer, PlayerSkillCapabilityAttacher.SKILL_CAP);

        BiConsumer<PlayerEntity, LazyOptional<PlayerExperienceCapabilityInterface>> expBiConsumer = sendExperienceNBTData();
        EventUtils.onJoin(player, expBiConsumer, PlayerExperienceCapabilityAttacher.EXPERIENCE_CAP);

        BiConsumer<PlayerEntity, LazyOptional<PlayerAuxiliaryCapabilityInterface>> auxBiConsumer = shapeshiftToNormal();
        EventUtils.onJoin(player, auxBiConsumer, PlayerAuxiliaryCapability.AUX_CAP);

        BiConsumer<PlayerEntity, LazyOptional<PlayerMinionCapabilityInterface>> minBiConsumer = removeMinions(player);
        EventUtils.onJoin(player, minBiConsumer, PlayerMinionCapabilityAttacher.MINION_CAP);
    }

    private static BiConsumer<PlayerEntity, LazyOptional<PlayerMinionCapabilityInterface>> removeMinions(PlayerEntity player) {
        BiConsumer<PlayerEntity, LazyOptional<PlayerMinionCapabilityInterface>> minBiConsumer = (aPlayer, theMin) -> {
            theMin.ifPresent(x -> {
                x.removeAllSoulWolf(player);
                x.removeAllSoulBear(player);
                x.removeAllWisp(player);
            });
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.removeMinions");
        };
        return minBiConsumer;
    }

    private static BiConsumer<Player, LazyOptional<PlayerAuxiliaryCapabilityInterface>> shapeshiftToNormal() {
        BiConsumer<Player, LazyOptional<PlayerAuxiliaryCapabilityInterface>> auxBiConsumer = (aPlayer, theAux) -> {
            theAux.ifPresent(x -> {
                x.setWerewolfAttack(false, aPlayer);
                x.setWerewolfTransformation(false, aPlayer);
            });
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.shapeshiftToNormal");
        };
        return auxBiConsumer;
    }

    private static BiConsumer<PlayerEntity, LazyOptional<PlayerExperienceCapabilityInterface>> sendExperienceNBTData() {
        BiConsumer<PlayerEntity, LazyOptional<PlayerExperienceCapabilityInterface>> expBiConsumer = (aPlayer, theExp) -> {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) aPlayer),
                    new PlayerExperienceClientServerHandler(theExp.map(x -> x.getNBTData()).orElse(null)));
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.sendExperienceNBTData");
        };
        return expBiConsumer;
    }

    private static BiConsumer<PlayerEntity, LazyOptional<PlayerManaCapabilityInterface>> sendManaNBTData() {
        BiConsumer<PlayerEntity, LazyOptional<PlayerManaCapabilityInterface>> manaBiConsumer = (aPlayer, theMana) -> {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) aPlayer),
                    new PlayerManaClientServerHandler(theMana.map(PlayerManaCapabilityInterface::getNBTData).orElse(null)));
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.sendManaNBTData");
        };
        return manaBiConsumer;
    }

    private static BiConsumer<PlayerEntity, LazyOptional<PlayerSkillCapabilityInterface>> removeStoredSkillAttributes() {
        BiConsumer<PlayerEntity, LazyOptional<PlayerSkillCapabilityInterface>> skillBiConsumer = (aPlayer, theSkill) -> {
            theSkill.ifPresent(presentSkill -> {
                HashMap<String, UUID> attributeModifiers = presentSkill.getAttributeModifiers();
                UUID hlthAttMod = attributeModifiers.get(Attributes.MAX_HEALTH.getDescriptionId());
                UUID spdAttMod = attributeModifiers.get(Attributes.MOVEMENT_SPEED.getDescriptionId());
                UUID armrAttMod = attributeModifiers.get(Attributes.ARMOR.getDescriptionId());
                UUID attDmgMod = attributeModifiers.get(Attributes.ATTACK_DAMAGE.getDescriptionId());

                if (hlthAttMod != null) {
                    aPlayer.getAttribute(Attributes.MAX_HEALTH).removeModifier(hlthAttMod);
                }
                if (spdAttMod != null) {
                    aPlayer.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(spdAttMod);
                }
                if (armrAttMod != null) {
                    aPlayer.getAttribute(Attributes.ARMOR).removeModifier(armrAttMod);
                }
                if (attDmgMod != null) {
                    aPlayer.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(attDmgMod);
                }


                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) aPlayer),
                        new PlayerSkillClientServerHandler(presentSkill.getNBTData()));
            });
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.removeStoredSkillAttributes");
        };
        return skillBiConsumer;
    }

    @SubscribeEvent
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerSleep()");
        PlayerEntity player = event.getPlayer();

        if (player.level.isClientSide) {
            return;
        }

        LazyOptional<PlayerManaCapabilityInterface> mana = player.getCapability(PlayerManaCapabilityAttacher.MANA_CAP, null);
        mana.ifPresent((cap) -> cap.setMana(mana.map(x -> x.getMaxMana()).orElse(Float.NaN), player));

        String message = String.format(
                "You refreshed yourself in the bed. You recovered mana and you have %f mana left.",
                mana.map(x -> x.getMaxMana()).orElse(Float.NaN));
        player.sendMessage(new StringTextComponent(message), player.getUUID());
    }

    @SubscribeEvent
    public static void onplayerLevelChange(PlayerXpEvent.LevelChange e) {
        PlayerEntity player = e.getPlayer();
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerLevelChange() Client? "
                + player.level.isClientSide + " level? " + player.experienceLevel + " " + player.totalExperience);

        PlayerExperienceCapabilityInterface expCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                PlayerExperienceCapabilityAttacher.EXPERIENCE_CAP);
        expCap.setCurrentLevel(player.experienceLevel + e.getLevels(), player);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerSetSpawnEvent(PlayerSetSpawnEvent event) {
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerSetSpawnEvent()");
        if (event.getNewSpawn() != null) {
            DevilRpg.LOGGER.info("||||||||||||||||||||||||||| SPAWN POINT FIRST {}", event.getNewSpawn());

        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingEquipmentChangeEvent(LivingEquipmentChangeEvent event) {

        if ((event.getEntity() instanceof PlayerEntity) && event.getSlot().getType().compareTo(EquipmentSlotType.Group.ARMOR) == 0) {
            PlayerEntity player = (PlayerEntity) event.getEntity();
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onLivingEquipmentChangeEvent. Server Side?: {}",!player.level.isClientSide);
            /*DevilRpg.LOGGER.info("----------------------->getSlot {}", event.getSlot().getName());
            DevilRpg.LOGGER.info("----------------------->getFrom {}", event.getFrom().toString());
            DevilRpg.LOGGER.info("----------------------->getTo {}", event.getTo().toString());
            DevilRpg.LOGGER.info("----------------------->getType {}", event.getSlot().getType().toString());*/
            ISkillContainer loadedSkill = getLoadedSkillForPlayer(player, SkillEnum.SKIN_ARMOR);
            loadedSkill.execute(player.level, player, new HashMap<>());
        }
    }

    private static ISkillContainer getLoadedSkillForPlayer(PlayerEntity player, SkillEnum skill) {
        PlayerSkillCapabilityInterface aSkillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapabilityAttacher.SKILL_CAP);
        ISkillContainer loadedSkill = aSkillCap.getLoadedSkill(skill);
        DevilRpg.LOGGER.info("----------------------->loadedSkill {}", loadedSkill);
        return loadedSkill;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onPlayerPickupXP(PlayerXpEvent.PickupXp e) {
        e.getOrb().value *= 0.5;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingUpdateEvent(LivingEvent.LivingUpdateEvent event) {
        if ((event.getEntity() instanceof PlayerEntity))
            DevilRpg.LOGGER.info("----------------------->onLivingUpdateEvent {}", event.getEntity());
        DevilRpg.LOGGER.info("----------------------->onLivingUpdateEvent {}", event);
    }

}
