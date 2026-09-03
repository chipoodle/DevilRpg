package com.chipoodle.devilrpg.blockentity;

import net.minecraft.world.level.block.Blocks;
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


    public SoulVineBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.SOUL_VINE_ENTITY_BLOCK.get(), pos, state);
    }

    public boolean tick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos currentBlockPos, @NotNull RandomSource randomSource) {
        if (timeOfCreation == null) {
            timeOfCreation = world.getGameTime();
        }

        Integer currentAge = state.getValue(AGE);
        int skillLevel = state.getValue(LEVEL);
        int currentDecay = state.getValue(DECAY_STAGE);
        Direction currentDirection = state.getValue(DIRECTIONS);
        Integer duration = skillLevel * TICK_FACTOR + 60;
        boolean hasChildren = state.getValue(HAS_CHILDREN);

        //DevilRpg.LOGGER.info("-------->tick. Age {} ", currentAge);

        long timeElapsed = world.getGameTime() - timeOfCreation;
        int newDecayStage = (int) ((timeElapsed * 4) / duration); // 4 etapas de decadencia

        // Actualiza el estado de decadencia
        if (newDecayStage != currentDecay && newDecayStage <= 3) {
            state = state.setValue(DECAY_STAGE, newDecayStage);
            world.setBlockAndUpdate(currentBlockPos, state);
        }

        if (!canStay(state, world, currentBlockPos, currentDirection) || timeOfCreation + duration < world.getGameTime()) {
            world.destroyBlock(currentBlockPos, true);
            return hasChildren;
        }

        double length = skillLevel * 0.5 + 10;
        if (currentAge < length) {
            //DevilRpg.LOGGER.info("-------->Direction: {}, AGE {}, LEVEL {}, duration: {}", currentDirection, currentAge, skillLevel, duration);

            if (!hasChildren) {

                //DevilRpg.LOGGER.info("--------> currentDirection: {} age: {}", currentDirection, currentAge);

                BlockPos childBlockPos;
                BlockState childBlockState;
                List<Direction> possibleValues = new ArrayList<>(DIRECTIONS.getPossibleValues()
                        .stream()
                        .filter(x -> x != currentDirection)
                        .sorted(Comparator.comparingInt(Direction::get3DDataValue)).toList());
                //DevilRpg.LOGGER.info("Ordered list--> {}", possibleValues);
                possibleValues.add(0, currentDirection);
                for (Direction nextDirection : possibleValues) {
                    // DevilRpg.LOGGER.info("nextDirection--> {}", nextDirection);
                    childBlockPos = currentBlockPos.relative(nextDirection);
                    childBlockState = world.getBlockState(childBlockPos);
                    if (childBlockState.isAir() || childBlockState.is(Blocks.SHORT_GRASS)) {
                        if (hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(world, childBlockPos, nextDirection)) {
                            state = setBlockDirection(state, world, currentBlockPos, nextDirection);
                            createChildBlock(state, world, currentDirection, childBlockPos, currentBlockPos, nextDirection);
                            return true;
                        } else {
                            //Verifica el siguiente del siguiente
                            List<Direction> adjacentDirections = new ArrayList<>(DIRECTIONS.getPossibleValues().stream()
                                    .filter(dir -> !dir.equals(nextDirection))
                                    .filter(dir -> !dir.equals(nextDirection.getOpposite()))
                                    .sorted(Comparator.comparingInt(Direction::get3DDataValue))
                                    .toList());


                            if (adjacentDirections.contains(Direction.DOWN)) {
                                adjacentDirections.remove(Direction.DOWN);
                                adjacentDirections.add(0, Direction.DOWN);
                            }
                            if (adjacentDirections.contains(Direction.UP)) {
                                adjacentDirections.remove(Direction.UP);
                                adjacentDirections.add(0, Direction.UP);
                            }

                            for (Direction adjacentDirection : adjacentDirections) {
                                BlockPos adjacentBlockPos = childBlockPos.relative(adjacentDirection);
                                BlockState adjacentBlockState = world.getBlockState(adjacentBlockPos);
                                if (adjacentBlockState.isAir() && hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(world, adjacentBlockPos, adjacentDirection)) {
                                    state = setBlockDirection(state, world, currentBlockPos, nextDirection);
                                    createChildBlock(state, world, currentDirection, childBlockPos, currentBlockPos, adjacentDirection);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return hasChildren;
    }

    @NotNull
    private BlockState setBlockDirection(@NotNull BlockState blockState, @NotNull ServerLevel serverLevel, @NotNull BlockPos currentBlockPos, Direction currentDirection) {
        this.setChanged();
        switch (currentDirection) {
            case UP -> blockState = blockState.setValue(SOULVINE_FACING, Direction.UP);
            case DOWN -> blockState = blockState.setValue(SOULVINE_FACING, Direction.DOWN);
            case EAST -> blockState = blockState.setValue(SOULVINE_FACING, Direction.EAST);
            case WEST -> blockState = blockState.setValue(SOULVINE_FACING, Direction.WEST);
            case NORTH -> blockState = blockState.setValue(SOULVINE_FACING, Direction.NORTH);
            case SOUTH -> blockState = blockState.setValue(SOULVINE_FACING, Direction.SOUTH);
        }
        serverLevel.setBlockAndUpdate(currentBlockPos, blockState);
        return blockState;
    }

    private void createChildBlock(@NotNull BlockState blockState, @NotNull ServerLevel serverLevel,
                                  Direction currentDirection, BlockPos childBlockPos, BlockPos currentBlockPos, Direction childDirection) {
        //DevilRpg.LOGGER.info("-------->AGE {} creating children at: {}",currentAge,childBlockPos);
        this.setChanged();
        BlockState parentBlockState = blockState.setValue(HAS_CHILDREN, true);

        BlockState childBlockState = SoulVineBlock
                .getGrowIntoState(blockState)
                .setValue(AGE, blockState.getValue(AGE) + 1)
                .setValue(DIRECTIONS, currentDirection);

        serverLevel.setBlockAndUpdate(currentBlockPos, parentBlockState);


        //DevilRpg.LOGGER.debug("======> Age {} currentDirection {} childDirection {}", AGE, currentDirection, childDirection);

        serverLevel.setBlockAndUpdate(childBlockPos, childBlockState);
    }
}

