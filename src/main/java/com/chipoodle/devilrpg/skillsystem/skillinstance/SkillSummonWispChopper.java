package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.entity.SoulWisp;
import com.chipoodle.devilrpg.entity.SoulWispChopper;
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

import java.util.HashMap;
import java.util.Objects;
import java.util.Random;

public class SkillSummonWispChopper extends AbstractSkillExecutor {

    public SkillSummonWispChopper(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SUMMON_WISP_CHOPPER;
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
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.5F, 0.4F / (rand.nextFloat() * 0.4F + 0.8F));

                PlayerSkillCapabilityInterface playerCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
                int maxSummons = playerCap.getSkillsPoints(SkillEnum.WISP_ARMY);

                PlayerMinionCapabilityInterface minionCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerMinionCapability.INSTANCE);
                minionCap.summonWispComplete(level, player, rand, () -> summonWisp(level, player, rand), maxSummons, SoulWispChopper.class);
            }
        }
        player.getCooldowns().addCooldown(icon.getItem(), 20);
    }

    private SoulWisp summonWisp(Level levelIn, Player playerIn, Random rand) {
        BlockHitResult playerBlockRayResult = TargetUtils.getPlayerBlockRayResult();
        BlockPos blockPos = playerBlockRayResult != null ? playerBlockRayResult.getBlockPos() : playerIn.blockPosition();
        if (!levelIn.isEmptyBlock(blockPos))
            blockPos = blockPos.above();

        SoulWispChopper sw = ModEntities.WISP_CHOPPER.get().create((ServerLevel) levelIn, null, blockPos, MobSpawnType.MOB_SUMMONED, true, true);
        Objects.requireNonNull(sw).updateLevel(playerIn);
        sw.moveTo(blockPos, Mth.wrapDegrees(rand.nextFloat() * 360.0F), 0.0F);
        levelIn.addFreshEntity(sw);
        return sw;
    }
}
