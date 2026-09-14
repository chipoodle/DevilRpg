package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
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

    /** Distancia a la que el aldeano ya llega a colocar el bloque. */
    private static final double REACH = 4.5D;
    /** Ticks "trabajando" antes de colocar (medio segundo): se le ve dar el golpe, pero sin eternizarse. */
    private static final int WORK_TICKS = 10;
    /** Descanso entre bloque y bloque (medio segundo). Antes eran 2 s y el obrero tardaba una eternidad. */
    private static final int REST_TICKS = 10;
    /**
     * Descanso cuando NO hay nada que reparar (5 s). Importante: buscar huecos recorre el plano entero leyendo
     * bloques, así que no se puede hacer en cada tick.
     */
    private static final int IDLE_REST_TICKS = 100;
    /** Si el obrero se aleja más de esto del centro de la aldea, deja de trabajar (no se pierde por el mundo). */
    private static final double MAX_DISTANCE_FROM_CENTER = 48.0D;
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
        if (villager.blockPosition().distSqr(center) > MAX_DISTANCE_FROM_CENTER * MAX_DISTANCE_FROM_CENTER) {
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
        irAlHueco();
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && isBuilder() && !villager.isBaby() && stuckTicks < STUCK_LIMIT;
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
            target = null;
            return;
        }
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) > REACH * REACH) {
            if (villager.getNavigation().isDone()) {
                stuckTicks++;
                irAlHueco();
            }
            return;
        }
        villager.getNavigation().stop();
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            return;
        }
        workTicks = 0;
        BlockState state = VillageManager.blueprintState(level, objectiveIndex, target);
        if (state != null) {
            BlockPos puesto = target;
            level.setBlock(puesto, state, Block.UPDATE_ALL);
            level.playSound(null, puesto, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
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
        villager.getNavigation().stop();
    }

    private void irAlHueco() {
        if (target != null) {
            villager.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.6D);
        }
    }

    private boolean isBuilder() {
        return villager.getPersistentData().getBoolean(VillageManager.BUILDER_TAG);
    }
}
