package com.chipoodle.devilrpg.world;

/**
 * <b>F2 · Daño por caída de los aldeanos del pueblo.</b>
 * <p>
 * Un aldeano que vuelve al pueblo por terreno abrupto puede caerse y morir
 * (`Volviendo al pueblo hit the ground too hard`, medido en `729,64,542`). El pueblo no debe perder
 * gente por el terreno que él mismo no sabe rodear. La política es deliberadamente pequeña y pura:
 * <ul>
 *   <li>si el aldeano pertenece a una aldea (<b>miembro</b>), se le protege de la caída;</li>
 *   <li>si la aldea está <b>en asedio</b>, NO se le protege: el combate y las caídas de la defensa
 *       siguen contando (no se regala invulnerabilidad en la pelea).</li>
 * </ul>
 * La membresía se decide fuera (la etiqueta {@link VillageManager#CENTRO_TAG}) y el asedio se consulta
 * con {@link VillageManager#hayAsedioEnLaAldeaDe}. El evento solo aplica esto a {@code Villager}.
 */
public final class VillageFallPolicy {

    private VillageFallPolicy() {
    }

    /**
     * @param aldeanoDelPueblo el aldeano tiene centro de aldea ({@link VillageManager#CENTRO_TAG})
     * @param enAsedio         su aldea está siendo atacada ahora mismo
     * @return {@code true} si se debe cancelar el daño por caída
     */
    public static boolean protegeDeLaCaida(boolean aldeanoDelPueblo, boolean enAsedio) {
        return aldeanoDelPueblo && !enAsedio;
    }
}
