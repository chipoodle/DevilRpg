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
import java.util.Comparator;
import java.util.List;

import static com.chipoodle.devilrpg.block.SoulVineBlock.*;

public class SoulVineBlockEntity extends BlockEntity {

    public static final int TICK_FACTOR = 20;

    /**
     * Orden base de preferencia cuando la trayectoria de la mirada no decide (o no hay trayectoria):
     * <b>primero ARRIBA</b> (trepar la pared), <b>luego ABAJO</b> y <b>al final los lados</b>.
     */
    private static final Direction[] DIRECCIONES_PREFERIDAS = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /** Reloj de la vid. <b>Persistido</b>: antes vivía solo en memoria y al salir y volver a entrar en la
     *  partida el bloque se cargaba sin él, así que la vid "rejuvenecía" entera y empezaba a envejecer de cero. */
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

    // ---------------------------------------------------------------------------------------------
    // Trayectoria de la mirada (mecánica nueva)
    //
    // La vid ya no se encasilla en uno de los 6 ejes: guarda el vector EXACTO de la mirada del jugador
    // (aimX/aimY/aimZ) y un error acumulado por eje (errX/errY/errZ) tipo DDA. En cada paso se reparte el
    // peso de la mirada (|aim| normalizado a suma 1) entre los ejes y se descuenta un bloque entero al eje
    // que de verdad avanzó, así que el eje que "debe" avanzar es el que más error lleva. Resultado: mirando
    // a 45° la vid hace una escalera perfecta de 45° en vez de irse por un solo eje, y si le sale una pared
    // por delante sigue su contorno eligiendo en cada paso la dirección que menos se aparta de la mirada.
    // ---------------------------------------------------------------------------------------------
    private double aimX = 0.0D, aimY = 0.0D, aimZ = 0.0D;
    private boolean hasAim = false;
    private double errX = 0.0D, errY = 0.0D, errZ = 0.0D;
    // Pesos del DDA: |mirada| normalizada a suma 1. Así cada paso reparte exactamente un bloque entre los
    // ejes y el error queda acotado (si no, el eje dominante se desbordaba y la escalera salía más plana
    // que la mirada). Se recalculan a partir de la mirada, no hace falta guardarlos.
    private double pesoX = 0.0D, pesoY = 0.0D, pesoZ = 0.0D;

    public boolean hasAim() { return hasAim; }

