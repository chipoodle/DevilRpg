package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Goal del <b>aldeano obrero</b>: va <b>andando</b> hasta los huecos que dejaron los asedios en su aldea y los
 * vuelve a construir <b>bloque a bloque</b>, con su animación y su sonido de colocar.
 * <p>
 * Antes esto lo hacía el gestor con un {@code repair()} que reconstruía caminos, cabañas, faroles, granja y valla
 * <b>en un solo tick</b>: si mirabas, la aldea aparecía de la nada (y encima podía reconstruir sobre lo que
 * hubiera construido el jugador). Ahora el que trabaja es un aldeano, solo repone lo que <b>falta de verdad</b>
 * (según el plano de {@code VillageGenerator.captureBlueprint}) y <b>jamás</b> pisa un bloque que ya exista: si
 * en ese sitio hay algo puesto por el jugador, lo deja en paz.
 */
public class VillagerRepairGoal extends Goal {

    /** Distancia a la que el aldeano ya llega a colocar el bloque (a la altura de sus pies). */
    private static final double REACH = 4.5D;
    /**
     * Alcance <b>extra por cada bloque que el hueco esté POR ENCIMA</b> del obrero (brazo estirado).
     * <p>
     * Hace falta de verdad, y está medido: el <b>techo del kiosco está a la cota+5</b> (la plataforma en la cota, los
     * cuatro postes y el tejado encima) y con el alcance fijo de 4,5 <b>no se podía reponer nunca desde el suelo</b>:
     * la distancia mínima a un bloque cinco por encima es 5,0 (justo debajo) o 5,8 (a tres bloques), así que el
     * obrero se quedaba pegándose cabezazos debajo del agujero, se rendía a los 5 s y lo marcaba como inalcanzable
     * ({@code saltados}). Con 0,4 por bloque, un hueco a 5 se alcanza desde 6,5: entra incluso de pie a 3 bloques.
     * <p>
     * No se sube el alcance base (4,5) porque eso dejaría al obrero colocando bloques "a distancia" a su altura, que
     * se ve raro: lo que se estira es solo el brazo hacia ARRIBA.
     */
    private static final double REACH_EXTRA_POR_ALTURA = 0.4D;
    /** Ticks "trabajando" antes de colocar (medio segundo): se le ve dar el golpe, pero sin eternizarse. */
    private static final int WORK_TICKS = 10;
    /** Descanso entre bloque y bloque (medio segundo). Antes eran 2 s y el obrero tardaba una eternidad. */
    private static final int REST_TICKS = 10;
    /**
     * Descanso cuando NO hay nada que reparar (5 s). Importante: buscar huecos recorre el plano entero leyendo
     * bloques, así que no se puede hacer en cada tick.
     */
    private static final int IDLE_REST_TICKS = 100;
    /**
     * Si el obrero se aleja más de esto del centro de la aldea, deja de trabajar (no se pierde por el mundo).
     * Derivado del radio de la aldea: con un valor fijo, al agrandarla habría dejado de reparar el borde.
     */
    private static final double MAX_DISTANCE_FROM_CENTER = VillageGenerator.FENCE_RADIUS + 12.0D;
    /** Si no logra acercarse en este tiempo (ticks sin llegar), abandona ese hueco. */
    private static final int STUCK_LIMIT = 100;

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    /** Huecos que ya se dieron por inalcanzables, para no quedarse en bucle con ellos. */
    private final Set<Long> saltados = new HashSet<>();
    @Nullable
    private BlockPos target;
    private int workTicks;
    private int stuckTicks;
    /** Distancia más corta lograda en este viaje al hueco: mientras baje, el obrero avanza. */
    private double mejorDistancia = Double.MAX_VALUE;
    private int restTicks;

