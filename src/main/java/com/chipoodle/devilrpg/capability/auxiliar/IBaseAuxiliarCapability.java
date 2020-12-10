package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.capability.IGenericCapability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Hand;

public interface IBaseAuxiliarCapability extends IGenericCapability{
	public boolean isWerewolfAttack();
	public void setWerewolfAttack(boolean active,PlayerEntity player);
	public boolean isWerewolfTransformation();
	public void setWerewolfTransformation(boolean active,PlayerEntity player);
	public boolean isSwingingMainHand();
	public void setSwingingMainHand(boolean active,PlayerEntity player);
	public Hand swingHands(PlayerEntity player);
	
	CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
	
}
