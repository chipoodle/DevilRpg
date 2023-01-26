package com.chipoodle.devilrpg.capability.tamable_minion;

import java.util.concurrent.Callable;


public class TamableMinionCapabilityFactory implements Callable<IBaseTamableMinionCapability> {
	@Override
	public IBaseTamableMinionCapability call() throws Exception {
		return new TamableMinionCapability();
	}
}
