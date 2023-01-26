package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.player_minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.config.DevilRpgConfig;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
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

public class SkillSummonWispArcher implements ISkillContainer, WispSkillInterface {

	private PlayerSkillCapability parentCapability;

	public SkillSummonWispArcher(PlayerSkillCapability parentCapability) {
		this.parentCapability = parentCapability;
	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.SUMMON_WISP_ARCHER;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn, HashMap<String, String> parameters) {
		if (!worldIn.isClientSide) {
			Random rand = new Random();
			worldIn.playSound((PlayerEntity) null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
					SoundEvents.BEACON_ACTIVATE, SoundCategory.NEUTRAL, 0.5F,
					0.4F / (rand.nextFloat() * 0.4F + 0.8F));
			LazyOptional<IBaseMinionCapability> min = playerIn.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);
			ConcurrentLinkedQueue<UUID> keys = min.map(x -> x.getWispMinions())
					.orElse(new ConcurrentLinkedQueue<>());

			keys.offer(summonWisp(worldIn, playerIn, rand).getUUID());
			if (keys.size() > NUMBER_OF_SUMMONS) {
				UUID key = keys.remove();
				min.ifPresent(x -> {
					SoulWispEntity e = (SoulWispEntity) x.getTameableByUUID(key, playerIn.level);
					if (e != null)
						x.removeWisp(playerIn, e);
				});
			}
			min.ifPresent(x -> x.setWispMinions(keys, playerIn));
		}
	}

	private SoulWispEntity summonWisp(World worldIn, PlayerEntity playerIn, Random rand) {
		SoulWispEntity sw = ModEntityTypes.WISP_ARCHER.get().create(worldIn);
		sw.updateLevel(playerIn, null, null, SkillEnum.SUMMON_WISP_ARCHER, true);
		Vector3d playerLookVector = playerIn.getLookAngle();
		double spawnX = playerIn.getX() + DevilRpgConfig.WISP_SPAWN_DISTANCE * playerLookVector.x;
		double spawnZ = playerIn.getZ() + DevilRpgConfig.WISP_SPAWN_DISTANCE * playerLookVector.z;
		double spawnY = playerIn.getY() + DevilRpgConfig.WISP_SPAWN_DISTANCE * playerLookVector.y + 2;
		sw.moveTo(spawnX, spawnY, spawnZ, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
		worldIn.addFreshEntity(sw);
		return sw;
	}
}
