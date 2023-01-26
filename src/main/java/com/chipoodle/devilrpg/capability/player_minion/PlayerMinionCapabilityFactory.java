package com.chipoodle.devilrpg.capability.player_minion;

import java.util.concurrent.Callable;


public class PlayerMinionCapabilityFactory implements Callable<IBaseMinionCapability> {
	@Override
	public IBaseMinionCapability call() throws Exception {
		return new PlayerMinionCapability();
	}
}
