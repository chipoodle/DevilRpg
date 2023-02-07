package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import com.chipoodle.devilrpg.capability.player_minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.config.DevilRpgConfig;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Mirror;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SkillSoulVine implements ISkillContainer {

    public SkillSoulVine(PlayerSkillCapability parentCapability) {
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SOULVINE;
    }

    @Override
    public void execute(World worldIn, PlayerEntity playerIn, HashMap<String, String> parameters) {
        if (!worldIn.isClientSide) {
            Random rand = new Random();
            worldIn.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
                    SoundEvents.CHICKEN_EGG, SoundCategory.NEUTRAL, 0.5F,
                    0.4F / (rand.nextFloat() * 0.4F + 0.8F));
            setVine(worldIn, playerIn);
        }
    }

    private void setVine(World worldIn, PlayerEntity playerIn) {
        BlockPos blockPos = playerIn.blockPosition();
        Block block = ModBlocks.SOUL_VINE_BLOCK.get();
        BlockState blockState = worldIn.getBlockState(blockPos);

        Vector3d playerLookVector = playerIn.getLookAngle();
        Direction nearest = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);

        DevilRpg.LOGGER.info("-------->Direction: " + nearest);

        if (blockState.getBlock().equals(Blocks.AIR))
            worldIn
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
