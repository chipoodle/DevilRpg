package com.chipoodle.devilrpg.world;

/**
 * C4 · ventana de gracia de carga de la aldea.
 * <p>
 * Al abrir un mundo los aldeanos persistidos tardan en cargarse; el conteo por radio los ve incompletos y el
 * mantenimiento reponía puestos «vacíos» creando un aldeano nuevo que reclamaba el POI de un trabajador
 * persistido (medido en {@code c4n-20261123-015}). Esta política decide, de forma pura, si la aldea ya puede
 * repoblarse: solo cuando han pasado los ticks de gracia desde el primer latido de vida del servidor.
 */
public final class VillagePopulationLoadPolicy {

    private VillagePopulationLoadPolicy() {
    }

    /**
     * @param ahora      tick de juego actual
     * @param primerTick primer latido observado tras el arranque ({@code < 0} = aún no observado)
     * @param gracia     ticks de gracia
     * @return {@code true} si ya se puede repoblar
     */
    public static boolean cargaAsentada(final long ahora, final long primerTick, final int gracia) {
        if (gracia <= 0 || primerTick < 0L) {
            return true; // sin ventana configurada no se bloquea nada
        }
        if (ahora < primerTick) {
            return false; // reloj incoherente: se espera
        }
        return ahora - primerTick >= gracia;
    }
}
