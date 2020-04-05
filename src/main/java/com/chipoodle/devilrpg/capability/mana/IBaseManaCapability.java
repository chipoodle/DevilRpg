package com.chipoodle.devilrpg.capability.mana;

import com.chipoodle.devilrpg.capability.IGenericCapability;

import net.minecraft.nbt.CompoundNBT;

public interface IBaseManaCapability extends IGenericCapability{
	public float getMana();
	public void setMana(float mana);
	public float getMaxMana();
	public void setMaxMana(float maxMana);
	void SetManaNoUpdate(float mana);
	CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
}
