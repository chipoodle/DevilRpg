package com.chipoodle.devilrpg.capability.experience;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerExperienceClientServerHandler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fml.network.PacketDistributor;

public class PlayerExperienceCapability implements IBaseExperienceCapability {

	protected int currentLevel = 0;
	protected int maximumLevel = 0;
	protected int unspentPoints = 0;

	@Override
	public int getCurrentLevel() {
		return currentLevel;
	}

	@Override
	public int getMaximumLevel() {
		return maximumLevel;
	}

	@Override
	public int getUnspentPoints() {
		return unspentPoints;
	}

	@Override
	public void setCurrentLevel(int currentLevel, PlayerEntity pe) {
		this.currentLevel = currentLevel;
		if(this.currentLevel > maximumLevel) {
			unspentPoints+= this.currentLevel-maximumLevel;
			maximumLevel = this.currentLevel;
		}	
		if(!pe.level.isClientSide)
			sendExperienceChangesToClient((ServerPlayerEntity) pe);
		else
			sendExperienceChangesToServer();
	}


	@Override
	public int consumePoint() {
		unspentPoints--;
		sendExperienceChangesToServer();
		return 1;
	}

	@Override
	public CompoundNBT getNBTData() {
		CompoundNBT nbt = new CompoundNBT();
		nbt.putInt("currentLevel", currentLevel);
		nbt.putInt("maximumLevel", maximumLevel);
		nbt.putInt("unspentPoints", unspentPoints);
		return nbt;
	}

	@Override
	public void setNBTData(CompoundNBT compound) {
		currentLevel = compound.getInt("currentLevel");
		maximumLevel = compound.getInt("maximumLevel");
		unspentPoints = compound.getInt("unspentPoints");
	}

	private void sendExperienceChangesToServer() {
		ModNetwork.CHANNEL.sendToServer(new PlayerExperienceClientServerHandler(getNBTData()));
	}
	
	private void sendExperienceChangesToClient(ServerPlayerEntity pe) {
		DevilRpg.LOGGER.info("----------> sendExperienceChangesToClient. unspentPoints: "+unspentPoints);
		ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->pe),new PlayerExperienceClientServerHandler(getNBTData()));
		
	}
}
