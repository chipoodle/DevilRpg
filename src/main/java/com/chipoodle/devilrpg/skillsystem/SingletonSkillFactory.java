package com.chipoodle.devilrpg.skillsystem;

import java.util.Hashtable;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillFireBall;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillShapeshiftWerewolf;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillSummonSoulBear;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillSummonSoulWolf;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillSummonWispCurse;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillSummonWispHealth;
import com.chipoodle.devilrpg.util.SkillEnum;

public class SingletonSkillFactory {

	private final Hashtable<SkillEnum, ISkillContainer> skillPool = new Hashtable<>();
	private PlayerSkillCapability parentCapability;

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
		if (skillEnum.equals(SkillEnum.TRANSFORM_WEREWOLF)) {
			skillPool.putIfAbsent(SkillEnum.TRANSFORM_WEREWOLF, new SkillShapeshiftWerewolf(parentCapability));
			return skillPool.get(SkillEnum.TRANSFORM_WEREWOLF);
		}
		if (skillEnum.equals(SkillEnum.FROSTBALL)) {
			skillPool.putIfAbsent(SkillEnum.FROSTBALL, new SkillFireBall(parentCapability));
			return skillPool.get(SkillEnum.FROSTBALL);
		}
		return null;
	}

	public ISkillContainer getExistingSkill(SkillEnum skillEnum) {
		return skillPool.get(skillEnum);
	}
}
