package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;
import java.util.Random;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.entity.FrostBall;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class SkillFrostBall extends AbstractSkillExecutor {

    public SkillFrostBall(PlayerSkillCapabilityInterface parentCapability) {
		super(parentCapability);
    }

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.FROSTBALL;
	}

	@Override
	public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
		return !player.getCooldowns().isOnCooldown(icon.getItem());
	}

	@Override
	public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
		if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
			if (!levelIn.isClientSide) {
				//Vec3d look = player.getLook(1.0F);
				//LivingEntity target = TargetUtils.acquireLookTarget(player, 20, 5, true);
				Random random = new Random();
				levelIn.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
				FrostBall snowballentity = new FrostBall(levelIn, player);
				snowballentity.updateLevel(player, SkillEnum.FROSTBALL);
				snowballentity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
				levelIn.addFreshEntity(snowballentity);
				player.getCooldowns().addCooldown(icon.getItem(), 20);
			}
		}
	}
}
