package com.chipoodle.devilrpg.blockentity;

import com.chipoodle.devilrpg.block.SoulShieldVineBlock;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import static com.chipoodle.devilrpg.block.SoulShieldVineBlock.*;

public class SoulShieldVineBlockEntity extends BlockEntity {

    public static final int TICK_FACTOR = 20;
    private Long timeOfCreation = null;

    private boolean directionChanged = false;


    public SoulShieldVineBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.SOUL_SHIELD_VINE_ENTITY_BLOCK.get(), pos, state);
    }

    public void tick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos currentBlockPos, @NotNull RandomSource randomSource) {
        if (timeOfCreation == null) {
            timeOfCreation = world.getGameTime();
        }

        int skillLevel = state.getValue(LEVEL);
        int currentDecay = state.getValue(DECAY_STAGE);
        Direction currentDirection = state.getValue(DIRECTIONS);

        Integer duration = skillLevel * TICK_FACTOR + 80;
        //boolean hasChildren = state.getValue(HAS_CHILDREN);

        long timeElapsed = world.getGameTime() - timeOfCreation;
        int newDecayStage = (int) ((timeElapsed * 4) / duration); // 4 etapas de decadencia

        // Actualiza el estado de decadencia
        if (newDecayStage != currentDecay && newDecayStage <= 3) {
            state = state.setValue(DECAY_STAGE, newDecayStage);
            world.setBlockAndUpdate(currentBlockPos, state);
        }

        //DevilRpg.LOGGER.info(" timeOfCreation {} + duration {} < world.getGameTime() {} ----> {} < {} = {}",  timeOfCreation , duration , world.getGameTime(),timeOfCreation + duration, world.getGameTime(), timeOfCreation + duration < world.getGameTime());

        //setBlockDirection(state,world,currentBlockPos,currentDirection);
        if (!directionChanged) {
            this.setChanged();
            switch (currentDirection) {
                case UP -> state = state.setValue(SoulShieldVineBlock.SOULVINE_FACING, Direction.EAST);
                case DOWN -> state = state.setValue(SoulShieldVineBlock.SOULVINE_FACING, Direction.WEST);
                case EAST -> state = state.setValue(SoulShieldVineBlock.SOULVINE_FACING, Direction.UP);
                case WEST -> state = state.setValue(SoulShieldVineBlock.SOULVINE_FACING, Direction.DOWN);
                case NORTH -> state = state.setValue(SoulShieldVineBlock.SOULVINE_FACING, Direction.NORTH);//
                case SOUTH -> state = state.setValue(SoulShieldVineBlock.SOULVINE_FACING, Direction.SOUTH);//
            }
            //getGrowIntoState(state).setValue(AGE, state.getValue(AGE) + 1)
            world.setBlockAndUpdate(currentBlockPos, state);
            directionChanged = true;
        }

        if (/*!canStay(state, world, currentBlockPos, currentDirection) ||*/ timeOfCreation + duration < world.getGameTime()) {
            world.destroyBlock(currentBlockPos, true);
        }

    }

}

