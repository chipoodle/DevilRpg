package com.chipoodle.devilrpg.capability.skill;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import java.util.HashMap;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.PacketDistributor;

public interface IBaseSkillCapability extends IGenericCapability{
		
	public HashMap<PowerEnum,SkillEnum> getSkillsNameOfPowers();
	public void setSkillsNameOfPowers(HashMap<PowerEnum,SkillEnum> names);
	public HashMap<SkillEnum,Integer> getSkillsPoints();
	public void setSkillsPoints(HashMap<SkillEnum,Integer> points);
	public HashMap<SkillEnum,Integer> getMaxSkillsPoints();
	public void setMaxSkillsPoints(HashMap<SkillEnum,Integer> points);
	
	public void triggerAction(ServerPlayerEntity playerIn, PowerEnum triggeredPower) ;
	
	public CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
}
