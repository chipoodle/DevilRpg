package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 REMEDIACIÓN 2 · <b>caché negativa con reintento acotado</b>.
 *
 * <p>Un resultado "no hay destino" no puede quedar grabado para siempre: la causa suele ser temporal (chunk
 * aún no cargado, celda ocupada, fluido). Esta política permite reintentar cada {@value #RETRY_TICKS} ticks
 * sin repetir la búsqueda cara en cada tick. Al encontrar un destino, se limpia.
 */
public final class NegativeDestinationCache {

    /** Intervalo mínimo entre búsquedas fallidas (5 s a 20 tps). */
    public static final int RETRY_TICKS = 100;

    private boolean buscado;
    private long proximoIntento;
    private int intentosFallidos;

    public boolean puedeBuscar(final long ahora) {
        return !buscado || ahora >= proximoIntento;
    }

    public void fallo(final long ahora) {
        buscado = true;
        intentosFallidos++;
        proximoIntento = ahora + RETRY_TICKS;
    }

    public void exito() {
        buscado = false;
        proximoIntento = 0L;
        intentosFallidos = 0;
    }

    public void reiniciar() {
        exito();
    }

    public int intentosFallidos() {
        return intentosFallidos;
    }
}
