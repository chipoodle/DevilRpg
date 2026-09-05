/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the editor in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.common;

import net.minecraft.resources.ResourceLocation;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapability;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityInterface;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.init.ModEffects;
import com.chipoodle.devilrpg.network.payload.PlayerAuxiliarPayload;
import com.chipoodle.devilrpg.network.payload.PlayerExperiencePayload;
import com.chipoodle.devilrpg.network.payload.PlayerManaPayload;
import com.chipoodle.devilrpg.network.payload.PlayerMinionPayload;
import com.chipoodle.devilrpg.network.payload.PlayerPassiveSkillPayload;
import com.chipoodle.devilrpg.network.payload.PlayerSkillTreePayload;
import com.chipoodle.devilrpg.network.payload.PlayerStaminaPayload;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author Christian
 */

@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME)
public class PlayerCapabilityForgeEventSubscriber {

    /** Fraccion de XP que se conserva al morir (el resto se pierde). */
    private static final double XP_KEPT = 0.9;

    /**
     * Cada cuantos ticks se repara 1 punto de durabilidad por pieza de cuero mientras se esta
     * transformado. 50 ticks = 2.5s (10x mas lento que antes) para simular la durabilidad de un
     * armadura de diamante.
     */
    private static final int ARMOR_REPAIR_INTERVAL_TICKS = 50;

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone e) {
        if (e.isWasDeath()) {
            // Al morir, conservar el 90% del XP (el nivel no se pierde del todo).
            restoreNinetyPercentXp(e);
            clonePlayerCapability(e, PlayerAuxiliaryCapability.INSTANCE);
            clonePlayerCapability(e, PlayerExperienceCapability.INSTANCE);
            clonePlayerCapability(e, PlayerManaCapability.INSTANCE);
            clonePlayerCapability(e, PlayerStaminaCapability.INSTANCE);
            clonePlayerCapability(e, PlayerMinionCapability.INSTANCE);
            clonePlayerCapability(e, PlayerSkillCapability.INSTANCE);
        }
    }

    /**
     * Tras morir, el {@code experienceLevel} vanilla se resetea a 0. Como el nivel del mod se deriva
     * de el, se conserva el 90% (tanto del nivel como del XP total) para que solo se pierda el 10%.
     */
    private static void restoreNinetyPercentXp(PlayerEvent.Clone e) {
        Player original = e.getOriginal();
        Player clone = e.getEntity();
        clone.totalExperience = (int) Math.floor(original.totalExperience * XP_KEPT);
        clone.experienceLevel = (int) Math.floor(original.experienceLevel * XP_KEPT);
        clone.experienceProgress = original.experienceProgress;
        DevilRpg.LOGGER.info("[XP] Restaurado al {}%% tras morir: nivel {} (antes {}), {} XP",
                Math.round(XP_KEPT * 100), clone.experienceLevel, original.experienceLevel, clone.totalExperience);
    }

    /**
     * Skin Armor: mientras el jugador esta transformado en hombre lobo y lleva armadura de CUERO
     * COMPLETA, la armadura se repara gradualmente (su durabilidad se mantiene alta como si fuera
     * de diamante). Al volver a humano se deja de reparar y vuelve a desgastarse con normalidad.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Reparar muy despacio (cada ARMOR_REPAIR_INTERVAL_TICKS) para simular la durabilidad de
        // una armadura de diamante: la de cuero se desgasta mucho mas lento mientras eres lobo.
        if (player.tickCount % ARMOR_REPAIR_INTERVAL_TICKS != 0) {
            return;
        }
        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null || !aux.isWerewolfTransformation()) {
            return;
        }
        repairLeatherArmor(player);
    }

    /** Repara 1 punto de durabilidad en cada pieza de cuero que no este al maximo. */
    private static void repairLeatherArmor(ServerPlayer player) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem armorItem
                    && armorItem.getMaterial().equals(ArmorMaterials.LEATHER)
                    && stack.isDamageableItem()) {
                int damage = stack.getDamageValue();
                if (damage > 0) {
                    stack.setDamageValue(damage - 1);
                }
            }
        }
    }

    private static <T extends IGenericCapability> void clonePlayerCapability(PlayerEvent.Clone e, Supplier<AttachmentType<T>> cap) {        Player originalPlayer = e.getOriginal();
        Player actualPlayer = e.getEntity();

        CompoundTag originalCompound = originalPlayer.getData(cap).serializeNBT(originalPlayer.level().registryAccess());
        actualPlayer.getData(cap).deserializeNBT(actualPlayer.level().registryAccess(), originalCompound);
    }

    @SubscribeEvent
    public static void onPlayerLogsIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        PlayerManaCapabilityInterface manaCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                PlayerManaCapability.INSTANCE);
        String message1 = String.format("Mana disponible: %f ", manaCap.getMana());
        player.displayClientMessage(Component.literal(message1), false);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();

        if (player.level().isClientSide) {
            return;
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();

        PlayerSkillCapabilityInterface aSkillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
        //Es necesrio enviar nuevamente la activación de todos los pasivos del Player que no se guardan en un Attribute
        SkillEnum.getPassiveSkills().stream().filter(x -> !x.isForMinion()).forEach(skillEnum -> {
            CompoundTag compoundTag = aSkillCap.setSkillToByteArray(skillEnum);
            PacketDistributor.sendToServer(new PlayerPassiveSkillPayload(compoundTag));
        });
    }

    @SubscribeEvent
    public static void onEntityLeaveLevelEvent(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player) {

            if (player.level().isClientSide)
                return;

            BiConsumer<Player, PlayerManaCapabilityInterface> manaBiConsumer = sendManaNBTData();
            manaBiConsumer.accept(player, player.getData(PlayerManaCapability.INSTANCE));

            BiConsumer<Player, PlayerStaminaCapabilityInterface> staminaBiConsumer = sendStaminaNBTData();
            staminaBiConsumer.accept(player, player.getData(PlayerStaminaCapability.INSTANCE));

            BiConsumer<Player, PlayerSkillCapabilityInterface> skillBiConsumer = removeStoredSkillAttributes();
            skillBiConsumer.accept(player, player.getData(PlayerSkillCapability.INSTANCE));

            BiConsumer<Player, PlayerExperienceCapabilityInterface> expBiConsumer = sendExperienceNBTData();
            expBiConsumer.accept(player, player.getData(PlayerExperienceCapability.INSTANCE));

            BiConsumer<Player, PlayerAuxiliaryCapabilityInterface> auxBiConsumer = shapeshiftToNormal();
            auxBiConsumer.accept(player, player.getData(PlayerAuxiliaryCapability.INSTANCE));

            BiConsumer<Player, PlayerMinionCapabilityInterface> minBiConsumer = removeMinions(player);
            minBiConsumer.accept(player, player.getData(PlayerMinionCapability.INSTANCE));
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

        if (entity instanceof ITamableEntity) {
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
        if (player.level().isClientSide)
            return;

        BiConsumer<Player, PlayerManaCapabilityInterface> manaBiConsumer = sendManaNBTData();
        EventUtils.onJoin(player, manaBiConsumer, PlayerManaCapability.INSTANCE);

        BiConsumer<Player, PlayerStaminaCapabilityInterface> staminaBiConsumer = sendStaminaNBTData();
        EventUtils.onJoin(player, staminaBiConsumer, PlayerStaminaCapability.INSTANCE);

        BiConsumer<Player, PlayerSkillCapabilityInterface> skillBiConsumer = removeStoredSkillAttributes();
        EventUtils.onJoin(player, skillBiConsumer, PlayerSkillCapability.INSTANCE);

        BiConsumer<Player, PlayerExperienceCapabilityInterface> expBiConsumer = sendExperienceNBTData();
        EventUtils.onJoin(player, expBiConsumer, PlayerExperienceCapability.INSTANCE);

        BiConsumer<Player, PlayerAuxiliaryCapabilityInterface> auxBiConsumer = shapeshiftToNormal();
        EventUtils.onJoin(player, auxBiConsumer, PlayerAuxiliaryCapability.INSTANCE);

        BiConsumer<Player, PlayerMinionCapabilityInterface> minBiConsumer = removeMinions(player);
        EventUtils.onJoin(player, minBiConsumer, PlayerMinionCapability.INSTANCE);
    }

    private static BiConsumer<Player, PlayerMinionCapabilityInterface> removeMinions(Player player) {
        return (aPlayer, theMin) -> {
            if (!aPlayer.isLocalPlayer()) {
                theMin.removeAllSoulWolf(player);
                theMin.removeAllSoulBear(player);
                theMin.removeAllWisp(player);
            }
        };
    }

    private static BiConsumer<Player, PlayerAuxiliaryCapabilityInterface> shapeshiftToNormal() {
        return (aPlayer, theAux) -> {
            if (!aPlayer.isLocalPlayer()) {
                theAux.setWerewolfAttack(false, aPlayer);
                theAux.setWerewolfTransformation(false, aPlayer);
            }
        };
    }

    private static BiConsumer<Player, PlayerExperienceCapabilityInterface> sendExperienceNBTData() {
        return (aPlayer, theExp) -> {
            if (!aPlayer.isLocalPlayer())
                PacketDistributor.sendToPlayer((ServerPlayer) aPlayer, new PlayerExperiencePayload(theExp.serializeNBT(aPlayer.level().registryAccess())));
        };
    }

    private static BiConsumer<Player, PlayerManaCapabilityInterface> sendManaNBTData() {
        return (aPlayer, theMana) -> {
            if (!aPlayer.isLocalPlayer())
                PacketDistributor.sendToPlayer((ServerPlayer) aPlayer, new PlayerManaPayload(theMana.serializeNBT(aPlayer.level().registryAccess())));
        };
    }

    private static BiConsumer<Player, PlayerStaminaCapabilityInterface> sendStaminaNBTData() {
        return (aPlayer, theStamina) -> {
            if (!aPlayer.isLocalPlayer())
                PacketDistributor.sendToPlayer((ServerPlayer) aPlayer, new PlayerStaminaPayload(theStamina.serializeNBT(aPlayer.level().registryAccess())));
        };
    }

    private static BiConsumer<Player, PlayerSkillCapabilityInterface> removeStoredSkillAttributes() {
        return (aPlayer, presentSkill) -> {
            if (!aPlayer.isLocalPlayer()) {
                HashMap<String, String> attributeModifiers = presentSkill.getAttributeModifiers();
                String hlthAttMod = attributeModifiers.get(Attributes.MAX_HEALTH.value().getDescriptionId());
                String spdAttMod = attributeModifiers.get(Attributes.MOVEMENT_SPEED.value().getDescriptionId());
                String armrAttMod = attributeModifiers.get(Attributes.ARMOR.value().getDescriptionId());
                String attDmgMod = attributeModifiers.get(Attributes.ATTACK_DAMAGE.value().getDescriptionId());
                String stepDmgMod = attributeModifiers.get(Attributes.STEP_HEIGHT.value().getDescriptionId());

                if (hlthAttMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.MAX_HEALTH)).removeModifier(ResourceLocation.parse(hlthAttMod));
                }
                if (spdAttMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.MOVEMENT_SPEED)).removeModifier(ResourceLocation.parse(spdAttMod));
                }
                if (armrAttMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.ARMOR)).removeModifier(ResourceLocation.parse(armrAttMod));
                }
                if (attDmgMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.ATTACK_DAMAGE)).removeModifier(ResourceLocation.parse(attDmgMod));
                }
                if (stepDmgMod != null) {
                    Objects.requireNonNull(aPlayer.getAttribute(Attributes.STEP_HEIGHT)).removeModifier(ResourceLocation.parse(stepDmgMod));
                }

                PacketDistributor.sendToPlayer((ServerPlayer) aPlayer, new PlayerSkillTreePayload(presentSkill.serializeNBT(aPlayer.level().registryAccess())));

                aPlayer.removeEffect(ModEffects.KNOCKBACK_RESISTANCE);
                aPlayer.removeEffect(MobEffects.ABSORPTION);
            }
        };
    }

    @SubscribeEvent
    public static void onPlayerLevelChange(PlayerXpEvent.LevelChange e) {
        Player player = e.getEntity();

        PlayerExperienceCapabilityInterface expCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerExperienceCapability.INSTANCE);
        expCap.setCurrentLevel(player.experienceLevel + e.getLevels(), player);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingEquipmentChangeEvent(LivingEquipmentChangeEvent event) {

        if ((event.getEntity() instanceof Player player) && event.getSlot().getType().compareTo(EquipmentSlot.Type.HUMANOID_ARMOR) == 0) {
            AbstractSkillExecutor loadedSkill = getLoadedSkillForPlayer(player, SkillEnum.SKIN_ARMOR);
            loadedSkill.execute(player.level(), player, new HashMap<>());
        }
    }

    private static AbstractSkillExecutor getLoadedSkillForPlayer(Player player, SkillEnum skill) {
        PlayerSkillCapabilityInterface aSkillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
        AbstractSkillExecutor loadedSkill = aSkillCap.getLoadedSkillExecutor(skill);
        return loadedSkill;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerSetSpawnEvent(PlayerSetSpawnEvent event) {
        if (event.getNewSpawn() != null) {
            //DevilRpg.LOGGER.info("||||||||||||||||||||||||||| SPAWN POINT FIRST {}", event.getNewSpawn());
        } else {
            //DevilRpg.LOGGER.info("||||||||||||||||||||||||||| SPAWN POINT");
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerPickupXP(PlayerXpEvent.PickupXp e) {
        //e.getOrb().value *= 0.5;
    }
}
