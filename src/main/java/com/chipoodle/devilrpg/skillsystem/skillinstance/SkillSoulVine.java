package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.block.SoulVineBlock;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Random;

public class SkillSoulVine extends AbstractSkillExecutor {

    public SkillSoulVine(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SOULVINE;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        BlockPos playerBlockPos = player.blockPosition();
        BlockState playerBlockState = player.level.getBlockState(playerBlockPos);
        Vec3 playerLookVector = player.getLookAngle();
        Direction nearestDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        //DevilRpg.LOGGER.info("-------->Direction: {}", nearestDirection);
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);
        boolean canPlace = playerBlockState.getBlock().equals(Blocks.AIR)
                && player.level.getBlockState(newBlockpos).getBlock().equals(Blocks.AIR)
                && SoulVineBlock.hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(player.level,newBlockpos,nearestDirection);
        return !player.getCooldowns().isOnCooldown(icon.getItem()) && canPlace;
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!levelIn.isClientSide) {
                Random rand = new Random();
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F,
                        0.4F / (rand.nextFloat() * 0.4F + 0.8F));

                PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                        PlayerSkillCapability.INSTANCE);
                setVine(levelIn, player, skillCap);
                player.getCooldowns().addCooldown(icon.getItem(), 20);
            }
        }
    }

    private void setVine(Level level, Player playerIn, PlayerSkillCapabilityInterface skillCap) {
        BlockPos playerBlockPos = playerIn.blockPosition();
        SoulVineBlock createdBlock = ModBlocks.SOUL_VINE_BLOCK.get();
        BlockState playerBlockState = level.getBlockState(playerBlockPos);
        Vec3 playerLookVector = playerIn.getLookAngle();
        Direction nearestDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        //DevilRpg.LOGGER.info("-------->Direction: {}", nearestDirection);
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);
        if (playerBlockState.getBlock().equals(Blocks.AIR)
                && level.getBlockState(newBlockpos).getBlock().equals(Blocks.AIR)
                && SoulVineBlock.hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(level,newBlockpos,nearestDirection)) {
            int skillPoints = skillCap.getSkillsPoints().get(SkillEnum.SOULVINE);
            level
                    .setBlockAndUpdate(
                            newBlockpos,
                            createdBlock.defaultBlockState()
                                    .setValue(SoulVineBlock.AGE, 1)
                                    .setValue(SoulVineBlock.DIRECTIONS, nearestDirection)
                                    .setValue(SoulVineBlock.LEVEL, skillPoints)
                                    .setValue(SoulVineBlock.HAS_CHILDREN,false)
                    );


        }
    }
}
