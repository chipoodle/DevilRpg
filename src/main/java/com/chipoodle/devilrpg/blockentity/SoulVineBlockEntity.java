package com.chipoodle.devilrpg.blockentity;

import net.minecraft.world.level.block.Blocks;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.chipoodle.devilrpg.block.SoulVineBlock.*;

public class SoulVineBlockEntity extends BlockEntity {

    public static final int TICK_FACTOR = 20;
    private Long timeOfCreation = null;
    // Skill level (antes propiedad LEVEL del blockstate). Se movio aqui para reducir el espacio de
    // estados del bloque (el mayor multiplicador que el modelo no usaba). Persistido en NBT.
    private int skillLevel = 0;

    public int getSkillLevel() { return skillLevel; }
    public void setSkillLevel(int skillLevel) { this.skillLevel = skillLevel; this.setChanged(); }

    /**
     * Modo PUENTE: la vid crece <b>en línea recta</b> en la dirección con la que se lanzó (la mirada del
     * jugador) <b>sin necesitar ninguna pared al lado</b>, para poder cruzar barrancos y abismos. Se apaga en
     * cuanto la vid <b>topa</b> con algo (suelo, pared o techo): a partir de ahí vuelve a la mecánica de
     * siempre (agarrarse a las superficies y elegir dirección).
     */
    private boolean bridging = false;

    public boolean isBridging() { return bridging; }
    public void setBridging(boolean bridging) { this.bridging = bridging; this.setChanged(); }

    // Permite que los bloques hijos hereden el momento de creacion de la raiz, para que TODA la vid
    // se marchite a la vez (antes cada bloque tenia su propio reloj y la raiz duraba mas).
    public void setTimeOfCreation(long timeOfCreation) { this.timeOfCreation = timeOfCreation; this.setChanged(); }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("skillLevel", skillLevel);
        tag.putBoolean("bridging", bridging);
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.skillLevel = tag.getInt("skillLevel");
        this.bridging = tag.getBoolean("bridging");
    }


    public SoulVineBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.SOUL_VINE_ENTITY_BLOCK.get(), pos, state);
    }

    public boolean tick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos currentBlockPos, @NotNull RandomSource randomSource) {
        if (timeOfCreation == null) {
            timeOfCreation = world.getGameTime();
        }

        Integer currentAge = state.getValue(AGE);
        int skillLevel = this.skillLevel;
        int currentDecay = state.getValue(DECAY_STAGE);
        Direction currentDirection = state.getValue(DIRECTIONS);
        Integer duration = (skillLevel * TICK_FACTOR + 60) * 2; // vida el doble (era x1)
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

                // MODO PUENTE: mientras esté activo, la vid crece RECTO en la dirección del lanzamiento
                // (la mirada del jugador) sin exigir una pared al lado, que es lo que permite cruzar un vacío.
                // Se apaga en cuanto TOPA con algo (suelo, pared o techo): a partir de ahí valen las reglas
                // de siempre, así que la vid sigue trepando o rodeando como antes.
                if (bridging) {
                    BlockPos aheadPos = currentBlockPos.relative(currentDirection);
                    BlockState aheadState = world.getBlockState(aheadPos);
                    if (aheadState.isAir() || aheadState.is(Blocks.SHORT_GRASS)) {
                        state = setBlockDirection(state, world, currentBlockPos, currentDirection);
                        createChildBlock(state, world, currentDirection, aheadPos, currentBlockPos, currentDirection);
                        return true;
                    }
                    this.bridging = false;
                    this.setChanged();
                    DevilRpg.LOGGER.debug("[Soulvine] el puente topo con {} en {}: sigue con el crecimiento normal",
                            aheadState.getBlock(), aheadPos);
                }

                //DevilRpg.LOGGER.info("--------> currentDirection: {} age: {}", currentDirection, currentAge);

                BlockPos childBlockPos;
                BlockState childBlockState;
                // Orden de preferencia al topar con algo (antes era [direccion actual, DOWN, UP, ...] y por eso
                // la vid se iba SIEMPRE hacia abajo al llegar a una pared):
                //   1) seguir recto en la direccion del lanzamiento,
                //   2) trepar hacia ARRIBA (subir por la pared),
                //   3) bajar hacia ABAJO,
                //   4) y solo como ultimo recurso rodear por los lados.
                List<Direction> possibleValues = new ArrayList<>();
                possibleValues.add(currentDirection);
                for (Direction candidate : new Direction[]{Direction.UP, Direction.DOWN,
                        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                    if (candidate != currentDirection) {
                        possibleValues.add(candidate);
                    }
                }
                for (Direction nextDirection : possibleValues) {
                    // DevilRpg.LOGGER.info("nextDirection--> {}", nextDirection);
                    childBlockPos = currentBlockPos.relative(nextDirection);
                    childBlockState = world.getBlockState(childBlockPos);
                    if (childBlockState.isAir() || childBlockState.is(Blocks.SHORT_GRASS)) {
                        if (hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(world, childBlockPos, nextDirection)) {
                            state = setBlockDirection(state, world, currentBlockPos, nextDirection);
                            if (nextDirection != currentDirection) {
                                DevilRpg.LOGGER.debug("[Soulvine] la vid cambia de rumbo: {} -> {} en {}",
                                        currentDirection, nextDirection, currentBlockPos);
                            }
                            createChildBlock(state, world, currentDirection, childBlockPos, currentBlockPos, nextDirection);
                            return true;
                        } else {
                            //Verifica el siguiente del siguiente, con el MISMO criterio de preferencia que
                            //arriba (primero arriba, luego abajo, y los lados al final).
                            List<Direction> adjacentDirections = new ArrayList<>();
                            for (Direction candidate : new Direction[]{Direction.UP, Direction.DOWN,
                                    Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                                if (candidate != nextDirection && candidate != nextDirection.getOpposite()) {
                                    adjacentDirections.add(candidate);
                                }
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
        // El hijo hereda el reloj y el nivel de la vid para que toda la planta tenga el MISMO tiempo de
        // vida (antes el hijo nacía con skillLevel=0 -> duración corta, mientras la raíz duraba por el skill).
        if (serverLevel.getBlockEntity(childBlockPos) instanceof SoulVineBlockEntity childBE) {
            childBE.setTimeOfCreation(this.timeOfCreation);
            childBE.setSkillLevel(this.skillLevel);
            // El puente sigue siendo puente: el hijo hereda el modo hasta que alguno tope con algo.
            childBE.setBridging(this.bridging);
        }
    }
}

