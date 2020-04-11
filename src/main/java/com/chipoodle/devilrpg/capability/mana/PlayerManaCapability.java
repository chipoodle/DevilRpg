package com.chipoodle.devilrpg.capability.mana;

import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fml.network.PacketDistributor;

public class PlayerManaCapability implements IBaseManaCapability {

	protected float mana = 0;
	protected float maxMana = 50;

	@Override
	public float getMana() {
		return mana;
	}

	@Override
	public void setMana(float mana, PlayerEntity player) {
		this.mana = mana;
		if (!player.world.isRemote)
			sendManaChangesToClient((ServerPlayerEntity) player);
		else
			sendManaChangesToServer();
	}

	@Override
	public float getMaxMana() {
		return maxMana;
	}

	@Override
	public void setMaxMana(float maxMana, PlayerEntity player) {
		this.maxMana = maxMana;
		if (!player.world.isRemote)
			sendManaChangesToClient((ServerPlayerEntity) player);
		else
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
	
	private void sendManaChangesToClient(ServerPlayerEntity pe) {
		ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
				new PlayerManaClientServerHandler(getNBTData()));
	}
}
