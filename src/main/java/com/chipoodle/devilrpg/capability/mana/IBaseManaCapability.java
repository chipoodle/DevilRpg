package com.chipoodle.devilrpg.capability.mana;

import com.chipoodle.devilrpg.capability.IGenericCapability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public interface IBaseManaCapability extends IGenericCapability{
	float getMana();
	void setMana(float mana, PlayerEntity player);
	float getMaxMana();
	void setMaxMana(float maxMana, PlayerEntity player);
	float getRegeneration();
	void setRegeneration(float regeneration, PlayerEntity player);

	void onPlayerTickEventRegeneration(PlayerEntity player);

	CompoundNBT getNBTData();
	void setNBTData(CompoundNBT nbt);
}
