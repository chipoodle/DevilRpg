package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 REMEDIACIÓN 2 · <b>Presupuesto finito de escapes tácticos</b>.
 *
 * <p>Salir del agua no puede convertirse en un bucle de "busco otra orilla" cada 200 ticks. Este contador
 * puro limita los intentos por episodio de agua; al agotarse, el goal deja de pedir destinos tácticos y el
 * ejecutor retoma la misión. Se reinicia solo cuando el ente sale del agua (episodio nuevo).
 */
public final class TacticalEscapeBudget {

    private final int maxIntentos;
    private int intentos;

    public TacticalEscapeBudget(final int maxIntentos) {
        this.maxIntentos = Math.max(1, maxIntentos);
    }

    /** Registra un intento. Devuelve false si ya no quedan. */
    public boolean intentar() {
        if (agotado()) {
            return false;
        }
        intentos++;
        return true;
    }

    public boolean agotado() {
        return intentos >= maxIntentos;
    }

    public int intentos() {
        return intentos;
    }

    public int maxIntentos() {
        return maxIntentos;
    }

    public void reiniciar() {
        intentos = 0;
    }
}
