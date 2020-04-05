package com.chipoodle.devilrpg.capability.mana;

import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;

import net.minecraft.nbt.CompoundNBT;

public class PlayerManaCapability implements IBaseManaCapability {

	protected float mana = 0;
	protected float maxMana = 50;

	@Override
	public float getMana() {
		return mana;
	}

	@Override
	public void setMana(float mana) {
		this.mana = mana;
		sendManaChangesToServer();
	}

	@Override
	public float getMaxMana() {
		return maxMana;
	}

	@Override
	public void setMaxMana(float maxMana) {
		this.maxMana = maxMana;
		sendManaChangesToServer();
	}

	@Override
	public void SetManaNoUpdate(float mana) {
		this.mana = mana;
	}

	@Override
	public CompoundNBT getNBTData() {
		CompoundNBT nbt = new CompoundNBT();
		nbt.putFloat("mana", mana);
		nbt.putFloat("maxMana", maxMana);
		return nbt;
	}

	@Override
	public void setNBTData(CompoundNBT compound) {
		mana = compound.getFloat("mana");
		maxMana = compound.getFloat("maxMana");
	}

	private void sendManaChangesToServer() {
		ModNetwork.CHANNEL.sendToServer(new PlayerManaClientServerHandler(getNBTData()));
	}
}
