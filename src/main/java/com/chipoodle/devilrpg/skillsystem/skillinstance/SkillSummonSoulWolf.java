package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.player_minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.config.DevilRpgConfig;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;

public class SkillSummonSoulWolf implements ISkillContainer {

    private static final int NUMBER_OF_SUMMONS = 3;

    public SkillSummonSoulWolf(PlayerSkillCapability parentCapability) {
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SUMMON_SOUL_WOLF;
    }

    @Override
    public void execute(World worldIn, PlayerEntity playerIn, HashMap<String, String> parameters) {
        if (!worldIn.isClientSide) {
            Random rand = new Random();
            worldIn.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
                    SoundEvents.CHICKEN_EGG, SoundCategory.NEUTRAL, 0.5F,
                    0.4F / (rand.nextFloat() * 0.4F + 0.8F));
            LazyOptional<IBaseMinionCapability> min = playerIn.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);
            min.ifPresent(x -> x.removeAllSoulBear(playerIn));
            ConcurrentLinkedQueue<UUID> keys = min.map(x -> x.getSoulWolfMinions())
                    .orElse(new ConcurrentLinkedQueue<>());

            keys.offer(summonSoulWolf(worldIn, playerIn, rand).getUUID());
            if (keys.size() > NUMBER_OF_SUMMONS) {
                UUID key = keys.remove();
                min.ifPresent(x -> {
                    SoulWolfEntity e = (SoulWolfEntity) x.getTameableByUUID(key, playerIn.level);
                    if (e != null)
                        x.removeSoulWolf(playerIn, e);
                });
            }
            min.ifPresent(x -> x.setSoulWolfMinions(keys, playerIn));
        }
    }

    private SoulWolfEntity summonSoulWolf(World worldIn, PlayerEntity playerIn, Random rand) {
        SoulWolfEntity sw = ModEntityTypes.SOUL_WOLF.get().create(worldIn);
        sw.updateLevel(playerIn);
		/*Vector3d playerLookVector = playerIn.getLookAngle();
		double spawnX = playerIn.getX() + DevilRpgConfig.WOLF_SPAWN_DISTANCE * playerLookVector.x;
		double spawnZ = playerIn.getZ() + DevilRpgConfig.WOLF_SPAWN_DISTANCE * playerLookVector.z;
		double spawnY = playerIn.getY() + DevilRpgConfig.WOLF_SPAWN_DISTANCE * playerLookVector.y + 2;
		sw.moveTo(spawnX, spawnY, spawnZ, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);*/
        BlockRayTraceResult blockraytraceresult = TargetUtils.getPlayerBlockRayResult();
        BlockPos blockPos = blockraytraceresult.getBlockPos();

        if (!worldIn.isEmptyBlock(blockPos))
            blockPos = blockPos.above();

        sw.moveTo(blockPos, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
        worldIn.addFreshEntity(sw);
        return sw;
    }
}