    public VillagerRepairGoal(Villager villager, BlockPos center, int objectiveIndex) {
        this.villager = villager;
        this.center = center;
        this.objectiveIndex = objectiveIndex;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (restTicks > 0) {
            restTicks--;
            return false;
        }
        if (!isBuilder() || villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        // En plena refriega nadie se pone a construir.
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex)) {
            return false;
        }
        // Ni en su hora de descanso: si no, el obrero se queda andando en la cama (sus goals ganan al cerebro).
        if (VillageManager.estaDescansando(villager)) {
            return false;
        }
        // Distancia HORIZONTAL al centro: la Y del centro puede ser la del spawn del jugador y no debe contar.
        double dxCentro = villager.getX() - center.getX();
        double dzCentro = villager.getZ() - center.getZ();
        if (dxCentro * dxCentro + dzCentro * dzCentro > MAX_DISTANCE_FROM_CENTER * MAX_DISTANCE_FROM_CENTER) {
            return false;
        }
        target = VillageManager.findRepairTarget(level, objectiveIndex, villager.blockPosition(), saltados, villager.getUUID());
        if (target == null && !saltados.isEmpty()) {
            // Ya no queda nada alcanzable: se olvida la lista de descartados para volver a intentarlo más tarde.
            saltados.clear();
            target = VillageManager.findRepairTarget(level, objectiveIndex, villager.blockPosition(), saltados, villager.getUUID());
        }
        if (target == null) {
            restTicks = IDLE_REST_TICKS; // nada roto: no volver a recorrer el plano hasta dentro de 5 s
            return false;
        }
        // Se reclama el hueco para que otro obrero no vaya al mismo sitio (varios comparten el trabajo).
        if (!VillageManager.reclamarHueco(level, target, villager.getUUID())) {
            // Otro se lo quedó primero: se prueba en el siguiente intento sin gastar el descanso largo.
            restTicks = REST_TICKS;
            target = null;
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        irAlHueco();
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && isBuilder() && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (target == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        // Si el hueco ya no hace falta (lo repuso otro, o el jugador volvió a poner algo), se pasa al siguiente.
        // OJO: no basta con "está en aire": si era tierra de cultivo y alguien la pisoteó (queda tierra), también
        // hay que reponerla (ver VillageManager.necesitaReparacion).
        if (!VillageManager.necesitaReparacion(level, objectiveIndex, target)) {
            // OJO: hay que SOLTAR el reclamo del hueco ANTES de pasar al siguiente. `stop()` solo lo suelta si
            // `target != null`, así que dejándolo a null aquí el hueco se quedaba reservado para los demás obreros
            // hasta que caducara el reclamo (y con varios obreros, eso es trabajo que nadie hace).
            VillageManager.liberarHueco(level, target);
            target = null;
            return;
        }
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D));
        if (distancia > alcanceDe(target)) {
            // Al hueco se va POR EL CEREBRO en cada tick (ver VillageManager.caminarHacia): navegando a mano, el
            // cerebro del aldeano le da otro destino y se va a otra parte.
            // PERO NO SE CAMINA AL HUECO: se camina a UNA CASILLA DE PIE desde la que se alcance (I114/I117). Al hueco
            // —que es una casilla de aire que hay que rellenar— el planificador del juego devuelve una ruta de 1 nodo
            // (`alcanza=NO`) y el obrero NO DA UN PASO: medido, Filomena se rindió en `560,64,587` y `552,63,585` sin
            // moverse de `521,63,612` y `519,63,609`.
            BlockPos sitio = sitioDeCamino(level, target);
            if (sitio != null) {
                // El atasco se mide contra la CASILLA a la que se va, no contra el hueco: el rodeo hasta ella puede
                // empezar alejándose del hueco (la lección de I112). Y el progreso se reinicia cuando cambia el paso.
                double hastaElSitio = Math.sqrt(villager.distanceToSqr(sitio.getX() + 0.5D, sitio.getY() + 0.5D,
                        sitio.getZ() + 0.5D));
                if (!sitio.equals(sitioDeCaminoDe)) {
                    mejorDistancia = Double.MAX_VALUE;
                    stuckTicks = 0;
                }
                VillageManager.caminarHacia(villager, sitio, 0.6F);
                if (hastaElSitio < mejorDistancia - 0.5D) {
                    mejorDistancia = hastaElSitio;
                    stuckTicks = 0;
                } else {
                    stuckTicks++;
                }
                return;
            }
            VillageManager.caminarHacia(villager, target, 0.6F);
            // Solo cuenta como atasco NO ACERCARSE (contar cada tick lo mandaba a empezar de cero a los 5 s).
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            return;
        }
        workTicks = 0;
        // El bloque lo dice VillageManager.bloqueParaReparar: el del PLANO si la celda está en el plano, y si no el del
        // SUELO (hierba arriba, tierra abajo) cuando es un agujero del suelo — el cráter de un creeper, que en el
        // plano no está y antes no lo tapaba nadie.
        BlockState state = VillageManager.bloqueParaReparar(level, objectiveIndex, target);
        if (state != null) {
            // La TIERRA DE CULTIVO se repone REGADA, como la pondría el juego (`FarmBlock.isNearWater`): el plano la
            // guarda sin humedad (es estado transitorio, ver `VillageGenerator.estadoDelPlano`), así que reponerla tal
            // cual la dejaría seca —y al cultivo de encima creciendo más despacio— hasta que el juego se acuerde de
            // regarla sola. Aquí se pregunta por el agua de al lado UNA vez, al colocar (no en cada tick).
            if (state.is(Blocks.FARMLAND)) {
                state = VillageGenerator.tierraDeCultivo(level, target);
            }
            VillageManager.ponerActividad(villager, "Reparando la aldea");
            BlockPos puesto = target;
            level.setBlock(puesto, state, Block.UPDATE_ALL);
            level.playSound(null, puesto, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
            // Lo que acaba de reponer, en la cabeza (además del log): así se ve al obrero trabajar de verdad. El
            // nombre del bloque va en español a mano: la traducción del juego la resolvería el servidor (en inglés).
            VillageManager.ponerSuceso(villager, "Repuso " + VillageManager.nombreEnEspanol(state));
            DevilRpg.LOGGER.debug("[Village] El obrero repuso {} en {}", state.getBlock(), puesto);
        }
        VillageManager.liberarHueco(level, target);
        target = null; // el siguiente hueco lo busca canUse() tras el descanso
    }

