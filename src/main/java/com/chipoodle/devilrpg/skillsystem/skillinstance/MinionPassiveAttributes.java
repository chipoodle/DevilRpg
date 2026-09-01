package com.chipoodle.devilrpg.skillsystem.skillinstance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.entity.*;
import com.chipoodle.devilrpg.entity.goal.SoulWispGatherLogItemsGoal;
import com.chipoodle.devilrpg.entity.goal.SoulWispHarvestGrassGoal;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;


public class MinionPassiveAttributes {
    public static final String PASSIVE_MINION_HEALTH = "PASSIVE_MINION_HEALTH";
    public static final String PASSIVE_WAR_BEAR_HEALTH = "PASSIVE_WAR_BEAR_HEALTH";
    public static final String PASSIVE_WAR_BEAR_KNOCKBACK_RES = "PASSIVE_WAR_BEAR_KNOCKBACK";
    private final Level levelIn;
    private float factor;
    private Player playerIn;

    public MinionPassiveAttributes(ITamableEntity entity) {
        DevilRpg.LOGGER.info("||---->MinionPassiveAttributes entity {}", ((LivingEntity) entity).getUUID());
        levelIn = entity.level();
        LivingEntity owner = entity.getOwner();

        if (!(entity instanceof IPassiveMinionUpdater && owner instanceof Player))
            return;

        playerIn = (Player) owner;

        if (entity instanceof SoulWolf) {
            factor = 0.3333f;
            applyPassives((SoulWolf) entity);
        }
        if (entity instanceof SoulBear) {
            factor = 1.1f;
            applyPassives((SoulBear) entity);
        }
        if (entity instanceof SoulWisp) {
            factor = 0.9f;
            applyPassives((SoulWisp) entity);
        }
        if (entity instanceof SunflowerShulker) {
            factor = 0.2f;
            applyPassives((SunflowerShulker) entity);
        }
        apply(entity);
    }


    @SuppressWarnings("unchecked")
    private void apply(ITamableEntity entity) {
        DevilRpg.LOGGER.info("||---->MinionPassiveAttributes apply");

        if (!levelIn.isClientSide && playerIn != null) {
            PlayerSkillCapabilityInterface parentCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn,
                    PlayerSkillCapability.INSTANCE);
            HashMap<Holder<Attribute>, AttributeModifier> attributes = new HashMap<>();
            attributes.put(Attributes.MAX_HEALTH,
                    new AttributeModifier(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, PASSIVE_MINION_HEALTH),
                            factor * parentCapability.getSkillsPoints().get(SkillEnum.MINION_VITALITY),
                            AttributeModifier.Operation.ADD_VALUE));
            IPassiveMinionUpdater<ITamableEntity> minion = (IPassiveMinionUpdater<ITamableEntity>) entity;
            minion.applyPassives(attributes, entity);
        }
    }

    private void applyPassives(SoulBear entity) {
        PlayerSkillCapabilityInterface parentCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn,
                PlayerSkillCapability.INSTANCE);
        Integer warBear = parentCapability.getSkillsPoints().get(SkillEnum.WAR_BEAR);
        Integer mountBear = parentCapability.getSkillsPoints().get(SkillEnum.MOUNT_BEAR);
        DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SoulBearEntity warbear:{} factor: {}", warBear, factor);
        DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SoulBearEntity mountBear:{} factor: {}", mountBear, factor);

        entity.setWarBear(warBear);
        entity.setMountBear(mountBear);

        HashMap<Holder<Attribute>, AttributeModifier> attributes = new HashMap<>();
        attributes.put(Attributes.MAX_HEALTH, new AttributeModifier(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, PASSIVE_WAR_BEAR_HEALTH), 3.5D * warBear,
                AttributeModifier.Operation.ADD_VALUE));

        attributes.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, PASSIVE_WAR_BEAR_KNOCKBACK_RES), 0.1666666666 * warBear,
                AttributeModifier.Operation.ADD_VALUE));


        entity.applyPassives(attributes, entity);

    }

    private void applyPassives(SoulWolf entity) {
        DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SoulWolfEntity");
        PlayerSkillCapabilityInterface parentCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerSkillCapability.INSTANCE);
        Integer frostbite = parentCapability.getSkillsPoints().get(SkillEnum.WOLF_FROSTBITE);
        Integer iceArmor = parentCapability.getSkillsPoints().get(SkillEnum.WOLF_ICE_ARMOR);

        entity.setFrostbite(frostbite);
        entity.setIceArmor(iceArmor);
    }

    private void applyPassives(SoulWisp entity) {
        DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SoulWispEntity");
        PlayerSkillCapabilityInterface parentCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerSkillCapability.INSTANCE);
        if (entity instanceof SoulWispHealth soulWispHealth) {
            Integer points = parentCapability.getSkillsPoints().get(SkillEnum.WISP_REGENERATION);
            soulWispHealth.setSecondaryEffect(points, MobEffects.REGENERATION);
        }

        if (entity instanceof SoulWispChopper soulWispChopper) {
            Integer points = parentCapability.getSkillsPoints().get(SkillEnum.WISP_LOG_COLLECTOR);
            if (points != null && points > 0)
                soulWispChopper.goalSelector.addGoal(2, new SoulWispGatherLogItemsGoal(soulWispChopper));
        }

        if (entity instanceof SoulWispForester soulWispForester) {
            Integer points = parentCapability.getSkillsPoints().get(SkillEnum.WISP_SEED_COLLECTOR);
            if (points != null && points > 0)
                soulWispForester.goalSelector.addGoal(3, new SoulWispHarvestGrassGoal(soulWispForester));
        }

    }

    private void applyPassives(SunflowerShulker entity) {
        DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SunflowerShulker");
    }

}
