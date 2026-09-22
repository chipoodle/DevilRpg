package com.chipoodle.devilrpg.survival;

/**
 * <b>El NOMBRE de cada aldea</b>, determinista por el índice del objetivo: la misma aldea se llama siempre igual en
 * el servidor y en el cliente <b>sin sincronizar nada</b> (misma idea que {@link ObjectiveTargets} con las
 * coordenadas). Lo pidió el jugador: *"ya es necesario que cada aldea tenga su nombre (respetando el lore) y que
 * arriba donde está la barra de objetivos, que no diga objetivo 1, 2 etc, sino aldea, y cuando se descubra que diga
 * su nombre"*.
 * <p>
 * La tabla es de <b>nombres rústicos</b>, en la misma cuerda que los nombres de los aldeanos ({@code NOMBRES} de
 * {@code VillageManager}: Zacarías, Dorotea, Hipólito…): aldeas de labranza y piedra, no de fantasía.
 * <p>
 * <b>PENDIENTE</b>: el jugador va a pasar su propia lista; se pega aquí y no hay que tocar nada más (el índice manda:
 * la aldea 0 es el primer nombre de la tabla, la 1 el segundo…). Si alguna vez hay más aldeas que nombres, se repiten
 * con numeral romano ({@code Valdehierro II}), que nunca deja dos aldeas con el mismo nombre.
 */
public final class VillageNames {

    /** Nombres de aldea, en orden de objetivo. (Tabla provisional a la espera de la lista del jugador.) */
    private static final String[] NOMBRES = {
            "Aldea de Valdehierro",
            "Aldea de Fuenteclara",
            "Aldea de Robledal",
            "Aldea de Peñasalbas",
            "Aldea de Villaseca",
            "Aldea de Altamira",
            "Aldea de Prado Verde",
            "Aldea de las Espigas",
            "Aldea de Cantarranas",
            "Aldea del Otero",
            "Aldea de Molino Viejo",
            "Aldea de la Alameda",
            "Aldea de Piedrahita",
            "Aldea de los Olmos",
            "Aldea de Valdeprado",
            "Aldea de Santa Coloma",
    };

    /** Numerales para cuando la partida pase de la tabla (la aldea 17 vuelve a empezar por el primer nombre). */
    private static final String[] NUMERALES = {
            "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X",
    };

    private VillageNames() {
    }

    /**
     * Nombre de la aldea del objetivo {@code objectiveIndex}. Determinista y estable para siempre: no depende de la
     * partida, ni del bioma, ni de nada que haga falta guardar.
     */
    public static String nombre(int objectiveIndex) {
        int i = Math.max(0, objectiveIndex);
        String base = NOMBRES[i % NOMBRES.length];
        int vuelta = i / NOMBRES.length;
        return base + (vuelta < NUMERALES.length ? NUMERALES[vuelta] : " (" + (vuelta + 1) + ")");
    }

    /** Cuántos nombres distintos hay (para saber si una partida ya ha dado la vuelta a la tabla). */
    public static int cuantos() {
        return NOMBRES.length;
    }
}
