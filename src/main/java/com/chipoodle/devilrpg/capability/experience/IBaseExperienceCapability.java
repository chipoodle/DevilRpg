package com.chipoodle.devilrpg.capability.experience;

import com.chipoodle.devilrpg.capability.IGenericCapability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public interface IBaseExperienceCapability extends IGenericCapability{
	int getCurrentLevel();
	int getMaximumLevel();
	int getUnspentPoints();
	void setCurrentLevel(int currentLevel, PlayerEntity pe);
	int consumePoint();

	
	CompoundNBT getNBTData();
	void setNBTData(CompoundNBT nbt);
}
