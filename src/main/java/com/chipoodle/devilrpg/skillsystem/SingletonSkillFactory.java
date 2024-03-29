package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.skillsystem.skillinstance.*;
import com.chipoodle.devilrpg.util.SkillEnum;

import java.util.Hashtable;

public class SingletonSkillFactory {

    private final Hashtable<SkillEnum, ISkillContainer> skillPool = new Hashtable<>();
    private final PlayerSkillCapability parentCapability;

    public SingletonSkillFactory(PlayerSkillCapability parentCapability) {
        this.parentCapability = parentCapability;
        //DevilRpg.LOGGER.info("--------> SingletonSkillFactory. capability hash: "+parentCapability.hashCode());
    }

    public ISkillContainer create(SkillEnum skillEnum) {

        if (skillEnum.equals(SkillEnum.SUMMON_SOUL_WOLF)) {
            skillPool.putIfAbsent(SkillEnum.SUMMON_SOUL_WOLF, new SkillSummonSoulWolf(parentCapability));
            return skillPool.get(SkillEnum.SUMMON_SOUL_WOLF);
        }
        if (skillEnum.equals(SkillEnum.SUMMON_SOUL_BEAR)) {
            skillPool.putIfAbsent(SkillEnum.SUMMON_SOUL_BEAR, new SkillSummonSoulBear(parentCapability));
            return skillPool.get(SkillEnum.SUMMON_SOUL_BEAR);
        }
        if (skillEnum.equals(SkillEnum.SUMMON_WISP_HEALTH)) {
            skillPool.putIfAbsent(SkillEnum.SUMMON_WISP_HEALTH, new SkillSummonWispHealth(parentCapability));
            return skillPool.get(SkillEnum.SUMMON_WISP_HEALTH);
        }
        if (skillEnum.equals(SkillEnum.SUMMON_WISP_CURSE)) {
            skillPool.putIfAbsent(SkillEnum.SUMMON_WISP_CURSE, new SkillSummonWispCurse(parentCapability));
            return skillPool.get(SkillEnum.SUMMON_WISP_CURSE);
        }
        if (skillEnum.equals(SkillEnum.SUMMON_WISP_BOMB)) {
            skillPool.putIfAbsent(SkillEnum.SUMMON_WISP_BOMB, new SkillSummonWispBomber(parentCapability));
            return skillPool.get(SkillEnum.SUMMON_WISP_BOMB);
        }
        if (skillEnum.equals(SkillEnum.SUMMON_WISP_ARCHER)) {
            skillPool.putIfAbsent(SkillEnum.SUMMON_WISP_ARCHER, new SkillSummonWispArcher(parentCapability));
            return skillPool.get(SkillEnum.SUMMON_WISP_ARCHER);
        }
        if (skillEnum.equals(SkillEnum.TRANSFORM_WEREWOLF)) {
            skillPool.putIfAbsent(SkillEnum.TRANSFORM_WEREWOLF, new SkillShapeshiftWerewolf(parentCapability));
            return skillPool.get(SkillEnum.TRANSFORM_WEREWOLF);
        }
        if (skillEnum.equals(SkillEnum.FROSTBALL)) {
            skillPool.putIfAbsent(SkillEnum.FROSTBALL, new SkillFrostBall(parentCapability));
            return skillPool.get(SkillEnum.FROSTBALL);
        }
        if (skillEnum.equals(SkillEnum.SKIN_ARMOR)) {
            skillPool.putIfAbsent(SkillEnum.SKIN_ARMOR, new PlayerPassiveSkillSkinArmor(parentCapability));
            return skillPool.get(SkillEnum.SKIN_ARMOR);
        }
        if (skillEnum.equals(SkillEnum.WEREWOLF_HIT)) {
            skillPool.putIfAbsent(SkillEnum.WEREWOLF_HIT, new PlayerPassiveWerewolfHit(parentCapability));
            return skillPool.get(SkillEnum.WEREWOLF_HIT);
        }
        if (skillEnum.equals(SkillEnum.WEREWOLF_VITALITY)) {
            skillPool.putIfAbsent(SkillEnum.WEREWOLF_VITALITY, new PlayerPassiveWerewolfVitality(parentCapability));
            return skillPool.get(SkillEnum.WEREWOLF_VITALITY);
        }
        if (skillEnum.equals(SkillEnum.SOULVINE)) {
            skillPool.putIfAbsent(SkillEnum.SOULVINE, new SkillSoulVine(parentCapability));
            return skillPool.get(SkillEnum.SOULVINE);
        }

        return null;
    }

    public ISkillContainer getExistingSkill(SkillEnum skillEnum) {
        return (skillPool.containsKey(skillEnum) ? skillPool.get(skillEnum) : create(skillEnum));
    }
}
