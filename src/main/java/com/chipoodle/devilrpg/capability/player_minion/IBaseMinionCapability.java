package com.chipoodle.devilrpg.capability.player_minion;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.entity.ITameableEntity;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.World;

public interface IBaseMinionCapability extends IGenericCapability{
	ConcurrentLinkedQueue<UUID> getSoulWolfMinions();
	void setSoulWolfMinions(ConcurrentLinkedQueue<UUID> minions, PlayerEntity player);
	ConcurrentLinkedQueue<UUID> getSoulBearMinions();
	void setSoulBearMinions(ConcurrentLinkedQueue<UUID> minions, PlayerEntity player);
	ConcurrentLinkedQueue<UUID> getWispMinions();
	void setWispMinions(ConcurrentLinkedQueue<UUID> minions, PlayerEntity player);
	
	ConcurrentLinkedQueue<UUID> getAllMinions();
	
	ITameableEntity getTameableByUUID(UUID id, World world);
	
	void removeWisp(PlayerEntity owner, SoulWispEntity entity);
	void removeSoulWolf(PlayerEntity owner, SoulWolfEntity entity);
	void removeSoulBear(PlayerEntity owner, SoulBearEntity entity);
	
	void removeAllWisp(PlayerEntity owner);
	void removeAllSoulWolf(PlayerEntity owner);
	void removeAllSoulBear(PlayerEntity owner);
	
	CompoundNBT getNBTData();
	void setNBTData(CompoundNBT nbt);
	
}
