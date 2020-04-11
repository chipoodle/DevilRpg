package com.chipoodle.devilrpg.capability.mana;

import com.chipoodle.devilrpg.capability.IGenericCapability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public interface IBaseManaCapability extends IGenericCapability{
	public float getMana();
	public void setMana(float mana, PlayerEntity player);
	public float getMaxMana();
	public void setMaxMana(float maxMana, PlayerEntity player);
	void SetManaNoUpdate(float mana);
	CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
}
