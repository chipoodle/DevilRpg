package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public interface ISkillContainer{
	void execute(Level level, Player playerIn, HashMap<String,String> parameters);
	SkillEnum getSkillEnum();
}
