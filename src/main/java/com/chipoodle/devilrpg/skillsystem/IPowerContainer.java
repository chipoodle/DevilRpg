package com.chipoodle.devilrpg.skillsystem;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public interface IPowerContainer {
	public abstract void execute(World worldIn, PlayerEntity playerIn);
}
