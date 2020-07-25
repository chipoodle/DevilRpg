package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.config.DevilRpgConfig;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;

public class SkillSummonSoulWolf implements ISkillContainer {

	private final static int NUMBER_OF_SUMMONS = 3;
	private PlayerSkillCapability parentCapability;

	public SkillSummonSoulWolf(PlayerSkillCapability parentCapability) {
		this.parentCapability = parentCapability;
	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.SUMMON_SOUL_WOLF;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn) {
		if (!worldIn.isRemote) {
			LazyOptional<IBaseMinionCapability> min = playerIn.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);
			min.ifPresent(x->x.removeAllSoulBear(playerIn));
			ConcurrentLinkedQueue<UUID> keys = min.map(x -> x.getSoulWolfMinions())
					.orElse(new ConcurrentLinkedQueue<UUID>());

			keys.add(summoSoulWolf(worldIn, playerIn).getUniqueID());
			if (keys.size() > NUMBER_OF_SUMMONS) {
				UUID key = keys.remove();
				min.ifPresent(x -> {
					SoulWolfEntity e = (SoulWolfEntity) x.getTameableByUUID(key, playerIn.world);
					if (e != null)
						x.removeSoulWolf(playerIn, e);
				});
			}
			min.ifPresent(x -> x.setSoulWolfMinions(keys, playerIn));
		}
	}

	private SoulWolfEntity summoSoulWolf(World worldIn, PlayerEntity playerIn) {
		Random rand = new Random();
		SoulWolfEntity sw = ModEntityTypes.SOUL_WOLF.get().create(worldIn);
		sw.updateLevel(playerIn);
		Vec3d playerLookVector = playerIn.getLookVec();
		double spawnX = playerIn.getPosX() + DevilRpgConfig.WOLF_SPAWN_DISTANCE * playerLookVector.x;
		double spawnZ = playerIn.getPosZ() + DevilRpgConfig.WOLF_SPAWN_DISTANCE * playerLookVector.z;
		double spawnY = playerIn.getPosY() + DevilRpgConfig.WOLF_SPAWN_DISTANCE * playerLookVector.y + 2;
		sw.setLocationAndAngles(spawnX, spawnY, spawnZ, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
		worldIn.addEntity(sw);
		return sw;
	}
}
