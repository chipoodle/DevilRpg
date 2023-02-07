package com.chipoodle.devilrpg.capability.tamable_minion;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.entity.ITameableEntity;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.World;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface IBaseTamableMinionCapability extends IGenericCapability{

	void applyPassives(ITameableEntity entity);
	

	
}
