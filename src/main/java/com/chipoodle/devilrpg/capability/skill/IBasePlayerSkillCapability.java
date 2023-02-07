package com.chipoodle.devilrpg.capability.skill;

import java.util.HashMap;
import java.util.UUID;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientSkillBuilder;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public interface IBasePlayerSkillCapability extends IGenericCapability{
		
	HashMap<PowerEnum,SkillEnum> getSkillsNameOfPowers();
	void setSkillsNameOfPowers(HashMap<PowerEnum,SkillEnum> names,PlayerEntity player);
	HashMap<SkillEnum,Integer> getSkillsPoints();
	void setSkillsPoints(HashMap<SkillEnum,Integer> points,PlayerEntity player);
	HashMap<SkillEnum,Integer> getMaxSkillsPoints();
	void setMaxSkillsPoints(HashMap<SkillEnum,Integer> points,PlayerEntity player);
	HashMap<SkillEnum,Integer> getManaCostPoints();
	void setManaCostPoints(HashMap<SkillEnum,Integer> points,PlayerEntity player);
	HashMap<String, UUID> getAttributeModifiers();
	void setAttributeModifiers(HashMap<String, UUID> modifiers,PlayerEntity player);

	SkillEnum getSkillFromByteArray(CompoundNBT triggeredskill);
	CompoundNBT setSkillToByteArray(SkillEnum skillEnum);

	void triggerAction(ServerPlayerEntity sender, PowerEnum triggeredPower);
	void triggerPassive(ServerPlayerEntity sender, CompoundNBT triggeredPassive);
	ISkillContainer getLoadedSkill(SkillEnum skillEnum);
	ISkillContainer create(SkillEnum skillEnum);
	
	CompoundNBT getNBTData();
	void setNBTData(CompoundNBT nbt);

	ClientSkillBuilder getClientSkillBuilder();
}
