package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * <b>El rebaño del pueblo vuelve al corral andando.</b> Lo pidió el jugador: <i>"cuando el ganadero entra al corral,
 * deja la puerta abierta y los animales se salen; dale la capacidad para meterlos de vuelta o implementa algún
 * mecanismo para que los animales que se salgan vuelvan a entrar"</i>.
 * <p>
 * Antes esto era un <b>teleport</b> al corral ({@code VillageGenerator.recogerGanadoPerdido}, hoy
 * {@code ganadoPerdidoDelPueblo} + este goal) y solo miraba a los que estaban a más de 26 bloques del centro del
 * corral: el animal que se salía y se quedaba pastando al lado de la valla contaba como "dentro" y <b>no volvía
 * nunca</b> (medido en el guardado del jugador, aldea 2: la vaca del pueblo a <b>12,1</b> bloques del corral y la
 * oveja a <b>13,5</b>, con el portón abierto). Un teleport, además, se ve feo.
 * <p>
 * Cómo vuelve, por orden:
 * <ol>
 *   <li><b>Andando</b>: va al portón <b>por fuera</b> (con la puerta cerrada, que si no el rebaño de dentro se sale
 *       mientras éste llega) y, cuando está pegado, se le abre el portón y se le manda al hueco de dentro. El portón
 *       lo cierra el propio rebaño al pasar (o la red de seguridad del latido).</li>
 *   <li><b>Si no encuentra el camino</b> (atascado: {@link #ATASCO_LIMITE} ticks sin acercarse, invariante I3), se le
 *       mete en el corral <b>a mano</b>. Es el último recurso (aldea amurallada sin hueco, un animal en un tejado...):
 *       feo, pero mejor que un rebaño perdido.</li>
 * </ol>
 * Solo se le pone a animales <b>marcados del pueblo</b> que estén fuera del corral: los del <b>jugador</b> (sin marca)
 * y los que van <b>montados</b> o <b>atados con una cuerda</b> no se tocan.
 * <p>
 * La prioridad es {@link #PRIORIDAD}: por debajo de huir, criar y de la comida en la mano del jugador (que mandan), y
 * por encima de <b>pasear</b>, que es el goal que pisaría la navegación. En el selector del juego un goal <b>solo</b>
 * puede ser sustituido por otro de prioridad <b>estrictamente menor</b>, así que uno igual (seguir a la madre) no le
 * quita el sitio y el paso (prioridad 5) no puede interrumpirlo mientras vuelve a casa.
 */
public class VuelveAlCorralGoal extends Goal {

    /**
     * Prioridad con la que se le pone al animal (ver la explicación de la clase). Un animal <b>no</b> tiene cerebro
     * que le escriba el destino (eso es cosa de los aldeanos, invariante I5), así que aquí la navegación se pide con
     * {@code getNavigation().moveTo} y nadie la pisa: el goal, mientras corre, tiene la bandera {@link Flag#MOVE}.
     */
    public static final int PRIORIDAD = 4;
    /** Velocidad con la que vuelve (el mismo modificador que usa el juego para "seguir a la madre"). */
    private static final double VELOCIDAD = 1.15D;
    /** A esta distancia del portón ya se le abre: antes no, o el rebaño de dentro se saldría mientras éste llega. */
    private static final double CERCA_DEL_PORTON = 6.0D;
    /** A cuántos bloques por <b>fuera</b> del portón se le pone el punto de reunión. */
    private static final int FUERA_DEL_PORTON = 2;
    /**
     * Ticks sin <b>acercarse</b> al corral antes de rendirse y meterlo a mano (30 s). Se mide por progreso (no por
     * camino, invariante I3): un animal que viene de 100 bloques tarda, y no se le puede dar por atascado por eso.
     */
    private static final int ATASCO_LIMITE = 600;
    /** Cada cuánto se le vuelve a pedir el camino (por si la navegación se quedó sin ruta al abrirse el portón). */
    private static final int REINTENTO = 40;
    /** Cuánto tiene que acercarse para considerar que avanza (bloques). */
    private static final double PROGRESO = 1.0D;

    private final Animal animal;
    private final BlockPos center;
    /**
     * Cota del pueblo, medida <b>una vez</b> por goal (invariante I1) y cacheada: el corral no se mueve y esto corre
     * cada tick.
     */
    private int cota = Integer.MIN_VALUE;
    private double mejorDistancia = Double.MAX_VALUE;
    private int atasco;
    private int reintento;
    @Nullable
    private BlockPos destino;

    public VuelveAlCorralGoal(Animal animal, BlockPos center) {
        this.animal = animal;
        this.center = center;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    /**
     * Le pone a un animal del pueblo el goal de volver a casa (idempotente: si ya lo lleva, no hace nada). Lo llama
     * el latido de la aldea para cada animal del rebaño que se ha quedado fuera del corral.
     *
     * @return {@code true} si se lo ha puesto ahora
     */
    public static boolean asegurar(Animal animal, BlockPos center) {
        if (!(animal instanceof Mob mob) || mob.level().isClientSide) {
            return false;
        }
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof VuelveAlCorralGoal) {
                return false; // ya lo lleva
            }
        }
        mob.goalSelector.addGoal(PRIORIDAD, new VuelveAlCorralGoal(animal, center));
        return true;
    }

    @Override
    public boolean canUse() {
        return siguePerdido();
    }

    @Override
    public boolean canContinueToUse() {
        return siguePerdido();
    }

    /**
     * ¿Sigue habiendo que traerlo a casa? No si es del jugador, si va montado o atado, o si ya está dentro del
     * corral (que es "en casa" en cuanto pisa la valla, aunque aún no haya llegado a su rincón).
     */
    private boolean siguePerdido() {
        if (animal.isPassenger() || animal.isVehicle() || animal.isLeashed()) {
            return false; // montado o atado: es de alguien, no se toca
        }
        if (!VillageGenerator.esDelRebano(animal)) {
            return false; // sin la marca del pueblo: es del jugador
        }
        return !VillageGenerator.enElCorral(center, animal);
    }

    @Override
    public void start() {
        atasco = 0;
        reintento = 0;
        destino = null;
        mejorDistancia = Double.MAX_VALUE;
    }

    @Override
    public void tick() {
        if (!(animal.level() instanceof ServerLevel level)) {
            return;
        }
        if (cota == Integer.MIN_VALUE) {
            cota = VillageGenerator.cotaDeLaPlaza(level, center);
            if (cota <= level.getMinBuildHeight() + 1) {
                return; // sin cota no hay corral al que volver
            }
        }
        BlockPos base = VillageGenerator.baseDeAnexo(center);
        double alCorral = distanciaXZ(base.getX() + 0.5D, base.getZ() + 0.5D);
        if (alCorral < mejorDistancia - PROGRESO) {
            mejorDistancia = alCorral;
            atasco = 0;
        } else {
            atasco++;
        }
        if (atasco >= ATASCO_LIMITE) {
            meterlo(level, alCorral); // último recurso: no encuentra el camino
            return;
        }
        BlockPos porton = VillageGenerator.portonDelCorral(center, cota);
        BlockPos objetivo;
        if (distanciaXZ(porton.getX() + 0.5D, porton.getZ() + 0.5D) <= CERCA_DEL_PORTON) {
            // En la puerta: se le abre (el corral está cerrado por los cuatro lados) y se le manda al hueco de dentro.
            // El portón queda apuntado como abierto por el pueblo, así que lo cierra el rebaño al pasar o el latido.
            VillagerGateGoal.abrirParaElRebano(level, porton);
            objetivo = VillageGenerator.puntoDeApoyoAnexo(level, center);
        } else {
            // Todavía lejos: primero al portón POR FUERA, con la puerta cerrada.
            objetivo = fueraDelPorton(level, porton);
        }
        if (reintento-- <= 0 || !objetivo.equals(destino)) {
            animal.getNavigation().moveTo(objetivo.getX() + 0.5D, objetivo.getY(), objetivo.getZ() + 0.5D, VELOCIDAD);
            destino = objetivo;
            reintento = REINTENTO;
        }
    }

    @Override
    public void stop() {
        animal.getNavigation().stop();
        // Si ha llegado (está dentro) se le cierra el portón detrás: el que abrió el pueblo, y sin nadie más cruzando.
        if (animal.level() instanceof ServerLevel level && cota != Integer.MIN_VALUE
                && VillageGenerator.enElCorral(center, animal)) {
            VillagerGateGoal.cerrarParaElRebano(level, VillageGenerator.portonDelCorral(center, cota), animal);
        }
    }

    /** El punto de reunión <b>fuera</b> del portón (el lado al que mira la puerta, que es por donde se entra). */
    private BlockPos fueraDelPorton(ServerLevel level, BlockPos porton) {
        BlockState estado = level.getBlockState(porton);
        Direction fuera = estado.getBlock() instanceof FenceGateBlock
                ? estado.getValue(FenceGateBlock.FACING) : Direction.WEST;
        return porton.relative(fuera, FUERA_DEL_PORTON);
    }

    /** Último recurso: no encuentra el camino, así que se le mete en el corral a mano (y se deja dicho en el log). */
    private void meterlo(ServerLevel level, double alCorral) {
        BlockPos dentro = VillageGenerator.destinoDelAnimal(level, center, cota, animal, 0);
        animal.getNavigation().stop();
        animal.moveTo(dentro.getX() + 0.5D, dentro.getY(), dentro.getZ() + 0.5D, animal.getYRot(), 0.0F);
        animal.setDeltaMovement(Vec3.ZERO);
        DevilRpg.LOGGER.info("[Village] Un {} del corral no encontraba el camino a casa (a {} bloques): se le ha"
                        + " metido en el corral a mano (ultimo recurso)", animal.getType().getDescription().getString(),
                (int) alCorral);
        atasco = 0;
        mejorDistancia = Double.MAX_VALUE;
    }

    /** Distancia <b>horizontal</b> (XZ) del animal a un punto: la Y de un animal perdido no dice nada (invariante I2). */
    private double distanciaXZ(double x, double z) {
        double dx = animal.getX() - x;
        double dz = animal.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
