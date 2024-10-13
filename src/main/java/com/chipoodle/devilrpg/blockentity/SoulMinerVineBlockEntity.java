package com.chipoodle.devilrpg.blockentity;


import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulMinerVineBlock;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.chipoodle.devilrpg.block.SoulMinerVineBlock.*;
import static com.chipoodle.devilrpg.block.SoulShieldVineBlock.DECAY_STAGE;


public class SoulMinerVineBlockEntity extends BlockEntity {

    public static final int TICK_FACTOR = 140;
    private Long timeOfCreation = null;

    public SoulMinerVineBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.SOUL_MINER_VINE_ENTITY_BLOCK.get(), pos, state);
    }

    public boolean tick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos currentBlockPos, @NotNull RandomSource randomSource) {
        if (timeOfCreation == null) {
            timeOfCreation = world.getGameTime();
        }

        Integer currentAge = state.getValue(AGE); // Obtener el AGE actual
        int skillLevel = state.getValue(LEVEL); // Obtener el nivel de habilidad
        int maxBranchLength = skillLevel + 5; // Longitud máxima determinada por el nivel de habilidad
        int currentDecay = state.getValue(DECAY_STAGE);
        Direction currentDirection = state.getValue(DIRECTIONS);
        Integer duration = TICK_FACTOR;
        boolean hasChildren = state.getValue(HAS_CHILDREN);

        long timeElapsed = world.getGameTime() - timeOfCreation;
        int newDecayStage = (int) ((timeElapsed * 4) / duration); // 4 etapas de decadencia

        // Actualiza el estado de decadencia
        if (newDecayStage != currentDecay && newDecayStage <= 3) {
            state = state.setValue(DECAY_STAGE, newDecayStage);
            world.setBlockAndUpdate(currentBlockPos, state);
        }

        // Si la planta ha alcanzado su duración máxima, destruye el bloque
        if (!canStay(state, world, currentBlockPos, currentDirection) || timeOfCreation + duration < world.getGameTime()) {
            world.destroyBlock(currentBlockPos, true);
            return hasChildren;
        }

        // Verificar si la rama ha alcanzado su longitud máxima
        if (currentAge < maxBranchLength) { // Comparar AGE con la longitud máxima
            if (!hasChildren) {
                BlockPos childBlockPos;
                BlockState childBlockState;

                ArrayList<Direction> directions = new ArrayList<>(Direction.allShuffled(randomSource));

                for (Direction nextDirection : directions) {
                    childBlockPos = currentBlockPos.relative(nextDirection);
                    childBlockState = world.getBlockState(childBlockPos);

                    if (isMineable(childBlockState)) {
                        state = setBlockDirection(state, world, currentBlockPos, nextDirection);
                        mineBlockAndExpand(state, world, currentDirection, childBlockPos, currentBlockPos, nextDirection, randomSource, currentAge, maxBranchLength);
                        return true;
                    }
                }
            }
        }
        return hasChildren;
    }

    @NotNull
    private BlockState setBlockDirection(@NotNull BlockState blockState, @NotNull ServerLevel serverLevel, @NotNull BlockPos currentBlockPos, Direction currentDirection) {
        this.setChanged();
        blockState = blockState.setValue(SOULVINE_FACING, currentDirection);
        serverLevel.setBlockAndUpdate(currentBlockPos, blockState);
        return blockState;
    }

    private void mineBlockAndExpand(@NotNull BlockState blockState, @NotNull ServerLevel serverLevel, Direction currentDirection, BlockPos childBlockPos, BlockPos currentBlockPos, Direction childDirection, @NotNull RandomSource randomSource, int currentAge, int maxBranchLength) {
        this.setChanged();

        // Elimina el bloque que está siendo minado
        serverLevel.destroyBlock(childBlockPos, true);

        // Establece el bloque hijo en la primera dirección
        BlockState childBlockState = SoulMinerVineBlock
                .getGrowIntoState(blockState)
                .setValue(AGE, blockState.getValue(AGE) + 1) // Incrementar AGE para el bloque hijo
                .setValue(DIRECTIONS, currentDirection);

        serverLevel.setBlockAndUpdate(childBlockPos, childBlockState);
        serverLevel.setBlockAndUpdate(currentBlockPos, blockState.setValue(HAS_CHILDREN, true));


        List<Direction> possibleDirections = new ArrayList<>(Direction.allShuffled(randomSource));
        possibleDirections.remove(currentDirection.getOpposite()); // Evitar volver hacia atrás


        if (currentAge  == maxBranchLength - 1) {
            if (possibleDirections.isEmpty()) {
                return;
            }

            //Puede haber menos de 3 posibles direcciones
            int branchesToCreate = Math.min(3, possibleDirections.size());
            for (int i = 0; i < branchesToCreate; i++) {
                // Obtener una dirección aleatoria válida
                Direction nextDirection = possibleDirections.remove(0);
                BlockPos nextBlockPos = currentBlockPos.relative(nextDirection);
                BlockState nextBlockState = serverLevel.getBlockState(nextBlockPos);

                if (isMineable(nextBlockState)) {
                    // Minar el bloque y expandirse
                    serverLevel.destroyBlock(nextBlockPos, true);
                    BlockState newBlockState = SoulMinerVineBlock
                            .getGrowIntoState(blockState)
                            .setValue(AGE, blockState.getValue(AGE) + 1) // Incrementar AGE para la nueva rama
                            .setValue(DIRECTIONS, nextDirection);
                    DevilRpg.LOGGER.info("==============>");
                    serverLevel.setBlockAndUpdate(nextBlockPos, newBlockState);
                }
            }
        }
    }

    private boolean isMineable(BlockState blockState) {
        Material material = blockState.getMaterial();
        // Verifica si el bloque es roca, tierra o minerales
        return material == Material.STONE
                || material == Material.METAL
                || material == Material.HEAVY_METAL
                || material == Material.DIRT
                || material == Material.GRASS
                || material == Material.CLAY
                || material == Material.SAND
                || material == Material.LEAVES
                || material == Material.MOSS
                || material == Material.SNOW
                || material == Material.TOP_SNOW
                || material == Material.WEB
                || blockState.getBlock().equals(Blocks.NETHERRACK)
                || blockState.getBlock().equals(Blocks.BASALT)
                ;
    }
}

