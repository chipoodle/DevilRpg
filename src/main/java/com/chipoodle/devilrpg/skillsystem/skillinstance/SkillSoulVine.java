package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Random;

public class SkillSoulVine implements ISkillContainer {

    public SkillSoulVine(PlayerSkillCapabilityImplementation parentCapability) {
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SOULVINE;
    }

    @Override
    public void execute(Level levelIn, Player playerIn, HashMap<String, String> parameters) {
        if (!levelIn.isClientSide) {
            Random rand = new Random();
            levelIn.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
                    SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F,
                    0.4F / (rand.nextFloat() * 0.4F + 0.8F));
            setVine(levelIn, playerIn);
        }
    }

    private void setVine(Level levelIn, Player playerIn) {
        BlockPos blockPos = playerIn.blockPosition();
        Block block = ModBlocks.SOUL_VINE_BLOCK.get();
        BlockState blockState = levelIn.getBlockState(blockPos);

        Vec3 playerLookVector = playerIn.getLookAngle();
        Direction nearest = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);

        DevilRpg.LOGGER.info("-------->Direction: " + nearest);

        if (blockState.getBlock().equals(Blocks.AIR))
            levelIn
                    .setBlock(
                            blockPos,
                            //block.defaultBlockState().setValue(SoulVineBlock.getPropertyForFace(playerViewDirection.), Boolean.valueOf(true)),
                            block.defaultBlockState()
                                    //.setValue(SoulVineBlock.getPropertyForFace(Direction.UP), Boolean.valueOf(true))
                                    .setValue(SoulVineBlock.getPropertyForFace(Direction.UP), Boolean.valueOf(true))
                            ,
                            2);
    }
}
