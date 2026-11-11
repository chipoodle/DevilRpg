package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · <b>Máquina de estados de la navegación propia.</b>
 * <p>
 * Cada estado tiene una condición de entrada, un presupuesto de ticks y una única acción de salida. Esto es lo que
 * evita el fallo medido de los rezagados: quedarse indefinidamente reintentando lo mismo ({@code plan=idle} sin
 * objetivo, o {@code local-escape} repitiendo el mismo punto rechazado).
 */
public enum NavState {
    /** Sin objetivo de navegación: no se planifica nada. */
    IDLE,
    /** Hay destino y se está buscando ruta dentro del presupuesto. */
    PLANNING,
    /** Hay ruta y se está siguiendo nodo a nodo. */
    TRAVERSING,
    /** La ruta se ha agotado o el siguiente nodo es intransitable: hay que reclasificar. */
    OBSTRUCTED,
    /** Se está ejecutando una acción de recuperación acotada (romper, puentear, subir, bajar, rodear). */
    RECOVERING,
    /** Se llegó al destino. */
    ARRIVED,
    /** Se agotaron los reintentos: el destino se declara inalcanzable por ahora y se enfría. */
    FAILED;

    public boolean isMoving() {
        return this == TRAVERSING || this == RECOVERING;
    }

    public boolean isTerminal() {
        return this == ARRIVED || this == FAILED;
    }
}
