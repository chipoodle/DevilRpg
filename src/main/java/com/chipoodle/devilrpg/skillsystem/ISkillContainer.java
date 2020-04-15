package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public interface ISkillContainer{
	public void execute(World worldIn, PlayerEntity playerIn);
	public SkillEnum getSkillEnum();
}
