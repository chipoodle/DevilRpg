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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.chipoodle.devilrpg.block.SoulVineBlock.*;

public class SoulVineBlockEntity extends BlockEntity {

    public static final int TICK_FACTOR = 20;
    private Long timeOfCreation = null;
    private boolean hasChildren = false;

    public SoulVineBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.SOUL_VINE_ENTITY_BLOCK.get(), pos, state);
    }

    public void tick(@NotNull BlockState blockState, @NotNull ServerLevel serverLevel, @NotNull BlockPos oldBlockPos, @NotNull RandomSource randomSource) {
        if (timeOfCreation == null) {
            timeOfCreation = serverLevel.getGameTime();
        }

        Integer currentAge = blockState.getValue(AGE);
        Integer skillLevel = blockState.getValue(LEVEL);
        Direction currentDirection = blockState.getValue(DIRECTIONS);
        Integer duration = skillLevel * TICK_FACTOR;

        //DevilRpg.LOGGER.info("-------->tick. Age {} ", currentAge);

        if (!SoulVineBlock.canStay(blockState, serverLevel, oldBlockPos, currentDirection) || timeOfCreation + duration < serverLevel.getGameTime()) {
            serverLevel.destroyBlock(oldBlockPos, true);
            return;
        }

        if (currentAge < skillLevel * 0.5 + 10) {
            //DevilRpg.LOGGER.info("-------->Direction: {}, AGE {}, LEVEL {}, duration: {}", currentDirection, currentAge, skillLevel, duration);

            if (!hasChildren) {
                BlockPos nextBlockPos;
                BlockState nextBlockState;
                //DevilRpg.LOGGER.info("-------->oldBlocPos: {}, blockpos {} , direction {}", oldBlockPos, blockpos, this.customGrowthDirection);
                List<Direction> possibleValues = new ArrayList<>(DIRECTIONS.getPossibleValues().stream().filter(x -> x != currentDirection).sorted(Comparator.comparingInt(Direction::get3DDataValue)).toList());
                possibleValues.add(0, currentDirection);
                //DevilRpg.LOGGER.info("Ordered list--> {}", possibleValues);
                for (Direction nextBlockPosGeneral : possibleValues) {
                    DevilRpg.LOGGER.info("nextBlockPosGeneral--> {}", nextBlockPosGeneral);
                    nextBlockPos = oldBlockPos.relative(nextBlockPosGeneral);
                    nextBlockState = serverLevel.getBlockState(nextBlockPos);
                    if (SoulVineBlock.canGrowInto(nextBlockState)) {
                        createChildBlock(blockState, serverLevel, currentDirection, nextBlockPos);
                        return;
                    }
                }

            }
        }

    }
    private void createChildBlock(@NotNull BlockState blockState, @NotNull ServerLevel serverLevel, Direction currentDirection, BlockPos nextBlockPos) {
        //DevilRpg.LOGGER.info("-------->AGE {} creating children at: {}",currentAge,nextBlockPos);
        hasChildren = true;
        BlockState blockStateNew = SoulVineBlock.getGrowIntoState(blockState, serverLevel.random).setValue(AGE, blockState.getValue(AGE) + 1).setValue(DIRECTIONS, currentDirection);
        serverLevel.setBlockAndUpdate(nextBlockPos, blockStateNew);
    }


}

