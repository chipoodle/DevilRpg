package com.chipoodle.devilrpg.capability.skill;

import java.util.HashMap;
import java.util.UUID;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public interface IBaseSkillCapability extends IGenericCapability{
		
	public HashMap<PowerEnum,SkillEnum> getSkillsNameOfPowers();
	public void setSkillsNameOfPowers(HashMap<PowerEnum,SkillEnum> names,PlayerEntity player);
	public HashMap<SkillEnum,Integer> getSkillsPoints();
	public void setSkillsPoints(HashMap<SkillEnum,Integer> points,PlayerEntity player);
	public HashMap<SkillEnum,Integer> getMaxSkillsPoints();
	public void setMaxSkillsPoints(HashMap<SkillEnum,Integer> points,PlayerEntity player);
	public HashMap<SkillEnum,Integer> getManaCostPoints();
	public void setManaCostPoints(HashMap<SkillEnum,Integer> points,PlayerEntity player);
	public HashMap<String, UUID> getAttributeModifiers();
	public void setAttributeModifiers(HashMap<String, UUID> modifiers,PlayerEntity player);
	
	public void triggerAction(ServerPlayerEntity playerIn, PowerEnum triggeredPower);
	public ISkillContainer getLoadedSkill(SkillEnum skillEnum);
	public ISkillContainer create(SkillEnum skillEnum);
	
	public CompoundNBT getNBTData();
	public void setNBTData(CompoundNBT nbt);
}
