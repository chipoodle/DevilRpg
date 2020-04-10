package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.capability.IGenericCapability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public interface IBaseAuxiliarCapability extends IGenericCapability{
	public boolean isWerewolfAttack();
	public void setWerewolfAttack(boolean active,PlayerEntity player);
	public boolean isWerewolfTransformation();
	public void setWerewolfTransformation(boolean active,PlayerEntity player);
	
	CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
	
}
