/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityImplementation;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityImplementation;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityImplementation;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityImplementation;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityImplementation;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapability;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityImplementation;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityInterface;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.init.ModCapability;
import com.chipoodle.devilrpg.init.ModEffects;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.*;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.mojang.blaze3d.shaders.Effect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * @author Christian
 */

@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE)
public class PlayerCapabilityForgeEventSubscriber {

    @SubscribeEvent
    public static void onRegisterCapabilitiesEvent(RegisterCapabilitiesEvent event) {
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onRegisterCapabilitiesEvent()");
        event.register(PlayerAuxiliaryCapabilityImplementation.class);
        event.register(PlayerExperienceCapabilityImplementation.class);
        event.register(PlayerManaCapabilityImplementation.class);
        event.register(PlayerStaminaCapabilityImplementation.class);
        event.register(PlayerMinionCapabilityImplementation.class);
        event.register(PlayerSkillCapabilityImplementation.class);
        event.register(TamableMinionCapabilityImplementation.class);

    }

    @SubscribeEvent
    public static void onAttachCapabilitiesEvent(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onAttachCapabilitiesEvent() server? {}",event.getObject().level.isClientSide);
            ModCapability.registerForPlayer(event);
        }
        if (event.getObject() instanceof ITamableEntity) {
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onAttachCapabilitiesEvent()");
            ModCapability.registerForTamableEntity(event);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone e) {
        //if(e.getOriginal().level.isClientSide)
            //return;

        if (e.isWasDeath()) {
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerClone(). Player:{}, Server? {}",e.getOriginal().getUUID(),(!e.getOriginal().level.isClientSide));
            clonePlayerCapability(e, PlayerAuxiliaryCapability.INSTANCE);
            clonePlayerCapability(e, PlayerExperienceCapability.INSTANCE);
            clonePlayerCapability(e, PlayerManaCapability.INSTANCE);
            clonePlayerCapability(e, PlayerStaminaCapability.INSTANCE);
            clonePlayerCapability(e, PlayerMinionCapability.INSTANCE);
            clonePlayerCapability(e, PlayerSkillCapability.INSTANCE);
        }

    }

