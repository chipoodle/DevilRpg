package com.chipoodle.devilrpg.skillsystem;

import java.util.Hashtable;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillSummonSoulWolf;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillSummonWispHealth;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillSummonWispSpeed;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillTransformWerewolf;
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
		if (skillEnum.equals(SkillEnum.SUMMON_WISP_HEALTH)) {
			skillPool.putIfAbsent(SkillEnum.SUMMON_WISP_HEALTH, new SkillSummonWispHealth(parentCapability));
			return skillPool.get(SkillEnum.SUMMON_WISP_HEALTH);
		}
		if (skillEnum.equals(SkillEnum.SUMMON_WISP_SPEED)) {
			skillPool.putIfAbsent(SkillEnum.SUMMON_WISP_SPEED, new SkillSummonWispSpeed(parentCapability));
			return skillPool.get(SkillEnum.SUMMON_WISP_SPEED);
		}
		if (skillEnum.equals(SkillEnum.TRANSFORM_WEREWOLF)) {
			skillPool.putIfAbsent(SkillEnum.TRANSFORM_WEREWOLF, new SkillTransformWerewolf(parentCapability));
			return skillPool.get(SkillEnum.TRANSFORM_WEREWOLF);
		}
		return null;
	}

	public ISkillContainer getExistingSkill(SkillEnum skillEnum) {
		return skillPool.get(skillEnum);
	}
}
