package com.chipoodle.devilrpg.capability.experience;

import java.util.concurrent.Callable;


public class PlayerExperienceCapabilityFactory implements Callable<IBaseExperienceCapability> {
	@Override
	public IBaseExperienceCapability call() throws Exception {
		return new PlayerExperienceCapability();
	}
}
