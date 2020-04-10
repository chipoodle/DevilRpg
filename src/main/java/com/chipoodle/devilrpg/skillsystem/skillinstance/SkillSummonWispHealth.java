package com.chipoodle.devilrpg.skillsystem.skillinstance;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.config.DevilRpgConfig;
import com.chipoodle.devilrpg.entity.wisp.WispEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SkillSummonWispHealth implements ISkillContainer {

	private final static int NUMBER_OF_SUMMONS = 1;
	private ConcurrentLinkedQueue<WispEntity> playerWispQueue = new ConcurrentLinkedQueue<>();
	private PlayerSkillCapability parentCapability;

	public SkillSummonWispHealth(PlayerSkillCapability parentCapability) {
		this.parentCapability = parentCapability;
	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.SUMMON_WISP_HEALTH;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn) {
		if (!worldIn.isRemote) {
			SkillSummonWispSpeed wispSkill = (SkillSummonWispSpeed) parentCapability.getLoadedSkill(SkillEnum.SUMMON_WISP_SPEED);
			if (wispSkill != null) {
				wispSkill.getPlayerWisp().forEach(x -> x.remove());
			}

			playerWispQueue.add(summonWisp(worldIn, playerIn));
			if (playerWispQueue.size() > NUMBER_OF_SUMMONS) {
				WispEntity w = playerWispQueue.remove();
				w.remove();
			}
		}
	}

	private WispEntity summonWisp(World worldIn, PlayerEntity playerIn) {
		Random rand = new Random();
		WispEntity sw = new WispEntity(ModEntityTypes.WISP.get(), worldIn);
		sw.updateLevel(playerIn, Effects.HEALTH_BOOST, Effects.REGENERATION, SkillEnum.SUMMON_WISP_HEALTH);
		Vec3d playerLookVector = playerIn.getLookVec();
		double spawnX = playerIn.getPosX() + DevilRpgConfig.WISP_SPAWN_DISTANCE * playerLookVector.x;
		double spawnZ = playerIn.getPosZ() + DevilRpgConfig.WISP_SPAWN_DISTANCE * playerLookVector.z;
		double spawnY = playerIn.getPosY() + DevilRpgConfig.WISP_SPAWN_DISTANCE * playerLookVector.y + 2;
		sw.setLocationAndAngles(spawnX, spawnY, spawnZ, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
		worldIn.addEntity(sw);
		return sw;
	}

	public ConcurrentLinkedQueue<WispEntity> getPlayerWisp() {
		return playerWispQueue;
	}
}
