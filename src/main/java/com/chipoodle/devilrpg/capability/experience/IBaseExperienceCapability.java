package com.chipoodle.devilrpg.capability.experience;

import net.minecraft.nbt.CompoundNBT;

public interface IBaseExperienceCapability {
	public int getCurrentLevel();
	public int getMaximumLevel();
	public int getUnspentPoints();
	public void setCurrentLevel(int currentLevel);
	public int consumePoint();

	
	CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
}
