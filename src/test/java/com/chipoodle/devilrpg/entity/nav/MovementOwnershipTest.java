package com.chipoodle.devilrpg.entity.nav;

/** A2 REMEDIACIÓN 2 · prueba pura de la propiedad única del movimiento. */
public final class MovementOwnershipTest {

    private static int fallos;

    public static void main(String[] args) {
        comprobar("nav propia sin combate: ejecutor mueve", MovementOwnership.executorMayMove(true, false));
        comprobar("nav propia con combate: ejecutor se suspende", !MovementOwnership.executorMayMove(true, true));
        comprobar("sin nav propia: ejecutor no mueve", !MovementOwnership.executorMayMove(false, false));
        comprobar("sin nav propia con combate: ejecutor no mueve", !MovementOwnership.executorMayMove(false, true));

        if (fallos > 0) {
            System.out.println("MOVEMENT_OWNERSHIP_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("MOVEMENT_OWNERSHIP_TEST: OK (todas las aserciones verdes)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + nombre);
        } else {
            System.out.println("  ok: " + nombre);
        }
    }
}