    private static void clonePlayerCapability(PlayerEvent.Clone e, Capability<? extends IGenericCapability> cap) {


        Player originalPlayer = e.getOriginal();
        Player actualPlayer = e.getEntity();

        originalPlayer.reviveCaps();

        LazyOptional<? extends IGenericCapability> original = e.getOriginal().getCapability(cap);
        LazyOptional<? extends IGenericCapability> actual = e.getEntity().getCapability(cap);
        //DevilRpg.LOGGER.info("----------------------->clonePlayerCapability. Original: {}", original);
        //DevilRpg.LOGGER.info("----------------------->clonePlayerCapability. New: {}", actual);

        IGenericCapability originalCap = original.orElseThrow(NullPointerException::new);
        CompoundTag originalCompound = originalCap.serializeNBT();
        IGenericCapability actualCap = actual.orElseThrow(NegativeArraySizeException::new);
        actualCap.deserializeNBT(originalCompound);
        //DevilRpg.LOGGER.info("----------------------->clonePlayerCapability. Original: {}", originalCompound);
        //actual.ifPresent(actualCap -> original.ifPresent(originalCap -> actualCap.deserializeNBT(originalCap.serializeNBT())));
        originalPlayer.invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerLogsIn(PlayerEvent.PlayerLoggedInEvent event) {
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerLogsIn()");
        Player player = event.getEntity();
        PlayerManaCapabilityInterface manaCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                PlayerManaCapability.INSTANCE);
        String message1 = String.format("Mana disponible: %f ", manaCap.getMana());
        player.displayClientMessage(Component.literal(message1), false);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {

        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerRespawn()");
        Player player = event.getEntity();

        if (player.level.isClientSide) {
            return;
        }
        LazyOptional<PlayerManaCapabilityInterface> mana = player.getCapability(PlayerManaCapability.INSTANCE);
        LazyOptional<PlayerStaminaCapabilityInterface> stamina = player.getCapability(PlayerStaminaCapability.INSTANCE);
        LazyOptional<PlayerSkillCapabilityInterface> skill = player.getCapability(PlayerSkillCapability.INSTANCE);
        LazyOptional<PlayerExperienceCapabilityInterface> exp = player.getCapability(PlayerExperienceCapability.INSTANCE);
        LazyOptional<PlayerAuxiliaryCapabilityInterface> aux = player.getCapability(PlayerAuxiliaryCapability.INSTANCE);
        LazyOptional<PlayerMinionCapabilityInterface> min = player.getCapability(PlayerMinionCapability.INSTANCE);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();

        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerChangedDimension()");
        PlayerSkillCapabilityInterface aSkillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
        //Es necesrio enviar nuevamente la activación de todos los pasivos del Player que no se guardan en un Attribute
        SkillEnum.getPassiveSkills().stream().filter(x->!x.isForMinion()).forEach(skillEnum->{
            CompoundTag compoundTag = aSkillCap.setSkillToByteArray(skillEnum);
            ModNetwork.CHANNEL.sendToServer(new PlayerPassiveSkillServerHandler(compoundTag));
        });
    }

    @SubscribeEvent
    public static void onEntityLeaveLevelEvent(EntityLeaveLevelEvent event) {
        if(event.getEntity() instanceof Player player){

            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onEntityLeaveLevelEvent({}) ", player.getScoreboardName());


            BiConsumer<Player, LazyOptional<PlayerManaCapabilityInterface>> manaBiConsumer = sendManaNBTData();
            manaBiConsumer.accept(player,player.getCapability(PlayerManaCapability.INSTANCE));

            BiConsumer<Player, LazyOptional<PlayerStaminaCapabilityInterface>> staminaBiConsumer = sendStaminaNBTData();
            staminaBiConsumer.accept(player,player.getCapability(PlayerStaminaCapability.INSTANCE));

            BiConsumer<Player, LazyOptional<PlayerSkillCapabilityInterface>> skillBiConsumer = removeStoredSkillAttributes();
            skillBiConsumer.accept(player,player.getCapability(PlayerSkillCapability.INSTANCE));

            BiConsumer<Player, LazyOptional<PlayerExperienceCapabilityInterface>> expBiConsumer = sendExperienceNBTData();
            expBiConsumer.accept(player,player.getCapability(PlayerExperienceCapability.INSTANCE));

            BiConsumer<Player, LazyOptional<PlayerAuxiliaryCapabilityInterface>> auxBiConsumer = shapeshiftToNormal();
            auxBiConsumer.accept(player,player.getCapability(PlayerAuxiliaryCapability.INSTANCE));

            BiConsumer<Player, LazyOptional<PlayerMinionCapabilityInterface>> minBiConsumer = removeMinions(player);
            minBiConsumer.accept(player,player.getCapability(PlayerMinionCapability.INSTANCE));
        }
    }


    /**
     * Restore client player capabilities' values on join. Applies passive skills to entities
     *
     * @param event EntityJoinLevelEvent
     */
    @SubscribeEvent
    public static void onApplyPetPassives(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();

        if ((entity instanceof Player || entity instanceof ItemEntity)) {
            return;
        }

        //DevilRpg.LOGGER.info("-------({})", entity.getClass());
        if (entity instanceof ITamableEntity) {
            DevilRpg.LOGGER.debug("----------------------->PlayerCapabilityForgeEventSubscriber.onTamableJoinLevelEvent({})", entity.getScoreboardName());
            //new MinionPassiveAttributes((ITameableEntity) event.getEntity());
            TamableMinionCapabilityInterface minionPassiveCap
                    = IGenericCapability.getUnwrappedMinionCapability((ITamableEntity) entity,
                    TamableMinionCapability.INSTANCE);
            minionPassiveCap.applyPassives((ITamableEntity) entity);
        }

    }

    /**
     * Restore client player capabilities' values on join. Applies passive skills to player
     *
     * @param event EntityJoinLevelEvent
     */
    @SubscribeEvent
    public static void onPlayerJoinLevelEvent(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level.isClientSide)
            return;
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerJoinLevelEvent({}) ", player.getScoreboardName());

        BiConsumer<Player, LazyOptional<PlayerManaCapabilityInterface>> manaBiConsumer = sendManaNBTData();
        EventUtils.onJoin(player, manaBiConsumer, PlayerManaCapability.INSTANCE);

        BiConsumer<Player, LazyOptional<PlayerStaminaCapabilityInterface>> staminaBiConsumer = sendStaminaNBTData();
        EventUtils.onJoin(player, staminaBiConsumer, PlayerStaminaCapability.INSTANCE);

        BiConsumer<Player, LazyOptional<PlayerSkillCapabilityInterface>> skillBiConsumer = removeStoredSkillAttributes();
        EventUtils.onJoin(player, skillBiConsumer, PlayerSkillCapability.INSTANCE);

        BiConsumer<Player, LazyOptional<PlayerExperienceCapabilityInterface>> expBiConsumer = sendExperienceNBTData();
        EventUtils.onJoin(player, expBiConsumer, PlayerExperienceCapability.INSTANCE);

        BiConsumer<Player, LazyOptional<PlayerAuxiliaryCapabilityInterface>> auxBiConsumer = shapeshiftToNormal();
        EventUtils.onJoin(player, auxBiConsumer, PlayerAuxiliaryCapability.INSTANCE);

        BiConsumer<Player, LazyOptional<PlayerMinionCapabilityInterface>> minBiConsumer = removeMinions(player);
        EventUtils.onJoin(player, minBiConsumer, PlayerMinionCapability.INSTANCE);
    }

