package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.entity.SoulWolf;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.LazyOptional;

import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SkillSummonSoulWolf implements ISkillContainer {

    private static final int NUMBER_OF_SUMMONS = 3;

    public SkillSummonSoulWolf(PlayerSkillCapabilityImplementation parentCapability) {
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SUMMON_SOUL_WOLF;
    }

    @Override
    public void execute(Level levelIn, Player playerIn, HashMap<String, String> parameters) {
        if (!levelIn.isClientSide) {
            Random rand = new Random();
            levelIn.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F, 0.4F / (rand.nextFloat() * 0.4F + 0.8F));
            LazyOptional<PlayerMinionCapabilityInterface> min = playerIn.getCapability(PlayerMinionCapability.INSTANCE);
            min.ifPresent(x -> x.removeAllSoulBear(playerIn));
            ConcurrentLinkedQueue<UUID> keys = min.map(PlayerMinionCapabilityInterface::getSoulWolfMinions).orElse(new ConcurrentLinkedQueue<>());

            keys.offer(summonSoulWolf(levelIn, playerIn, rand).getUUID());
            if (keys.size() > NUMBER_OF_SUMMONS) {
                UUID key = keys.remove();
                min.ifPresent(x -> {
                    SoulWolf e = (SoulWolf) x.getTameableByUUID(key, playerIn.level);
                    if (e != null)
                        x.removeSoulWolf(playerIn, e);
                });
            }
            min.ifPresent(x -> x.setSoulWolfMinions(keys, playerIn));
        }
    }

    private SoulWolf summonSoulWolf(Level levelIn, Player playerIn, Random rand) {
        BlockHitResult playerBlockRayResult = TargetUtils.getPlayerBlockRayResult();
        BlockPos blockPos = playerBlockRayResult.getBlockPos();
        if (!levelIn.isEmptyBlock(blockPos))
            blockPos = blockPos.above();

        SoulWolf sw = ModEntities.SOUL_WOLF.get().create((ServerLevel) levelIn, null, null, blockPos, MobSpawnType.MOB_SUMMONED, true, true);
        Objects.requireNonNull(sw).updateLevel(playerIn);
        sw.moveTo(blockPos, Mth.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
        levelIn.addFreshEntity(sw);
        return sw;
    }
}
