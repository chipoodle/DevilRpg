package com.chipoodle.devilrpg.capability.mana;

import java.util.concurrent.Callable;


public class PlayerManaCapabilityFactory implements Callable<IBaseManaCapability> {
	@Override
	public IBaseManaCapability call() throws Exception {
		return new PlayerManaCapability();
	}
}
