package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * A2 · <b>Contrato puro de ejecución de un plan de túnel.</b>
 * <p>
 * {@link TunnelActionPlanner} solo produce pasos validados; este contrato fija en qué orden puede actuar el
 * ejecutor y, sobre todo, qué NO puede hacer:
 * <ul>
 *   <li>el paso se consume <b>únicamente</b> cuando la ocupación física de {@code post} se ha verificado;</li>
 *   <li>un break no verificado no avanza de fase ni consume el paso;</li>
 *   <li>un movimiento no verificado no consume el paso;</li>
 *   <li>un paso ya verificado no se repite: el cursor avanza y la fase vuelve a pies del paso siguiente.</li>
 * </ul>
 * No toca el mundo ni la entidad: es la pieza que las pruebas deterministas pueden ejercitar sin servidor.
 */
public final class TunnelExecutionPolicy {
    private TunnelExecutionPolicy() {
    }

    /** Acciones ordenadas de un paso de túnel: primero pies, después cabeza y solo entonces el movimiento. */
    public enum Phase {
        BREAK_FEET,
        BREAK_HEAD,
        MOVE_POST
    }

    /** Cursor inmutable: paso del plan en curso y fase dentro de ese paso. */
    public record Cursor(int planSize, int stepIndex, Phase phase) {
        public Cursor {
            if (planSize < 0 || stepIndex < 0 || phase == null) {
                throw new IllegalArgumentException("cursor de tunel invalido");
            }
        }

        public boolean complete() {
            return planSize <= 0 || stepIndex >= planSize;
        }

        public boolean breakPhase() {
            return phase == Phase.BREAK_FEET || phase == Phase.BREAK_HEAD;
        }
    }

    public static Cursor start(int planSize) {
        return new Cursor(Math.max(0, planSize), 0, Phase.BREAK_FEET);
    }

    /**
     * Resultado de un break. Solo un bloque que quedó Aire avanza de fase; un fallo devuelve el MISMO cursor,
     * de modo que el paso no puede consumirse y {@code post} no se alcanza.
     */
    public static Cursor afterBreak(Cursor cursor, boolean airVerified) {
        if (cursor == null || cursor.complete() || !airVerified) {
            return cursor;
        }
        return switch (cursor.phase()) {
            case BREAK_FEET -> new Cursor(cursor.planSize(), cursor.stepIndex(), Phase.BREAK_HEAD);
            case BREAK_HEAD -> new Cursor(cursor.planSize(), cursor.stepIndex(), Phase.MOVE_POST);
            case MOVE_POST -> cursor;
        };
    }

    /**
     * Resultado de un movimiento. Solo la ocupación física verificada de {@code post} consume el paso; un
     * movimiento fallido conserva el cursor (no se salta el paso ni se da por abierto el siguiente).
     */
    public static Cursor afterMove(Cursor cursor, boolean occupiedVerified) {
        if (cursor == null || cursor.complete() || cursor.phase() != Phase.MOVE_POST || !occupiedVerified) {
            return cursor;
        }
        return new Cursor(cursor.planSize(), cursor.stepIndex() + 1, Phase.BREAK_FEET);
    }

    /** Bloque exacto que la fase actual puede romper, o {@code null} si toca moverse. */
    public static BlockPos breakTarget(Cursor cursor, List<TunnelActionPlanner.Step> plan) {
        TunnelActionPlanner.Step step = activeStep(cursor, plan);
        if (step == null || cursor.phase() == Phase.MOVE_POST) {
            return null;
        }
        return cursor.phase() == Phase.BREAK_FEET ? step.feetBlock() : step.headBlock();
    }

    /**
     * El otro bloque de la MISMA apertura de 2 de alto. La línea de visión del break de pies puede cruzar el
     * bloque de cabeza (que forma parte del mismo paso y se rompe en el mismo paso), pero nunca un tercer bloque.
     */
    public static BlockPos stepSibling(Cursor cursor, List<TunnelActionPlanner.Step> plan) {
        TunnelActionPlanner.Step step = activeStep(cursor, plan);
        if (step == null || cursor.phase() == Phase.MOVE_POST) {
            return null;
        }
        return cursor.phase() == Phase.BREAK_FEET ? step.headBlock() : step.feetBlock();
    }

    /** Celda de pie resultante del paso actual, o {@code null} si el plan ya terminó. */
    public static BlockPos postCell(Cursor cursor, List<TunnelActionPlanner.Step> plan) {
        TunnelActionPlanner.Step step = activeStep(cursor, plan);
        return step == null ? null : step.post();
    }

    /** Celda desde la que se actúa el paso actual, o {@code null} si el plan ya terminó. */
    public static BlockPos standCell(Cursor cursor, List<TunnelActionPlanner.Step> plan) {
        TunnelActionPlanner.Step step = activeStep(cursor, plan);
        return step == null ? null : step.stand();
    }

    /**
     * Guarda de línea de visión del túnel: el primer bloque golpeado debe ser el objetivo pedido o el otro
     * bloque de la misma apertura. Un tercer bloque (o un rayo sin impacto) rechaza la acción.
     */
    public static boolean lineOfSightAccepted(BlockPos firstHit, BlockPos requested, BlockPos sibling) {
        if (requested == null) {
            return false;
        }
        return requested.equals(firstHit) || (sibling != null && sibling.equals(firstHit));
    }

    private static TunnelActionPlanner.Step activeStep(Cursor cursor, List<TunnelActionPlanner.Step> plan) {
        if (cursor == null || plan == null || cursor.complete() || cursor.stepIndex() >= plan.size()) {
            return null;
        }
        return plan.get(cursor.stepIndex());
    }
}
