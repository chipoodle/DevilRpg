package com.chipoodle.devilrpg.world;

/**
 * <b>Condición de victoria del asedio por la campana.</b>
 * <p>
 * La campana del kiosco es el centro estratégico: si un asaltante la destruye, la aldea está
 * conquistada. Antes el asedio seguía esperando a que la ola muriera o al plazo, así que una aldea
 * con la campana rota podía declararse salvada y pagar recompensa. La regla es explícita para que la
 * usen el asedio clásico, la horda del mundo y la reparación de partidas viejas.
 */
public final class VillageSiegeBellPolicy {

    private VillageSiegeBellPolicy() {
    }

    public static boolean laAldeaCaePorCampana(boolean campanaDestruida, boolean aldeaYaCaida) {
        return campanaDestruida && !aldeaYaCaida;
    }
}
