package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

import java.util.HashMap;

public interface ISkillContainer{
	public void execute(World worldIn, PlayerEntity playerIn, HashMap<String,String> parameters);
	public SkillEnum getSkillEnum();
}