    /**
     * Fija la trayectoria a seguir: el vector de la mirada del jugador al lanzar la vid (se normaliza).
     * El error arranca valiendo el peso de cada eje, que es justo el primer paso del DDA.
     */
    public void setAim(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-6D) {
            this.hasAim = false;
            return;
        }
        this.aimX = x / length;
        this.aimY = y / length;
        this.aimZ = z / length;
        this.hasAim = true;
        recalcularPesos();
        this.errX = this.pesoX;
        this.errY = this.pesoY;
        this.errZ = this.pesoZ;
        this.setChanged();
    }

    /** Pesos del DDA (suman 1) a partir de la mirada guardada. */
    private void recalcularPesos() {
        double total = Math.abs(aimX) + Math.abs(aimY) + Math.abs(aimZ);
        if (total < 1.0E-6D) {
            this.pesoX = 0.0D;
            this.pesoY = 0.0D;
            this.pesoZ = 0.0D;
            return;
        }
        this.pesoX = Math.abs(aimX) / total;
        this.pesoY = Math.abs(aimY) / total;
        this.pesoZ = Math.abs(aimZ) / total;
    }

    // Permite que los bloques hijos hereden el momento de creacion de la raiz, para que TODA la vid
    // se marchite a la vez (antes cada bloque tenia su propio reloj y la raiz duraba mas).
    public void setTimeOfCreation(long timeOfCreation) { this.timeOfCreation = timeOfCreation; this.setChanged(); }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("skillLevel", skillLevel);
        tag.putBoolean("bridging", bridging);
        // El reloj de la vid tiene que sobrevivir a guardar y cargar la partida.
        if (timeOfCreation != null) {
            tag.putLong("timeOfCreation", timeOfCreation);
        }
        // La trayectoria también: si no, al recargar el chunk la vid seguiría creciendo recta hacia arriba.
        tag.putBoolean("hasAim", hasAim);
        tag.putDouble("aimX", aimX);
        tag.putDouble("aimY", aimY);
        tag.putDouble("aimZ", aimZ);
        tag.putDouble("errX", errX);
        tag.putDouble("errY", errY);
        tag.putDouble("errZ", errZ);
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.skillLevel = tag.getInt("skillLevel");
        this.bridging = tag.getBoolean("bridging");
        // Si el NBT no lo trae (vids guardadas antes de este arreglo) se deja null: el tick le pone la hora
        // actual y la vid vuelve a empezar, que es lo único que se puede hacer con una vid sin reloj.
        this.timeOfCreation = tag.contains("timeOfCreation") ? tag.getLong("timeOfCreation") : null;
        this.hasAim = tag.getBoolean("hasAim");
        this.aimX = tag.getDouble("aimX");
        this.aimY = tag.getDouble("aimY");
        this.aimZ = tag.getDouble("aimZ");
        this.errX = tag.getDouble("errX");
        this.errY = tag.getDouble("errY");
        this.errZ = tag.getDouble("errZ");
        recalcularPesos();
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

                // Dirección que mejor sigue la trayectoria de la mirada en ESTE paso (null si la vid no
                // tiene trayectoria, p. ej. colocada a mano).
                Direction ideal = idealDirection();
                Direction pasoRecto = (ideal != null) ? ideal : currentDirection;

                // MODO PUENTE: mientras esté activo, la vid crece RECTO en la dirección del lanzamiento
                // (la mirada del jugador) sin exigir una pared al lado, que es lo que permite cruzar un vacío.
                // Se apaga en cuanto TOPA con algo (suelo, pared o techo): a partir de ahí valen las reglas
                // de siempre, así que la vid sigue trepando o rodeando como antes.
                if (bridging) {
                    BlockPos aheadPos = currentBlockPos.relative(pasoRecto);
                    BlockState aheadState = world.getBlockState(aheadPos);
                    if (aheadState.isAir() || aheadState.is(Blocks.SHORT_GRASS)) {
                        state = setBlockDirection(state, world, currentBlockPos, pasoRecto);
                        registrarPaso(pasoRecto);
                        createChildBlock(state, world, currentDirection, pasoRecto, aheadPos, currentBlockPos);
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
                // Orden de preferencia al topar con algo:
                //   1) seguir la trayectoria de la mirada (siempre va primero),
                //   2) las direcciones que menos se apartan de esa trayectoria,
                //   3) y en caso de empate ARRIBA, luego ABAJO y al final los lados.
                // Antes la lista se ordenaba por Direction.get3DDataValue y DOWN (0) quedaba antes que UP (1),
                // así que la vid se iba SIEMPRE hacia abajo en cuanto chocaba con una pared.
                for (Direction nextDirection : direccionesOrdenadas(pasoRecto, currentDirection, null, null)) {
                    // DevilRpg.LOGGER.info("nextDirection--> {}", nextDirection);
                    childBlockPos = currentBlockPos.relative(nextDirection);
                    childBlockState = world.getBlockState(childBlockPos);
                    if (childBlockState.isAir() || childBlockState.is(Blocks.SHORT_GRASS)) {
                        // Con trayectoria, el paso que sigue la mirada no necesita agarrarse a nada (es el
                        // puente). Los desvíos sí: así la vid "resbala" por el contorno de la pared.
                        boolean sigueLaMirada = hasAim && nextDirection == pasoRecto;
                        if (sigueLaMirada || hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(world, childBlockPos, nextDirection)) {
                            state = setBlockDirection(state, world, currentBlockPos, nextDirection);
                            if (nextDirection != currentDirection) {
                                DevilRpg.LOGGER.debug("[Soulvine] la vid cambia de rumbo: {} -> {} en {}",
                                        currentDirection, nextDirection, currentBlockPos);
                            }
                            registrarPaso(nextDirection);
                            createChildBlock(state, world, currentDirection, nextDirection, childBlockPos, currentBlockPos);
                            return true;
                        } else {
                            //Verifica el siguiente del siguiente, con el MISMO criterio de preferencia.
                            List<Direction> adjacentDirections = direccionesOrdenadas(null, currentDirection, nextDirection, nextDirection.getOpposite());

                            for (Direction adjacentDirection : adjacentDirections) {
                                BlockPos adjacentBlockPos = childBlockPos.relative(adjacentDirection);
                                BlockState adjacentBlockState = world.getBlockState(adjacentBlockPos);
                                if (adjacentBlockState.isAir() && hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(world, adjacentBlockPos, adjacentDirection)) {
                                    state = setBlockDirection(state, world, currentBlockPos, nextDirection);
                                    registrarPaso(nextDirection);
                                    createChildBlock(state, world, currentDirection, nextDirection, childBlockPos, currentBlockPos);
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

    /**
     * Eje que va más "atrasado" respecto a la trayectoria: es el que avanza en este paso. Es un DDA: en cada
     * paso se suma el peso de la mirada en cada eje y al eje elegido se le descuenta un bloque entero, así
     * que una mirada a 45° alterna los dos ejes (escalera de 45°) y una casi vertical avanza casi siempre en
     * el eje dominante con un escalón de vez en cuando. Devuelve {@code null} si la vid no tiene trayectoria,
     * y entonces se usa la dirección guardada en el bloque (la mecánica de siempre).
     */
    private Direction idealDirection() {
        if (!hasAim) {
            return null;
        }
        double max = Math.max(errX, Math.max(errY, errZ));
        if (max <= 0.0D) {
            return null;
        }
        if (errX >= max) {
            return aimX >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (errY >= max) {
            return aimY >= 0.0D ? Direction.UP : Direction.DOWN;
        }
        return aimZ >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Direcciones ordenadas por lo bien que siguen la trayectoria de la mirada (producto escalar con el vector
     * de la mirada). Los empates los rompe primero <b>el rumbo que ya traía la vid</b> (para no zigzaguear
     * cuando el eje de la mirada está tapado) y después el orden base ARRIBA, ABAJO, lados. {@code primero}
     * (la dirección ideal del paso) va delante de todo.
     */
    private List<Direction> direccionesOrdenadas(Direction primero, Direction rumbo, Direction excluir, Direction excluirOpuesto) {
        List<Direction> ordenadas = new ArrayList<>();
        List<Direction> resto = new ArrayList<>();
        for (Direction candidata : DIRECCIONES_PREFERIDAS) {
            if (candidata == excluir || candidata == excluirOpuesto) {
                continue;
            }
            if (primero != null && candidata == primero) {
                ordenadas.add(candidata);
                continue;
            }
            resto.add(candidata);
        }
        // El orden base ya viene de ARRIBA hacia abajo: al ser un sort estable, los empates lo conservan.
        resto.sort(Comparator.comparingDouble((Direction candidata) -> -alineacionConLaMirada(candidata))
                .thenComparingInt(candidata -> candidata == rumbo ? 0 : 1));
        ordenadas.addAll(resto);
        return ordenadas;
    }

    /** Cuánto acompaña esa dirección a la mirada del jugador (-1 = justo al revés, 1 = clavada). */
    private double alineacionConLaMirada(Direction direction) {
        if (!hasAim) {
            return 0.0D;
        }
        return direction.getStepX() * aimX + direction.getStepY() * aimY + direction.getStepZ() * aimZ;
    }

    /**
     * Anota un paso en el error de la trayectoria: se suma el peso de la mirada en cada eje (los pesos suman
     * 1) y se descuenta un bloque entero al eje que de verdad se anduvo. Si el paso elegido no era el ideal
     * (p. ej. porque había pared), el eje tapado <b>no</b> descuenta nada y lo vuelve a intentar en el paso
     * siguiente: así la vid rodea el obstáculo siguiendo su contorno pero sin perder la trayectoria.
     */
    private void registrarPaso(Direction paso) {
        if (!hasAim) {
            return;
        }
        errX += pesoX - Math.abs(paso.getStepX());
        errY += pesoY - Math.abs(paso.getStepY());
        errZ += pesoZ - Math.abs(paso.getStepZ());
        this.setChanged();
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
                                  Direction currentDirection, Direction paso, BlockPos childBlockPos, BlockPos currentBlockPos) {
        //DevilRpg.LOGGER.info("-------->AGE {} creating children at: {}",currentAge,childBlockPos);
        this.setChanged();
        BlockState parentBlockState = blockState.setValue(HAS_CHILDREN, true);

        // Con trayectoria el hijo "apunta" al paso que acaba de dar (así ese rumbo sirve luego de desempate
        // para no zigzaguear). Sin trayectoria se mantiene la mecánica clásica: el hijo hereda el rumbo del
        // padre y vuelve a elegir, que es lo que produce la escalera de siempre.
        Direction rumboHijo = hasAim ? paso : currentDirection;
        BlockState childBlockState = SoulVineBlock
                .getGrowIntoState(blockState)
                .setValue(AGE, blockState.getValue(AGE) + 1)
                .setValue(DIRECTIONS, rumboHijo);

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
            // Y hereda la trayectoria (mirada + error ya descontado): la escalera continúa en el hijo.
            childBE.heredarTrayectoria(this);
        }
    }

    /** Copia la trayectoria de la mirada (y el error acumulado) a un bloque hijo. */
    private void heredarTrayectoria(SoulVineBlockEntity padre) {
        this.hasAim = padre.hasAim;
        this.aimX = padre.aimX;
        this.aimY = padre.aimY;
        this.aimZ = padre.aimZ;
        this.errX = padre.errX;
        this.errY = padre.errY;
        this.errZ = padre.errZ;
        // Los pesos tambien: son los que reparten el bloque de cada paso del DDA.
        this.pesoX = padre.pesoX;
        this.pesoY = padre.pesoY;
        this.pesoZ = padre.pesoZ;
        this.setChanged();
    }
}
