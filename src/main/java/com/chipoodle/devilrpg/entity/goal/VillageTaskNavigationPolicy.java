package com.chipoodle.devilrpg.entity.goal;

/**
 * Política semántica de propiedad de navegación. No usa distancia ni
 * coordenadas: una tarea agrícola pertenece siempre al camino de respaldo del
 * granjero. El controlador propio queda para tareas no agrícolas.
 */
public final class VillageTaskNavigationPolicy {

    public enum Owner {
        CUSTOM_NAVIGATION,
        PROFESSION_FALLBACK
    }

    private VillageTaskNavigationPolicy() {
    }

    public static boolean esTareaAgricola(String tarea) {
        return tarea != null && tarea.startsWith("farm:");
    }

    /** Tareas cuyo destino es una posición de trabajo exacta, no un simple recado. */
    public static boolean esTareaDePosicionDeTrabajo(String tarea) {
        return esTareaAgricola(tarea) || (tarea != null && tarea.startsWith("mine:"));
    }

    public static boolean usaNavegacionPropia(String tarea) {
        return tarea != null && !tarea.isEmpty() && !esTareaDePosicionDeTrabajo(tarea);
    }

    public static Owner duenioDe(String tarea) {
        return usaNavegacionPropia(tarea) ? Owner.CUSTOM_NAVIGATION : Owner.PROFESSION_FALLBACK;
    }
}
