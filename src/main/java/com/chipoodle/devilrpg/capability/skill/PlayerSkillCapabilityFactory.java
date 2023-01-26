package com.chipoodle.devilrpg.capability.skill;

import java.util.concurrent.Callable;


public class PlayerSkillCapabilityFactory implements Callable<IBasePlayerSkillCapability> {
	@Override
	public IBasePlayerSkillCapability call() throws Exception {
		return new PlayerSkillCapability();
	}
}
