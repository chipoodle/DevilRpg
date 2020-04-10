package com.chipoodle.devilrpg.capability.auxiliar;

import java.util.concurrent.Callable;


public class PlayerAuxiliarCapabilityFactory implements Callable<IBaseAuxiliarCapability> {
	@Override
	public IBaseAuxiliarCapability call() throws Exception {
		return new PlayerAuxiliarCapability();
	}
}
