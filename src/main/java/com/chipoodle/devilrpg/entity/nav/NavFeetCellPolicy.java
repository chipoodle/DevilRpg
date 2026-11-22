package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · <b>¿Es esta celda una posición de PIE válida?</b>
 * <p>
 * La pregunta se responde por <b>colisión</b>, no por {@code isAir()}. Un bloque sin colisión en la celda de los
 * pies (capa de nieve de 1, hierba alta, helecho, antorcha...) NO impide estar de pie: la entidad descansa sobre el
 * bloque sólido de debajo. Exigir {@code isAir()} dejaba "inválidas" todas las celdas de un rellano nevado y ninguna
 * recuperación podía elegir una fuente (medido en `a2n-20261121-213055`: IDX2 termina FAILED en `660,72,608`,
 * encima de una capa de nieve, con las 8 vecinas también nevadas).
 * <p>
 * Siguen rechazándose, como siempre: fluidos, bloques peligrosos aunque no colisionen (fuego, lava, magma, cactus,
 * zarzas, nieve polvo, rosa de wither), celdas sin soporte sólido debajo y celdas protegidas por la aldea.
 */
public final class NavFeetCellPolicy {
    private NavFeetCellPolicy() {
    }

    public static boolean isValid(boolean chunkLoaded, boolean protectedCell,
                                  boolean feetCollides, boolean headCollides,
                                  boolean feetHazard, boolean headHazard,
                                  boolean support) {
        return chunkLoaded
                && !protectedCell
                && !feetCollides
                && !headCollides
                && !feetHazard
                && !headHazard
                && support;
    }
}
