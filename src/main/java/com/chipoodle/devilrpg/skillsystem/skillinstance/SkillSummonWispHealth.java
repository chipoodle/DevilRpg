package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityAttacher;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effects;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;

public class SkillSummonWispHealth implements ISkillContainer, WispSkillInterface {

    public SkillSummonWispHealth(PlayerSkillCapabilityImplementation parentCapability) {
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SUMMON_WISP_HEALTH;
    }

    @Override
    public void execute(World worldIn, PlayerEntity playerIn, HashMap<String, String> parameters) {
        if (!worldIn.isClientSide) {
            Random rand = new Random();
            worldIn.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundCategory.NEUTRAL, 0.5F,
                    0.4F / (rand.nextFloat() * 0.4F + 0.8F));
            LazyOptional<PlayerMinionCapabilityInterface> min = playerIn.getCapability(PlayerMinionCapabilityAttacher.MINION_CAP);
            ConcurrentLinkedQueue<UUID> keys = min.map(x -> x.getWispMinions())
                    .orElse(new ConcurrentLinkedQueue<UUID>());

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
        SoulWispEntity sw = ModEntityTypes.WISP.get().create(worldIn);
        sw.updateLevel(playerIn, Effects.HEALTH_BOOST, Effects.REGENERATION, SkillEnum.SUMMON_WISP_HEALTH, true);
        BlockRayTraceResult blockraytraceresult = TargetUtils.getPlayerBlockRayResult();
        BlockPos blockPos = blockraytraceresult.getBlockPos();
        if (!worldIn.isEmptyBlock(blockPos))
            blockPos = blockPos.above();
        sw.moveTo(blockPos, MathHelper.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
        worldIn.addFreshEntity(sw);
        return sw;
    }
}
