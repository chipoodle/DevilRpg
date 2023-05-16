package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;
import java.util.Random;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.entity.SoulIceBall;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class SkillFrostBall implements ISkillContainer {

    public SkillFrostBall(PlayerSkillCapabilityImplementation parentCapability) {
    }

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.FROSTBALL;
	}

	@Override
	public void execute(Level levelIn, Player playerIn, HashMap<String, String> parameters) {
		if (!levelIn.isClientSide) {
			//Vec3d look = playerIn.getLook(1.0F);
			//LivingEntity target = TargetUtils.acquireLookTarget(playerIn, 20, 5, true);
			Random random = new Random();
			levelIn.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
			SoulIceBall snowballentity = new SoulIceBall(levelIn, playerIn);
			snowballentity.updateLevel(playerIn,SkillEnum.FROSTBALL);
			snowballentity.shootFromRotation(playerIn, playerIn.getXRot(), playerIn.getYRot(), 0.0F, 1.5F, 1.0F);
	        levelIn.addFreshEntity(snowballentity);
		}
	}
}
