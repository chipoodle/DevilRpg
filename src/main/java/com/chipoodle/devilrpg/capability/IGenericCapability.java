package com.chipoodle.devilrpg.capability;

import net.minecraft.nbt.CompoundNBT;

public interface IGenericCapability {
	public CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
}
