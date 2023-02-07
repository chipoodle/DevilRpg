package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.player_minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.config.DevilRpgConfig;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;

public class SkillSummonSoulBear implements ISkillContainer {

    private static final int NUMBER_OF_SUMMONS = 1;

    public SkillSummonSoulBear(PlayerSkillCapability parentCapability) {
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SUMMON_SOUL_BEAR;
    }

    @Override
    public void execute(World worldIn, PlayerEntity playerIn, HashMap<String, String> parameters) {
        if (!worldIn.isClientSide) {
            Random rand = new Random();
            worldIn.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
                    SoundEvents.CHICKEN_EGG, SoundCategory.NEUTRAL, 0.5F,
                    0.4F / (rand.nextFloat() * 0.4F + 0.8F));
            LazyOptional<IBaseMinionCapability> min = playerIn.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);
            min.ifPresent(x -> x.removeAllSoulWolf(playerIn));
            ConcurrentLinkedQueue<UUID> keys = min.map(x -> x.getSoulBearMinions())
                    .orElse(new ConcurrentLinkedQueue<UUID>());

            keys.offer(summonSoulBear(worldIn, playerIn, rand).getUUID());
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
        BlockRayTraceResult blockraytraceresult = TargetUtils.getPlayerBlockRayResult();
        BlockPos blockPos = blockraytraceresult.getBlockPos();
        if (!worldIn.isEmptyBlock(blockPos))
        	blockPos = blockPos.above();
        sw.moveTo(blockPos, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
        worldIn.addFreshEntity(sw);
        return sw;
    }
}
