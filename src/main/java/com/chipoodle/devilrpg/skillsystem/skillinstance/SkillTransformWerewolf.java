package com.chipoodle.devilrpg.skillsystem.skillinstance;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.config.DevilRpgConfig;
import com.chipoodle.devilrpg.entity.soulwolf.SoulWolfEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SkillTransformWerewolf implements ISkillContainer {
	private PlayerSkillCapability parentCapability;
	private boolean isActive;
	
	public SkillTransformWerewolf(PlayerSkillCapability parentCapability) {
		this.parentCapability = parentCapability;
	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.TRANSFORM_WEREWOLF;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn) {
		if (!worldIn.isRemote) {
			
		}
	}
	
	public boolean isActive() {
		return isActive;
	}
}
