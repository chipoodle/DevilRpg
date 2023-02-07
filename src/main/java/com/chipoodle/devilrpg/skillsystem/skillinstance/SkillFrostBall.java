package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;
import java.util.Random;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.entity.SoulIceBallEntity;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;

public class SkillFrostBall implements ISkillContainer {

	private final PlayerSkillCapability parentCapability;

	public SkillFrostBall(PlayerSkillCapability parentCapability) {
		this.parentCapability = parentCapability;
	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.FROSTBALL;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn, HashMap<String, String> parameters) {
		if (!worldIn.isClientSide) {
			//Vec3d look = playerIn.getLook(1.0F);
			//LivingEntity target = TargetUtils.acquireLookTarget(playerIn, 20, 5, true);
			Random random = new Random();
			worldIn.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(), SoundEvents.SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
			SoulIceBallEntity snowballentity = new SoulIceBallEntity(worldIn, playerIn);
			snowballentity.updateLevel(playerIn,SkillEnum.FROSTBALL);
			snowballentity.shootFromRotation(playerIn, playerIn.xRot, playerIn.yRot, 0.0F, 1.5F, 1.0F);
	        worldIn.addFreshEntity(snowballentity);
		}
	}
}
