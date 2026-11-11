package com.chipoodle.devilrpg.entity.goal;

/**
 * Estado de trabajo de UN leñador. Vive en el goal (no es estático ni compartido): dos aldeanos no pueden reclamar la
 * misma asignación por accidente.
 *
 * <p>Separa la <b>selección</b> de tarea de la <b>ejecución</b>: mientras no hay árbol asignado el controlador está en
 * {@link State#SCOUTING}; al asignar un objetivo pasa a {@link State#TRAVELLING} y conserva las coordenadas hasta que
 * el goal las complete, las invalide o las abandone por el límite de atasco existente.
 */
public final class ForestryTaskController {

    public enum State {
        SCOUTING,
        TRAVELLING,
        HARVESTING,
        REPLANTING,
        GROWING,
        DELIVERING,
        RETURNING
    }

    private State state = State.SCOUTING;
    private int targetX;
    private int targetY;
    private int targetZ;

    public State state() {
        return state;
    }

    public boolean hasAssignment() {
        return state != State.SCOUTING;
    }

    /** Restaura una asignación ya validada. */
    public void restore(State restored, int x, int y, int z) {
        this.state = restored == null ? State.SCOUTING : restored;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    public void scouting() {
        state = State.SCOUTING;
    }

    public void assign(final int x, final int y, final int z) {
        targetX = x;
        targetY = y;
        targetZ = z;
        state = State.TRAVELLING;
    }

    public int targetX() {
        return targetX;
    }

    public int targetY() {
        return targetY;
    }

    public int targetZ() {
        return targetZ;
    }

    public void travelling() {
        if (hasAssignment()) {
            state = State.TRAVELLING;
        }
    }

    public void harvesting() {
        if (hasAssignment()) {
            state = State.HARVESTING;
        }
    }

    public void replanting() {
        if (hasAssignment()) {
            state = State.REPLANTING;
        }
    }

    public void growing() {
        if (hasAssignment()) {
            state = State.GROWING;
        }
    }

    public void delivering() {
        state = State.DELIVERING;
    }

    public void returning() {
        state = State.RETURNING;
    }

    /** El trabajo terminó o se invalidó: vuelve a explorar sin conservar un objetivo viejo. */
    public void complete() {
        state = State.SCOUTING;
    }

    public boolean ownsNavigation() {
        return state == State.TRAVELLING || state == State.HARVESTING || state == State.REPLANTING
                || state == State.GROWING || state == State.DELIVERING || state == State.RETURNING;
    }
}
