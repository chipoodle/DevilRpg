package com.chipoodle.devilrpg.capability.minion;

import java.util.concurrent.Callable;


public class PlayerMinionCapabilityFactory implements Callable<IBaseMinionCapability> {
	@Override
	public IBaseMinionCapability call() throws Exception {
		return new PlayerMinionCapability();
	}
}
