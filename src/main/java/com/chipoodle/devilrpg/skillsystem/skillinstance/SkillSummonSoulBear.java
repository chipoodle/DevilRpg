package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.entity.SoulBear;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
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

public class SkillSummonSoulBear extends AbstractSkillExecutor {

    private static final int NUMBER_OF_SUMMONS = 1;

    public SkillSummonSoulBear(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SUMMON_SOUL_BEAR;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        return !player.getCooldowns().isOnCooldown(icon.getItem());
    }

    @Override
    public void execute(Level level, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!level.isClientSide) {
                Random rand = new Random();
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F, 0.4F / (rand.nextFloat() * 0.4F + 0.8F));
                LazyOptional<PlayerMinionCapabilityInterface> min = player.getCapability(PlayerMinionCapability.INSTANCE);
                min.ifPresent(x -> x.removeAllSoulWolf(player));
                ConcurrentLinkedQueue<UUID> keys = min.map(PlayerMinionCapabilityInterface::getSoulBearMinions).orElse(new ConcurrentLinkedQueue<>());

                keys.offer(summonSoulBear(level, player, rand).getUUID());
                if (keys.size() > NUMBER_OF_SUMMONS) {
                    UUID key = keys.remove();
                    min.ifPresent(x -> {
                        SoulBear e = (SoulBear) x.getTameableByUUID(key, player.level);
                        if (e != null)
                            x.removeSoulBear(player, e);
                    });
                }
                min.ifPresent(x -> x.setSoulBearMinions(keys, player));
            }
            player.getCooldowns().addCooldown(icon.getItem(), 20);
        }
    }

    private SoulBear summonSoulBear(Level levelIn, Player playerIn, Random rand) {
        BlockHitResult playerBlockRayResult = TargetUtils.getPlayerBlockRayResult();
        BlockPos blockPos = playerBlockRayResult.getBlockPos();
        if (!levelIn.isEmptyBlock(blockPos))
            blockPos = blockPos.above();

        SoulBear sw = ModEntities.SOUL_BEAR.get().create((ServerLevel) levelIn, null, null, blockPos, MobSpawnType.MOB_SUMMONED, true, true);
        Objects.requireNonNull(sw).updateLevel(playerIn);
        sw.moveTo(blockPos, Mth.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
        levelIn.addFreshEntity(sw);
        return sw;
    }
}