    @Override
    public void stop() {
        if (target != null && stuckTicks >= STUCK_LIMIT) {
            saltados.add(target.asLong());
        }
        if (target != null) {
            VillageManager.liberarHueco((ServerLevel) villager.level(), target);
        }
        target = null;
        restTicks = REST_TICKS;
        VillageManager.parar(villager);
    }

    private void irAlHueco() {
        if (target != null && villager.level() instanceof ServerLevel level) {
            BlockPos sitio = sitioDeCamino(level, target);
            VillageManager.caminarHacia(villager, sitio != null ? sitio : target, 0.6F);
        }
    }

    /** La casilla del hueco que se está reparando AHORA ({@link #sitioDeCamino}); sirve para no recalcularla por tick. */
    @Nullable
    private BlockPos sitioDeCaminoDe;

    /** La casilla de pie desde la que se alcanza: a dónde se camina de verdad. */
    @Nullable
    private BlockPos sitioDeCamino;

    /**
     * <b>¿DESDE DÓNDE SE REPARA ESE HUECO?</b> La casilla de pie (aire a los pies y a la cabeza, suelo firme) desde la
     * que el hueco queda dentro del alcance ({@link #alcanceDe}), o {@code null} si no hay ninguna cerca.
     * <p>
     * Se busca <b>una vez por hueco</b> (son 7×7×6 celdas): el resultado se guarda en {@link #sitioDeCamino} mientras el
     * hueco no cambie.
     * <p>
     * <b>Por qué</b> (medido, 25-sep-2026): al obrero se le mandaba a <b>caminar al hueco</b> —una casilla de AIRE que
     * hay que rellenar— y el planificador del juego le devolvía una ruta de <b>1 nodo</b> (`ruta=1 nodos hasta
     * 521,63,612 alcanza=NO`): se quedaba plantado en `521,63,612` con el hueco a 46 bloques y se rendía (Filomena,
     * `560,64,587` y `552,63,585`). Es el mismo patrón de I114 (a un bloque no se camina), aquí en el goal de reparar.
     */
    @Nullable
    private BlockPos sitioDeCamino(ServerLevel level, BlockPos hueco) {
        if (hueco.equals(sitioDeCaminoDe)) {
            return sitioDeCamino;
        }
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        double alcance = alcanceDe(hueco);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos p = hueco.offset(dx, dy, dz);
                    if (!VillageManager.esCeldaDePie(level, p)) {
                        continue;
                    }
                    // Medio bloque de margen: el aldeano no se para justo en el centro de la casilla.
                    double d = Math.sqrt(p.distSqr(hueco));
                    if (d <= alcance - 0.5D && d < mejorDist) {
                        mejorDist = d;
                        mejor = p.immutable();
                    }
                }
            }
        }
        sitioDeCaminoDe = hueco;
        sitioDeCamino = mejor;
        return mejor;
    }

    private boolean isBuilder() {
        return villager.getPersistentData().getBoolean(VillageManager.BUILDER_TAG);
    }

    /**
     * Alcance para colocar <b>ese</b> bloque: el de siempre ({@link #REACH}) más lo que el hueco esté <b>por encima</b>
     * del obrero, que es lo que le permite reponer el <b>techo del kiosco</b> (cota+5) desde el suelo. Los bloques a su
     * altura o por debajo siguen con el alcance de siempre.
     */
    private double alcanceDe(BlockPos objetivo) {
        int dy = Math.max(0, objetivo.getY() - villager.blockPosition().getY());
        return REACH + dy * REACH_EXTRA_POR_ALTURA;
    }
}
