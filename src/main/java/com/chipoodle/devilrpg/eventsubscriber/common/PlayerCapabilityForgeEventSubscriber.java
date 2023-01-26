/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityProvider;
import com.chipoodle.devilrpg.capability.experience.IBaseExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityProvider;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;
import com.chipoodle.devilrpg.capability.player_minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBasePlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.capability.tamable_minion.IBaseTamableMinionCapability;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityProvider;
import com.chipoodle.devilrpg.entity.ITameableEntity;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerExperienceClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import com.chipoodle.devilrpg.network.handler.PlayerSkillClientServerHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
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
import net.minecraftforge.fml.network.PacketDistributor;

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
    public static final ResourceLocation AUX_CAP = new ResourceLocation(DevilRpg.MODID, "auxiliary");
    public static final ResourceLocation MINION_CAP = new ResourceLocation(DevilRpg.MODID, "minion");
    public static final ResourceLocation TAMABLE_MINION_CAP = new ResourceLocation(DevilRpg.MODID, "tamable_minion");


    @SubscribeEvent
    public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if ((event.getObject() instanceof PlayerEntity)) {
            DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.attachCapability()");
            event.addCapability(MANA_CAP, new PlayerManaCapabilityProvider());
            event.addCapability(SKILL_CAP, new PlayerSkillCapabilityProvider());
            event.addCapability(EXP_CAP, new PlayerExperienceCapabilityProvider());
            event.addCapability(AUX_CAP, new PlayerAuxiliarCapabilityProvider());
            event.addCapability(MINION_CAP, new PlayerMinionCapabilityProvider());
            event.addCapability(TAMABLE_MINION_CAP, new TamableMinionCapabilityProvider());
            LOGGER.info("------------------------>Capabilities attached");
        }

        if ((event.getObject() instanceof ITameableEntity)) {
            event.addCapability(TAMABLE_MINION_CAP, new TamableMinionCapabilityProvider());
            LOGGER.info("------------------------>Capabilities attached");
        }
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

        IBaseManaCapability manaCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                PlayerManaCapabilityProvider.MANA_CAP);
        String message1 = String.format("Mana disponible: %f ", manaCap.getMana());
        player.sendMessage(new StringTextComponent(message1), player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone e) {
        if (e.isWasDeath()) {
            DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onPlayerClone()");
            clonePlayerCapability(e, PlayerManaCapabilityProvider.MANA_CAP);
            clonePlayerCapability(e, PlayerSkillCapabilityProvider.SKILL_CAP);
            clonePlayerCapability(e, PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
            clonePlayerCapability(e, PlayerAuxiliarCapabilityProvider.AUX_CAP);
            clonePlayerCapability(e, PlayerMinionCapabilityProvider.MINION_CAP);
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

        DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onPlayerRespawn()");
        PlayerEntity player = event.getPlayer();

        if (player.level.isClientSide) {
            return;
        }
        LazyOptional<IBaseManaCapability> mana = player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);
        LazyOptional<IBasePlayerSkillCapability> skill = player.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
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
        if (event.getEntity() instanceof ITameableEntity) {
            DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld() == ITameable:{} ", event.getEntity().getScoreboardName());
            //new MinionPassiveAttributes((ITameableEntity) event.getEntity());
            IBaseTamableMinionCapability minionPassiveCap
                    = IGenericCapability.getUnwrappedMinionCapability((ITameableEntity) event.getEntity(),
                    TamableMinionCapabilityProvider.TAMABLE_MINION_CAP);
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

        DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld()");
        BiConsumer<PlayerEntity, LazyOptional<IBaseManaCapability>> manaBiConsumer = sendManaNBTData();
        EventUtils.onJoin(player, manaBiConsumer, PlayerManaCapabilityProvider.MANA_CAP);

        BiConsumer<PlayerEntity, LazyOptional<IBasePlayerSkillCapability>> skillBiConsumer = removeStoredSkillAttributes();
        EventUtils.onJoin(player, skillBiConsumer, PlayerSkillCapabilityProvider.SKILL_CAP);

        BiConsumer<PlayerEntity, LazyOptional<IBaseExperienceCapability>> expBiConsumer = sendExperienceNBTData();
        EventUtils.onJoin(player, expBiConsumer, PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);

        BiConsumer<PlayerEntity, LazyOptional<IBaseAuxiliarCapability>> auxBiConsumer = shapeshiftToNormal();
        EventUtils.onJoin(player, auxBiConsumer, PlayerAuxiliarCapabilityProvider.AUX_CAP);

        BiConsumer<PlayerEntity, LazyOptional<IBaseMinionCapability>> minBiConsumer = removeMinions(player);
        EventUtils.onJoin(player, minBiConsumer, PlayerMinionCapabilityProvider.MINION_CAP);
    }

    private static BiConsumer<PlayerEntity, LazyOptional<IBaseMinionCapability>> removeMinions(PlayerEntity player) {
        BiConsumer<PlayerEntity, LazyOptional<IBaseMinionCapability>> minBiConsumer = (aPlayer, theMin) -> {
            theMin.ifPresent(x -> {
                x.removeAllSoulWolf(player);
                x.removeAllSoulBear(player);
                x.removeAllWisp(player);
            });
            DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld().minBiConsumer");
        };
        return minBiConsumer;
    }

    private static BiConsumer<PlayerEntity, LazyOptional<IBaseAuxiliarCapability>> shapeshiftToNormal() {
        BiConsumer<PlayerEntity, LazyOptional<IBaseAuxiliarCapability>> auxBiConsumer = (aPlayer, theAux) -> {
            theAux.ifPresent(x -> {
                x.setWerewolfAttack(false, aPlayer);
                x.setWerewolfTransformation(false, aPlayer);
            });
            DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld().auxBiConsumer");
        };
        return auxBiConsumer;
    }

    private static BiConsumer<PlayerEntity, LazyOptional<IBaseExperienceCapability>> sendExperienceNBTData() {
        BiConsumer<PlayerEntity, LazyOptional<IBaseExperienceCapability>> expBiConsumer = (aPlayer, theExp) -> {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) aPlayer),
                    new PlayerExperienceClientServerHandler(theExp.map(x -> x.getNBTData()).orElse(null)));
            DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld().expBiConsumer");
        };
        return expBiConsumer;
    }

    private static BiConsumer<PlayerEntity, LazyOptional<IBaseManaCapability>> sendManaNBTData() {
        BiConsumer<PlayerEntity, LazyOptional<IBaseManaCapability>> manaBiConsumer = (aPlayer, theMana) -> {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) aPlayer),
                    new PlayerManaClientServerHandler(theMana.map(IBaseManaCapability::getNBTData).orElse(null)));
            DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld().manaBiConsumer");
        };
        return manaBiConsumer;
    }

    private static BiConsumer<PlayerEntity, LazyOptional<IBasePlayerSkillCapability>> removeStoredSkillAttributes() {
        BiConsumer<PlayerEntity, LazyOptional<IBasePlayerSkillCapability>> skillBiConsumer = (aPlayer, theSkill) -> {
            theSkill.ifPresent(presentSkill -> {
                HashMap<String, UUID> attributeModifiers = presentSkill.getAttributeModifiers();
                UUID hlthAttMod = attributeModifiers.get(Attributes.MAX_HEALTH.getDescriptionId());
                UUID spdAttMod = attributeModifiers.get(Attributes.MOVEMENT_SPEED.getDescriptionId());
                UUID armrAttMod = attributeModifiers.get(Attributes.ARMOR.getDescriptionId());

                if (hlthAttMod != null) {
                    // DevilRpg.LOGGER.info("||-------------> removing health id: " + hlthAttMod);
                    aPlayer.getAttribute(Attributes.MAX_HEALTH).removeModifier(hlthAttMod);
                }
                if (spdAttMod != null) {
                    aPlayer.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(spdAttMod);
                }
                if (armrAttMod != null) {
                    aPlayer.getAttribute(Attributes.ARMOR).removeModifier(armrAttMod);
                }


                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) aPlayer),
                        new PlayerSkillClientServerHandler(presentSkill.getNBTData()));
            });
            DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.onEntityJoinWorld().skillBiConsumer");
        };
        return skillBiConsumer;
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

        IBaseExperienceCapability expCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
        expCap.setCurrentLevel(player.experienceLevel + e.getLevels(), player);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerSetSpawnEvent(PlayerSetSpawnEvent event) {
        DevilRpg.LOGGER.info("----------------------->ForgeEventSubscriber.playerSetSpawnEvent()");
        if (event.getNewSpawn() != null) {
            DevilRpg.LOGGER.info(" SPAWN POINT FIRST {}", event.getNewSpawn());

        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingEquipmentChangeEvent(LivingEquipmentChangeEvent event) {

        if ((event.getEntity() instanceof PlayerEntity) && event.getSlot().getType().compareTo(EquipmentSlotType.Group.ARMOR) == 0) {
            PlayerEntity player = (PlayerEntity) event.getEntity();
            DevilRpg.LOGGER.info("----------------------->onLivingEquipmentChangeEvent. Server Side?: {}",!player.level.isClientSide);
            /*DevilRpg.LOGGER.info("----------------------->getSlot {}", event.getSlot().getName());
            DevilRpg.LOGGER.info("----------------------->getFrom {}", event.getFrom().toString());
            DevilRpg.LOGGER.info("----------------------->getTo {}", event.getTo().toString());
            DevilRpg.LOGGER.info("----------------------->getType {}", event.getSlot().getType().toString());*/
            ISkillContainer loadedSkill = getLoadedSkillForPlayer(player, SkillEnum.SKIN_ARMOR);
            loadedSkill.execute(player.level, player, new HashMap<>());
        }
    }

    private static ISkillContainer getLoadedSkillForPlayer(PlayerEntity player, SkillEnum skill) {
        IBasePlayerSkillCapability aSkillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapabilityProvider.SKILL_CAP);
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
