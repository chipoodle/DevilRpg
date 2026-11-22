package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · <b>Veto de madera en la planificación de BREAK.</b>
 * <p>
 * El veto de árboles existía para no talar el bosque al abrirse paso. Pero se aplicaba a TODO {@code LOGS},
 * incluidos los troncos del <b>anillo del muro de la aldea</b>, que {@code breakBlockAt} sí acepta cuando la celda
 * es muralla (la excepción medida: «el muro perimetral sí es la brecha»). Resultado medido en
 * `a2n-20261121-213055`: el planificador solo podía proponer BREAK en las columnas de adoquín (1 de cada 4 celdas
 * del anillo) o en el portón; los tres asaltantes que entraron lo hicieron exactamente por columnas de adoquín.
 * <p>
 * La regla pura: las hojas siempre se vetas; un tronco se veta salvo que sea una celda del anillo del muro.
 */
public final class NavBreakFilterPolicy {
    private NavBreakFilterPolicy() {
    }

    public static boolean vetoWood(boolean leaves, boolean log, boolean wallRingCell) {
        return leaves || (log && !wallRingCell);
    }
}