    private static BiConsumer<Player, LazyOptional<PlayerMinionCapabilityInterface>> removeMinions(Player player) {
        return (aPlayer, theMin) -> {
            theMin.ifPresent(x -> {
                x.removeAllSoulWolf(player);
                x.removeAllSoulBear(player);
                x.removeAllWisp(player);
            });
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.removeMinions");
        };
    }

    private static BiConsumer<Player, LazyOptional<PlayerAuxiliaryCapabilityInterface>> shapeshiftToNormal() {
        return (aPlayer, theAux) -> {
            theAux.ifPresent(x -> {
                x.setWerewolfAttack(false, aPlayer);
                x.setWerewolfTransformation(false, aPlayer);
            });
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.shapeshiftToNormal");
        };
    }

    private static BiConsumer<Player, LazyOptional<PlayerExperienceCapabilityInterface>> sendExperienceNBTData() {
        return (aPlayer, theExp) -> {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) aPlayer),
                    new PlayerExperienceClientServerHandler(theExp.map(INBTSerializable::serializeNBT).orElse(null)));
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.sendExperienceNBTData");
        };
    }

    private static BiConsumer<Player, LazyOptional<PlayerManaCapabilityInterface>> sendManaNBTData() {
        return (aPlayer, theMana) -> {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) aPlayer),
                    new PlayerManaClientServerHandler(theMana.map(PlayerManaCapabilityInterface::serializeNBT).orElse(null)));
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.sendManaNBTData");
        };
    }

    private static BiConsumer<Player, LazyOptional<PlayerStaminaCapabilityInterface>> sendStaminaNBTData() {
        return (aPlayer, theMana) -> {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) aPlayer),
                    new PlayerStaminaClientServerHandler(theMana.map(PlayerStaminaCapabilityInterface::serializeNBT).orElse(null)));
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.sendStaminaNBTData");
        };
    }

    private static BiConsumer<Player, LazyOptional<PlayerSkillCapabilityInterface>> removeStoredSkillAttributes() {
        return (aPlayer, theSkill) -> {
            theSkill.ifPresent(presentSkill -> {
                HashMap<String, UUID> attributeModifiers = presentSkill.getAttributeModifiers();
                UUID hlthAttMod = attributeModifiers.get(Attributes.MAX_HEALTH.getDescriptionId());
                UUID spdAttMod = attributeModifiers.get(Attributes.MOVEMENT_SPEED.getDescriptionId());
                UUID armrAttMod = attributeModifiers.get(Attributes.ARMOR.getDescriptionId());
                UUID attDmgMod = attributeModifiers.get(Attributes.ATTACK_DAMAGE.getDescriptionId());
                UUID stepDmgMod = attributeModifiers.get(ForgeMod.STEP_HEIGHT_ADDITION.get().getDescriptionId());

                if (hlthAttMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.MAX_HEALTH)).removeModifier(hlthAttMod);
                }
                if (spdAttMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.MOVEMENT_SPEED)).removeModifier(spdAttMod);
                }
                if (armrAttMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.ARMOR)).removeModifier(armrAttMod);
                }
                if (attDmgMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.ATTACK_DAMAGE)).removeModifier(attDmgMod);
                }
                if (stepDmgMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get())).removeModifier(stepDmgMod);
                }

                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) aPlayer),
                        new PlayerSkillTreeClientServerHandler(presentSkill.serializeNBT()));

                aPlayer.removeEffect(ModEffects.KNOCKBACK_RESISTANCE.get());
                aPlayer.removeEffect(MobEffects.ABSORPTION);
            });
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.removeStoredSkillAttributes");
        };
    }

    @SubscribeEvent
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerSleep()");
        Player player = event.getEntity();


        if (player.level.isClientSide) {
            return;
        }

        LazyOptional<PlayerManaCapabilityInterface> mana = player.getCapability(PlayerManaCapability.INSTANCE, null);
        mana.ifPresent((cap) -> cap.setMana(mana.map(PlayerManaCapabilityInterface::getMaxMana).orElse(Float.NaN), player));

        String message = String.format(
                "You refreshed yourself in the bed. You recovered mana and you have %f mana left.",
                mana.map(PlayerManaCapabilityInterface::getMaxMana).orElse(Float.NaN));
        player.sendSystemMessage(Component.literal(message));
    }

    @SubscribeEvent
    public static void onPlayerLevelChange(PlayerXpEvent.LevelChange e) {
        Player player = e.getEntity();
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerLevelChange() Client? "
                + player.level.isClientSide + " level? " + player.experienceLevel + " " + player.totalExperience);

        PlayerExperienceCapabilityInterface expCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerExperienceCapability.INSTANCE);
        expCap.setCurrentLevel(player.experienceLevel + e.getLevels(), player);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingEquipmentChangeEvent(LivingEquipmentChangeEvent event) {

        if ((event.getEntity() instanceof Player player) && event.getSlot().getType().compareTo(EquipmentSlot.Type.ARMOR) == 0) {
            DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onLivingEquipmentChangeEvent. Server Side?: {}", !player.level.isClientSide);
           /* DevilRpg.LOGGER.info("----------------------->getSlot {}", event.getSlot().getName());
            DevilRpg.LOGGER.info("----------------------->getFrom {}", event.getFrom().toString());
            DevilRpg.LOGGER.info("----------------------->getTo {}", event.getTo().toString());
            DevilRpg.LOGGER.info("----------------------->getType {}", event.getSlot().getType().toString());*/
            AbstractSkillExecutor loadedSkill = getLoadedSkillForPlayer(player, SkillEnum.SKIN_ARMOR);
            loadedSkill.execute(player.level, player, new HashMap<>());
        }
    }

    private static AbstractSkillExecutor getLoadedSkillForPlayer(Player player, SkillEnum skill) {
        PlayerSkillCapabilityInterface aSkillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
        AbstractSkillExecutor loadedSkill = aSkillCap.getLoadedSkillExecutor(skill);
        DevilRpg.LOGGER.info("----------------------->loadedSkill {}", loadedSkill);
        return loadedSkill;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerSetSpawnEvent(PlayerSetSpawnEvent event) {
        DevilRpg.LOGGER.info("----------------------->PlayerCapabilityForgeEventSubscriber.onPlayerSetSpawnEvent()");
        if (event.getNewSpawn() != null) {
            DevilRpg.LOGGER.info("||||||||||||||||||||||||||| SPAWN POINT FIRST {}", event.getNewSpawn());

        }
        else{
            DevilRpg.LOGGER.info("||||||||||||||||||||||||||| SPAWN POINT");
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onPlayerPickupXP(PlayerXpEvent.PickupXp e) {
        //e.getOrb().value *= 0.5;
    }

}
