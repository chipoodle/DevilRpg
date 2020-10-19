package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.entity.SoulFireBallEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.Vec3d;
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
		if (!worldIn.isRemote) {
			Vec3d look = playerIn.getLook(1.0F);

			LivingEntity target = TargetUtils.acquireLookTarget(playerIn, 20, 5, true);
			// ShulkerBulletEntity bulletEntity =
			// EntityType.SHULKER_BULLET.create(playerIn.world);
			// ShulkerBulletEntity bulletEntity = new ShulkerBulletEntity(playerIn.world,
			// playerIn, target,Axis.X);

			// SmallFireballEntity fireball = new SmallFireballEntity(worldIn, playerIn, 1,
			// 1, 1);

			/*
			 * SoulBoulderEntity fireball =
			 * ModEntityTypes.SOUL_BOULDER.get().create(worldIn);
			 * fireball.setProperties(playerIn.getPosX() + look.x * 2, playerIn.getPosY() +
			 * look.y * 2 + 1.1, playerIn.getPosZ() + look.z * 2, 1, 1, 1);
			 */

			// SoulBoulderEntity fireball = new SoulBoulderEntity(worldIn, playerIn, 1, 1,
			// 1);
			// worldIn.addEntity(bulletEntity);

			SoulFireBallEntity fireball = new SoulFireBallEntity(worldIn, playerIn, 1, 1, 1);
			fireball.updateLevel(playerIn);

			fireball.setPosition(playerIn.getPosX() + look.x * 2, playerIn.getPosY() + look.y * 2+ 1.1,
					playerIn.getPosZ() + look.z * 2);
			fireball.accelerationX = look.x * 0.1;
			fireball.accelerationY = look.y * 0.1;
			fireball.accelerationZ = look.z * 0.1;

			worldIn.addEntity(fireball);
			fireball.playSound(SoundEvents.ENTITY_SHULKER_BULLET_HIT, 1.0F, 1.0F);
		}
	}
}
