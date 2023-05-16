package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityAttacher;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.entity.*;
import com.chipoodle.devilrpg.util.SkillEnum;
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
    private final Level levelIn;
    private float factor;
    private Player playerIn;

    public MinionPassiveAttributes(ITamableEntity entity) {
        DevilRpg.LOGGER.info("||---->MinionPassiveAttributes");
        levelIn = entity.getLevel();
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
            factor = 1.0f;
            applyPassives((SoulWisp) entity);
        }
        apply(entity);
    }

    @SuppressWarnings("unchecked")
    private void apply(ITamableEntity entity) {
        DevilRpg.LOGGER.info("||---->MinionPassiveAttributes apply");

        if (!levelIn.isClientSide && playerIn != null) {
            PlayerSkillCapabilityInterface parentCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn,
                    PlayerSkillCapability.INSTANCE);
            HashMap<Attribute, AttributeModifier> attributes = new HashMap<>();
            attributes.put(Attributes.MAX_HEALTH,
                    new AttributeModifier(PASSIVE_MINION_HEALTH,
                            factor * parentCapability.getSkillsPoints().get(SkillEnum.MINION_VITALITY),
                            AttributeModifier.Operation.ADDITION));
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

        entity.setWarBear(warBear);
        entity.setMountBear(mountBear);

        HashMap<Attribute, AttributeModifier> attributes = new HashMap<>();
        attributes.put(Attributes.MAX_HEALTH, new AttributeModifier(PASSIVE_WAR_BEAR_HEALTH, 3.5D * warBear,
                AttributeModifier.Operation.ADDITION));
        IPassiveMinionUpdater<SoulBear> minion = entity;
        minion.applyPassives(attributes, entity);

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

    }

}
