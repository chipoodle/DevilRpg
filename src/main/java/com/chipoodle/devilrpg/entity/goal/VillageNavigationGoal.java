package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Controlador de navegación por <b>tarea lógica</b>, no por cada recado de
 * sub-paso. Para un granjero la tarea es su parcela asignada; los cambios de
 * destino dentro de la misma parcela no reinician la propiedad.
 */
public class VillageNavigationGoal extends Goal {

    private enum State { UNOWNED, OWNED, MOVING, RECOVERING, ARRIVED, RELEASED }

    private static final int MAX_REPLANS = 3;
    private final Villager villager;
    private @Nullable BlockPos destino;
    private String tarea = "";
    private long recadoId = -1L;
    private String tareaDiagnostico = "";
    private State state = State.UNOWNED;
    private int replans;
    private int ticksSinAvanzar;
    private Vec3 ultimaPosicion = Vec3.ZERO;

    public VillageNavigationGoal(Villager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!VillageManager.NAVEGACION_ACTIVA || villager.isBaby()
                || VillageManager.estaDescansando(villager)) {
            return false;
        }
        String tarea = VillageManager.laTareaDelRecado(villager);
        if (!tarea.equals(tareaDiagnostico)) {
            tareaDiagnostico = tarea;
            DevilRpg.LOGGER.info("[NAV-POLICY] uuid={} tarea={} duenio={} motivo={}",
                    villager.getUUID(), tarea, VillageTaskNavigationPolicy.duenioDe(tarea),
                    VillageTaskNavigationPolicy.esTareaDePosicionDeTrabajo(tarea)
                            ? "posicion-trabajo-fallback" : "elegible");
        }
        return VillageTaskNavigationPolicy.usaNavegacionPropia(tarea)
                && VillageManager.elRecadoDeAhora(villager) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && state != State.ARRIVED && state != State.RELEASED;
    }

    @Override
    public void start() {
        String tareaAhora = VillageManager.laTareaDelRecado(villager);
        boolean mismaTarea = tareaAhora.equals(tarea);
        tarea = tareaAhora;
        recadoId = VillageManager.elIdDelRecado(villager);
        destino = VillageManager.elRecadoDeAhora(villager);
        state = State.OWNED;
        if (!mismaTarea) {
            replans = 0;
            ticksSinAvanzar = 0;
        }
        ultimaPosicion = villager.position();
        DevilRpg.LOGGER.info("[NAV] START uuid={} tarea={} id={} recado={}", villager.getUUID(),
                tarea, recadoId, destino == null ? "-" : destino.toShortString());
    }

    @Override
    public void tick() {
        String tareaAhora = VillageManager.laTareaDelRecado(villager);
        BlockPos recado = VillageManager.elRecadoDeAhora(villager);
        if (!VillageTaskNavigationPolicy.usaNavegacionPropia(tareaAhora) || recado == null) {
            state = State.RELEASED;
            return;
        }
        if (!tareaAhora.equals(tarea)) {
            // Tarea lógica nueva: suelta la ruta anterior y empieza de cero.
            state = State.RELEASED;
            villager.getNavigation().stop();
            tarea = tareaAhora;
            recadoId = VillageManager.elIdDelRecado(villager);
            destino = recado;
            replans = 0;
            ticksSinAvanzar = 0;
            ultimaPosicion = villager.position();
            state = State.OWNED;
            return;
        }
        recadoId = VillageManager.elIdDelRecado(villager);
        if (destino == null || !destino.equals(recado)) {
            // Nuevo sub-paso de la MISMA tarea: se actualiza el destino sin
            // soltar la propiedad ni reiniciar el estado de la tarea.
            destino = recado;
            ultimaPosicion = villager.position();
            villager.getNavigation().stop();
            state = State.MOVING;
        }

        if (villager.blockPosition().distSqr(recado) <= 16) {
            state = State.ARRIVED;
            DevilRpg.LOGGER.info("[NAV] ARRIVE uuid={} tarea={} id={} recado={} pos={}",
                    villager.getUUID(), tarea, recadoId, recado.toShortString(),
                    villager.blockPosition().toShortString());
            // La llegada a un sub-paso NO completa la tarea: no se borra el recado.
            state = State.ARRIVED;
            return;
        }

        if (villager.position().distanceToSqr(ultimaPosicion) > 1.0D) {
            ticksSinAvanzar = 0;
            replans = 0;
            ultimaPosicion = villager.position();
            state = State.MOVING;
        } else if (++ticksSinAvanzar > VillageManager.RECADO_PRESUPUESTO) {
            state = State.RELEASED;
            DevilRpg.LOGGER.info("[NAV] ABANDON uuid={} tarea={} id={} recado={} ticks={}",
                    villager.getUUID(), tarea, recadoId, recado.toShortString(), ticksSinAvanzar);
            VillageManager.abandonarElRecado(villager);
            return;
        }

        var nav = villager.getNavigation();
        if (nav.getPath() == null || nav.getPath().isDone()) {
            if (replans >= MAX_REPLANS) {
                state = State.RELEASED;
                DevilRpg.LOGGER.info("[NAV] ABANDON-REPLANS uuid={} tarea={} id={} recado={}",
                        villager.getUUID(), tarea, recadoId, recado.toShortString());
                VillageManager.abandonarElRecado(villager);
                return;
            }
            state = State.RECOVERING;
            nav.moveTo(recado.getX() + 0.5D, recado.getY(), recado.getZ() + 0.5D, 0.6D);
            replans++;
            state = State.MOVING;
        }
    }

    @Override
    public void stop() {
        villager.getNavigation().stop();
        state = State.RELEASED;
        destino = null;
        // Se conserva `tarea` a propósito: al reanudar la misma parcela no se
        // reinician los contadores ni se trata como tarea nueva.
        ticksSinAvanzar = 0;
    }
}
