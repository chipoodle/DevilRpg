package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.config.DevilRpgConfig;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;

public class SkillSummonWispHealth implements ISkillContainer {

	private final static int NUMBER_OF_SUMMONS = 1;
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
			LazyOptional<IBaseMinionCapability> min = playerIn.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);
			ConcurrentLinkedQueue<UUID> keys = min.map(x -> x.getWispMinions())
					.orElse(new ConcurrentLinkedQueue<UUID>());

			keys.add(summonWisp(worldIn, playerIn).getUniqueID());
			if (keys.size() > NUMBER_OF_SUMMONS) {
				UUID key = keys.remove();
				min.ifPresent(x -> {
					SoulWispEntity e = (SoulWispEntity) x.getTameableByUUID(key, playerIn.world);
					if (e != null)
						x.removeWisp(playerIn, e);
				});
			}
			min.ifPresent(x -> x.setWispMinions(keys, playerIn));
		}
	}

	private SoulWispEntity summonWisp(World worldIn, PlayerEntity playerIn) {
		Random rand = new Random();
		SoulWispEntity sw = ModEntityTypes.WISP.get().create(worldIn);
		sw.updateLevel(playerIn, Effects.HEALTH_BOOST, Effects.REGENERATION, SkillEnum.SUMMON_WISP_HEALTH,true);
		Vec3d playerLookVector = playerIn.getLookVec();
		double spawnX = playerIn.getPosX() + DevilRpgConfig.WISP_SPAWN_DISTANCE * playerLookVector.x;
		double spawnZ = playerIn.getPosZ() + DevilRpgConfig.WISP_SPAWN_DISTANCE * playerLookVector.z;
		double spawnY = playerIn.getPosY() + DevilRpgConfig.WISP_SPAWN_DISTANCE * playerLookVector.y + 2;
		sw.setLocationAndAngles(spawnX, spawnY, spawnZ, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
		worldIn.addEntity(sw);
		return sw;
	}
}
