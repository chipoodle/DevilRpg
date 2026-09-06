package com.chipoodle.devilrpg.blockentity;

import com.chipoodle.devilrpg.block.SoulShieldVineBlock;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
    // Skill level (antes propiedad LEVEL del blockstate). Movido aqui para reducir el espacio de
    // estados del bloque (el mayor multiplicador que el modelo no usaba). Persistido en NBT.
    private int skillLevel = 0;

    public int getSkillLevel() { return skillLevel; }
    public void setSkillLevel(int skillLevel) { this.skillLevel = skillLevel; this.setChanged(); }


    public SoulShieldVineBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.SOUL_SHIELD_VINE_ENTITY_BLOCK.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("skillLevel", skillLevel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.skillLevel = tag.getInt("skillLevel");
    }

    public void tick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos currentBlockPos, @NotNull RandomSource randomSource) {
        if (timeOfCreation == null) {
            timeOfCreation = world.getGameTime();
        }

        int skillLevel = this.skillLevel;
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

