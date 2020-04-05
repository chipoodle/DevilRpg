package com.chipoodle.devilrpg.skillsystem.skillinstance;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.entity.soulwolf.SoulWolfEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.Constantes;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SkillSummonSoulWolf implements ISkillContainer {

	private final static int NUMBER_OF_SUMMONS = 3;
	private ConcurrentLinkedQueue<SoulWolfEntity> playerWolves = new ConcurrentLinkedQueue<>();

	public SkillSummonSoulWolf() {

	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.SUMMON_SOUL_WOLF;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn) {
		if (!worldIn.isRemote) {
			playerWolves.add(summonWolf(worldIn, playerIn));
			if (playerWolves.size() > NUMBER_OF_SUMMONS) {
				SoulWolfEntity  w = playerWolves.remove();
				w.onKillCommand();
			}

		}
	}

	private SoulWolfEntity summonWolf(World worldIn, PlayerEntity playerIn) {
		LOGGER.info("------------------>Summon soulWolf");
		// ConcurrentLinkedQueue<EntitySoulBear> bearClear =
		// PowerSummonSoulBear.playerBear.get(playerIn);
		/*
		 * if (bearClear != null) { bearClear.forEach(x -> x.setDead());
		 * 
		 * }
		 */

		Random rand = new Random();
		SoulWolfEntity sw = new SoulWolfEntity(ModEntityTypes.SOUL_WOLF.get(),worldIn);
		sw.updateLevel(playerIn);
		Vec3d playerLookVector = playerIn.getLookVec();
		double spawnX = playerIn.getPosX() + Constantes.WOLF_SPAWN_DISTANCE * playerLookVector.x;
		double spawnZ = playerIn.getPosZ() + Constantes.WOLF_SPAWN_DISTANCE * playerLookVector.z;
		double spawnY = playerIn.getPosY() + Constantes.WOLF_SPAWN_DISTANCE * playerLookVector.y + 2;
		sw.setLocationAndAngles(spawnX, spawnY, spawnZ, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
		worldIn.addEntity(sw);
		System.out.println("Summoned soulWolf. is Client? " + worldIn.isRemote);
		return sw;
	}
}
