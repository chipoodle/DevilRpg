package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.Random;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.entity.SoulFireBallEntity;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;

public class SkillFireBall implements ISkillContainer {

	private PlayerSkillCapability parentCapability;

	public SkillFireBall(PlayerSkillCapability parentCapability) {
		this.parentCapability = parentCapability;
	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.FIREBALL;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn) {
		if (!worldIn.isClientSide) {
			//Vec3d look = playerIn.getLook(1.0F);
			//LivingEntity target = TargetUtils.acquireLookTarget(playerIn, 20, 5, true);
			Random random = new Random();
			worldIn.playSound((PlayerEntity)null, playerIn.getX(), playerIn.getY(), playerIn.getZ(), SoundEvents.SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
			SoulFireBallEntity snowballentity = new SoulFireBallEntity(worldIn, playerIn);
			snowballentity.updateLevel(playerIn);
			snowballentity.shootFromRotation(playerIn, playerIn.xRot, playerIn.yRot, 0.0F, 1.5F, 1.0F);
	        worldIn.addFreshEntity(snowballentity);
		}
	}
}
