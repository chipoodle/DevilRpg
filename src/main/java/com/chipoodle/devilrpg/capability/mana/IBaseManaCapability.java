package com.chipoodle.devilrpg.capability.mana;

import com.chipoodle.devilrpg.capability.IGenericCapability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public interface IBaseManaCapability extends IGenericCapability{
	public float getMana();
	public void setMana(float mana, PlayerEntity player);
	public float getMaxMana();
	public void setMaxMana(float maxMana, PlayerEntity player);
	public float getRegeneration();
	public void setRegeneration(float regeneration, PlayerEntity player);

	public void onPlayerTickEventRegeneration(PlayerEntity player);

	CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
}
