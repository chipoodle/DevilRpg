package com.chipoodle.devilrpg.blockentity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import static com.chipoodle.devilrpg.block.SoulVineBlock.*;

public class SoulVineBlockEntity extends BlockEntity {

    private Long timeOfcreation = null;

    public SoulVineBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.SOUL_VINE_ENTITY_BLOCK.get(), pos, state);
    }

    public void tick(@NotNull BlockState blockState, @NotNull ServerLevel serverLevel, @NotNull BlockPos oldBlockPos, @NotNull RandomSource randomSource) {
        DevilRpg.LOGGER.info("-------->tick: ");
        if (timeOfcreation == null) {
            timeOfcreation = serverLevel.getGameTime();
        }

        Integer currentAge = blockState.getValue(AGE);
        Integer skillLevel = blockState.getValue(LEVEL);
        Direction currentDirection = blockState.getValue(DIRECTIONS);
        Integer duration = skillLevel * 20;

        if (currentAge < skillLevel * 0.5 + 10) {
            DevilRpg.LOGGER.info("-------->Direction: {}, AGE {}, LEVEL {}, duration: {}", currentDirection, currentAge, skillLevel, duration);
            BlockPos nextBlockPos = oldBlockPos.relative(currentDirection);
            BlockState nextBlockState = serverLevel.getBlockState(nextBlockPos);
            //DevilRpg.LOGGER.info("-------->oldBlocPos: {}, blockpos {} , direction {}", oldBlockPos, blockpos, this.customGrowthDirection);
            if (SoulVineBlock.canGrowInto(nextBlockState)) {
                BlockState blockStateNew = SoulVineBlock.getGrowIntoState(blockState, serverLevel.random).setValue(AGE, blockState.getValue(AGE) + 1).setValue(DIRECTIONS, currentDirection);
                serverLevel.setBlockAndUpdate(nextBlockPos, blockStateNew);
                //serverLevel.scheduleTick(nextBlockPos, blockStateNew.getBlock(), 3);
            }
        }

        if (timeOfcreation + duration < serverLevel.getGameTime()) {
            serverLevel.destroyBlock(oldBlockPos, true);
        }

        /*if (!SoulVineBlock.canStay(blockState, serverLevel, oldBlockPos, currentDirection)) {
            serverLevel.destroyBlock(oldBlockPos, true);
        }*/
    }
}
