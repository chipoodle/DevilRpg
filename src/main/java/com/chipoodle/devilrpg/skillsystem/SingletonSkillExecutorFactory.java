package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.skillsystem.skillinstance.*;
import com.chipoodle.devilrpg.util.SkillEnum;

import java.util.Hashtable;

public class SingletonSkillExecutorFactory {

    private final Hashtable<SkillEnum, ISkillContainer> skillPool = new Hashtable<>();
    private final PlayerSkillCapabilityImplementation parentCapability;

    public SingletonSkillExecutorFactory(PlayerSkillCapabilityImplementation parentCapability) {
        this.parentCapability = parentCapability;
        DevilRpg.LOGGER.debug("--------> SingletonSkillExecutorFactory {}", this);
    }

    public ISkillContainer getOrCreate(SkillEnum skillEnum) {

        switch (skillEnum) {

            case SUMMON_SOUL_WOLF -> {
                if (!skillPool.containsKey(SkillEnum.SUMMON_SOUL_WOLF))
                    skillPool.put(SkillEnum.SUMMON_SOUL_WOLF, new SkillSummonSoulWolf(parentCapability));
                return skillPool.get(SkillEnum.SUMMON_SOUL_WOLF);
            }
            case SUMMON_SOUL_BEAR -> {
                if (!skillPool.containsKey(SkillEnum.SUMMON_SOUL_BEAR))
                    skillPool.put(SkillEnum.SUMMON_SOUL_BEAR, new SkillSummonSoulBear(parentCapability));
                return skillPool.get(SkillEnum.SUMMON_SOUL_BEAR);
            }
            case FROSTBALL -> {
                if (!skillPool.containsKey(SkillEnum.FROSTBALL))
                    skillPool.put(SkillEnum.FROSTBALL, new SkillFrostBall(parentCapability));
                return skillPool.get(SkillEnum.FROSTBALL);
            }
            case SUMMON_WISP_HEALTH -> {
                if (!skillPool.containsKey(SkillEnum.SUMMON_WISP_HEALTH))
                    skillPool.put(SkillEnum.SUMMON_WISP_HEALTH, new SkillSummonIWispHealth(parentCapability));
                return skillPool.get(SkillEnum.SUMMON_WISP_HEALTH);
            }
            case SUMMON_WISP_CURSE -> {
                if (!skillPool.containsKey(SkillEnum.SUMMON_WISP_CURSE))
                    skillPool.put(SkillEnum.SUMMON_WISP_CURSE, new SkillSummonIWispCurse(parentCapability));
                return skillPool.get(SkillEnum.SUMMON_WISP_CURSE);

            }
            case SUMMON_WISP_BOMB -> {
                if (!skillPool.containsKey(SkillEnum.SUMMON_WISP_BOMB))
                    skillPool.put(SkillEnum.SUMMON_WISP_BOMB, new SkillSummonIWispBomber(parentCapability));
                return skillPool.get(SkillEnum.SUMMON_WISP_BOMB);
            }
            case SUMMON_WISP_ARCHER -> {
                if (!skillPool.containsKey(SkillEnum.SUMMON_WISP_ARCHER))
                    skillPool.put(SkillEnum.SUMMON_WISP_ARCHER, new SkillSummonIWispArcher(parentCapability));
                return skillPool.get(SkillEnum.SUMMON_WISP_ARCHER);
            }
            case TRANSFORM_WEREWOLF -> {
                if (!skillPool.containsKey(SkillEnum.TRANSFORM_WEREWOLF))
                    skillPool.put(SkillEnum.TRANSFORM_WEREWOLF, new SkillShapeshiftWerewolf(parentCapability));
                return skillPool.get(SkillEnum.TRANSFORM_WEREWOLF);
            }
            case SKIN_ARMOR -> {
                if (!skillPool.containsKey(SkillEnum.SKIN_ARMOR))
                    skillPool.put(SkillEnum.SKIN_ARMOR, new PlayerPassiveSkillSkinArmorAttribute(parentCapability));
                return skillPool.get(SkillEnum.SKIN_ARMOR);
            }
            case WEREWOLF_HIT -> {
                if (!skillPool.containsKey(SkillEnum.WEREWOLF_HIT))
                    skillPool.put(SkillEnum.WEREWOLF_HIT, new PlayerPassiveWerewolfHitAttribute(parentCapability));
                return skillPool.get(SkillEnum.WEREWOLF_HIT);
            }
            case WEREWOLF_VITALITY -> {
                if (!skillPool.containsKey(SkillEnum.WEREWOLF_VITALITY))
                    skillPool.put(SkillEnum.WEREWOLF_VITALITY, new PlayerPassiveWerewolfVitalityAttribute(parentCapability));
                return skillPool.get(SkillEnum.WEREWOLF_VITALITY);
            }
            case SOULVINE -> {
                if (!skillPool.containsKey(SkillEnum.SOULVINE))
                    skillPool.put(SkillEnum.SOULVINE, new SkillSoulVine(parentCapability));
                return skillPool.get(SkillEnum.SOULVINE);
            }
            case MANA_POOL -> {
                if (!skillPool.containsKey(SkillEnum.MANA_POOL))
                    skillPool.put(SkillEnum.MANA_POOL, new PlayerPassiveManaPoolAttribute(parentCapability));
                return skillPool.get(SkillEnum.MANA_POOL);
            }
            case MANA_REGENERATION -> {
                if (!skillPool.containsKey(SkillEnum.MANA_REGENERATION))
                    skillPool.put(SkillEnum.MANA_REGENERATION, new PlayerPassiveManaRegenerationAttribute(parentCapability));
                return skillPool.get(SkillEnum.MANA_REGENERATION);
            }
            case CHARGE -> {
                if (!skillPool.containsKey(SkillEnum.CHARGE))
                    skillPool.put(SkillEnum.CHARGE, new SkillChargeWerewolf(parentCapability));
                return skillPool.get(SkillEnum.CHARGE);
            }
            case BLOCK -> {
                if (!skillPool.containsKey(SkillEnum.BLOCK))
                    skillPool.put(SkillEnum.BLOCK, new SkillBlockWerewolf(parentCapability));
                return skillPool.get(SkillEnum.BLOCK);
            }
            case KNOCKBACK_RESISTANCE -> {
                if (!skillPool.containsKey(SkillEnum.KNOCKBACK_RESISTANCE))
                    skillPool.put(SkillEnum.KNOCKBACK_RESISTANCE, new PlayerPassiveKnockBackResistance(parentCapability));
                return skillPool.get(SkillEnum.KNOCKBACK_RESISTANCE);
            }
        }
        return null;
    }

    public ISkillContainer getExistingSkill(SkillEnum skillEnum) {
        return (skillPool.containsKey(skillEnum) ? skillPool.get(skillEnum) : getOrCreate(skillEnum));
    }
}
