package com.chipoodle.devilrpg.skillsystem;

import java.util.Hashtable;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillSummonSoulWolf;
import com.chipoodle.devilrpg.util.SkillEnum;

public class SingletonSkillFactory {

	private Hashtable<SkillEnum, ISkillContainer> skills = new Hashtable<>();

	public ISkillContainer create(SkillEnum skillEnum) {

		if (skillEnum.equals(SkillEnum.SUMMON_SOUL_WOLF)) {
			if (!skills.containsKey(SkillEnum.SUMMON_SOUL_WOLF)) {
				skills.put(SkillEnum.SUMMON_SOUL_WOLF, new SkillSummonSoulWolf());
			}
			return skills.get(SkillEnum.SUMMON_SOUL_WOLF);
		}
		return null;
	}
}
