package com.chipoodle.devilrpg.capability.skill;

import java.util.concurrent.Callable;


public class PlayerSkillCapabilityFactory implements Callable<IBaseSkillCapability> {
	@Override
	public IBaseSkillCapability call() throws Exception {
		return new PlayerSkillCapability();
	}
}
