package com.chipoodle.devilrpg.capability.minion;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.World;

public interface IBaseMinionCapability extends IGenericCapability{
	public ConcurrentLinkedQueue<UUID> getSoulWolfMinions();
	public void setSoulWolfMinions(ConcurrentLinkedQueue<UUID> minions, PlayerEntity player);
	public ConcurrentLinkedQueue<UUID> getSoulBearMinions();
	public void setSoulBearMinions(ConcurrentLinkedQueue<UUID> minions, PlayerEntity player);
	public ConcurrentLinkedQueue<UUID> getWispMinions();
	public void setWispMinions(ConcurrentLinkedQueue<UUID> minions, PlayerEntity player);
	
	public ConcurrentLinkedQueue<UUID> getAllMinions();
	
	public ITamableEntity getTameableByUUID(UUID id, World world);
	
	public void removeWisp(PlayerEntity owner, SoulWispEntity entity);
	public void removeSoulWolf(PlayerEntity owner, SoulWolfEntity entity);
	public void removeSoulBear(PlayerEntity owner, SoulBearEntity entity);
	
	public void removeAllWisp(PlayerEntity owner);
	public void removeAllSoulWolf(PlayerEntity owner);
	public void removeAllSoulBear(PlayerEntity owner);
	
	public CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
	
}
