package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.config.DevilRpgConfig;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;

public class SkillSummonSoulBear implements ISkillContainer {

	private final static int NUMBER_OF_SUMMONS = 1;
	private PlayerSkillCapability parentCapability;

	public SkillSummonSoulBear(PlayerSkillCapability parentCapability) {
		this.parentCapability = parentCapability;
	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.SUMMON_SOUL_BEAR;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn) {
		if (!worldIn.isClientSide) {
			Random rand = new Random();
			worldIn.playSound((PlayerEntity) null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
					SoundEvents.CHICKEN_EGG, SoundCategory.NEUTRAL, 0.5F,
					0.4F / (rand.nextFloat() * 0.4F + 0.8F));
			LazyOptional<IBaseMinionCapability> min = playerIn.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);
			min.ifPresent(x -> x.removeAllSoulWolf(playerIn));
			ConcurrentLinkedQueue<UUID> keys = min.map(x -> x.getSoulBearMinions())
					.orElse(new ConcurrentLinkedQueue<UUID>());

			keys.add(summonSoulBear(worldIn, playerIn, rand).getUUID());
			if (keys.size() > NUMBER_OF_SUMMONS) {
				UUID key = keys.remove();
				min.ifPresent(x -> {
					SoulBearEntity e = (SoulBearEntity) x.getTameableByUUID(key, playerIn.level);
					if (e != null)
						x.removeSoulBear(playerIn, e);
				});
			}
			min.ifPresent(x -> x.setSoulBearMinions(keys, playerIn));
		}
	}

	private SoulBearEntity summonSoulBear(World worldIn, PlayerEntity playerIn, Random rand) {
		SoulBearEntity sw = ModEntityTypes.SOUL_BEAR.get().create(worldIn);
		sw.updateLevel(playerIn);
		Vector3d playerLookVector = playerIn.getLookAngle();
		double spawnX = playerIn.getX() + DevilRpgConfig.WOLF_SPAWN_DISTANCE * playerLookVector.x;
		double spawnZ = playerIn.getZ() + DevilRpgConfig.WOLF_SPAWN_DISTANCE * playerLookVector.z;
		double spawnY = playerIn.getY() + DevilRpgConfig.WOLF_SPAWN_DISTANCE * playerLookVector.y + 2;
		sw.moveTo(spawnX, spawnY, spawnZ, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
		worldIn.addFreshEntity(sw);
		return sw;
	}
}
