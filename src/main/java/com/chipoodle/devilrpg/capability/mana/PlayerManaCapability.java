package com.chipoodle.devilrpg.capability.mana;

import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fml.network.PacketDistributor;

public class PlayerManaCapability implements IBaseManaCapability {

	protected float mana = 0f;
	protected float maxMana = 50f;
	protected float regeneration = 0.10f;

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
	public float getRegeneration() {
		return regeneration;
	}

	@Override
	public void setRegeneration(float regeneration, PlayerEntity player) {
		this.regeneration = maxMana;
		if (!player.world.isRemote)
			sendManaChangesToClient((ServerPlayerEntity) player);
		else
			sendManaChangesToServer();
	}

	@Override
	public void onPlayerTickEventRegeneration(PlayerEntity player) {
		if (mana < maxMana) {
			mana += regeneration;
			setMana(mana > maxMana ? maxMana : mana, player);
		} else {
			mana = maxMana;
		}
	}

	@Override
	public CompoundNBT getNBTData() {
		CompoundNBT nbt = new CompoundNBT();
		nbt.putFloat("mana", mana);
		nbt.putFloat("maxMana", maxMana);
		nbt.putFloat("regeneration", regeneration);
		return nbt;
	}

	@Override
	public void setNBTData(CompoundNBT compound) {
		mana = compound.getFloat("mana");
		maxMana = compound.getFloat("maxMana");
		regeneration = compound.getFloat("regeneration");
	}

	private void sendManaChangesToServer() {
		ModNetwork.CHANNEL.sendToServer(new PlayerManaClientServerHandler(getNBTData()));
	}

	private void sendManaChangesToClient(ServerPlayerEntity pe) {
		ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
				new PlayerManaClientServerHandler(getNBTData()));
	}
}
