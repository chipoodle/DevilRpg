package com.chipoodle.devilrpg.capability.experience;

import com.chipoodle.devilrpg.capability.IGenericCapability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public interface IBaseExperienceCapability extends IGenericCapability{
	public int getCurrentLevel();
	public int getMaximumLevel();
	public int getUnspentPoints();
	public void setCurrentLevel(int currentLevel, PlayerEntity pe);
	public int consumePoint();

	
	CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
}
