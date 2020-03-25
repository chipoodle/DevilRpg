package com.chipoodle.devilrpg.capability.skill;

import java.util.concurrent.Callable;


public class PlayerSkillCapabilityFactory implements Callable<IBaseSkillCapability> {
	@Override
	public PlayerSkillCapability call() throws Exception {
		return new PlayerSkillCapability();
	}
}
