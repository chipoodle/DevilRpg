package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.capability.IGenericCapability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Hand;

public interface IBaseAuxiliarCapability extends IGenericCapability{
	boolean isWerewolfAttack();
	void setWerewolfAttack(boolean active,PlayerEntity player);
	boolean isWerewolfTransformation();
	void setWerewolfTransformation(boolean active,PlayerEntity player);
	boolean isSwingingMainHand();
	void setSwingingMainHand(boolean active,PlayerEntity player);
	Hand swingHands(PlayerEntity player);
	
	CompoundNBT getNBTData();
	void setNBTData(CompoundNBT nbt);
	
}
