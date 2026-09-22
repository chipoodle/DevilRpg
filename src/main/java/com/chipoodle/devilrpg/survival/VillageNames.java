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

    /**
     * Nombres de aldea, en orden de objetivo, <b>con el lore que escribió el jugador</b> (lo pasó agrupado en tres
     * bloques y en ese orden: primero las del bosque, después las de espíritus y animales, y al final las oscuras).
     * Ese orden <b>no es casual</b>: encaja con la escalada del mod —cuanto más lejos del círculo ritual, más podrida
     * está la tierra—, así que las primeras aldeas son verdes y las últimas son ceniza y niebla. La aldea 0 es la
     * primera de la lista y la 29 la última; a partir de ahí se repiten con numeral (ver {@link #NUMERALES}).
     */
    private static final String[] NOMBRES = {
            // --- Aldeas del bosque ---
            "Aldea de Valleverde",        // un asentamiento protegido por antiguos bosques
            "Aldea de Raíz Profunda",     // construido sobre un bosque antiquísimo
            "Aldea de Robledal",          // sencillo y clásico
            "Aldea de Hojaverde",         // pequeña aldea de recolectores y herbolarios
            "Aldea de Bosquealto",        // situada en las laderas de una montaña boscosa
            "Aldea de Brumavalle",        // constantemente cubierta por niebla
            "Aldea de Lunaverde",         // asociada con la magia nocturna del bosque
            "Aldea de Florumbría",        // flores que crecen incluso en lugares oscuros
            "Aldea de Rocío Viejo",       // rodeada de plantas medicinales
            "Aldea de Verdeniebla",       // asentamiento oculto entre árboles y niebla
            // --- Espíritus y animales ---
            "Aldea de Colmillo Gris",     // antigua comunidad de cazadores
            "Aldea de Lobomonte",         // cerca de las montañas donde habitan los lobos
            "Aldea de Aullaluna",         // veneran a los lobos espirituales
            "Aldea de Garra de Roble",    // mezcla de fuerza animal y naturaleza
            "Aldea de Piedra del Oso",    // construida alrededor de un antiguo monolito
            "Aldea de Manada Gris",       // sobrevivió gracias a los lobos
            "Aldea de Valle del Aullido", // nombre más oscuro y legendario
            "Aldea de Bosque del Espíritu", // donde los animales muertos regresan como guardianes
            // --- Misteriosas y oscuras ---
            "Aldea de Hongo Negro",       // cerca de cavernas infestadas de hongos
            "Aldea de Raíz Sombría",      // en el límite de las tierras corrompidas
            "Aldea de Umbraverde",        // bosque que permanece verde pese a la corrupción
            "Aldea de Valle Marchito",    // antiguo asentamiento parcialmente destruido por los no muertos
            "Aldea de Espinavieja",       // rodeada por zarzas enormes
            "Aldea de Morterra",          // tierra donde los muertos comenzaron a levantarse
            "Aldea de Brumagrís",         // permanentemente cubierta de niebla
            "Aldea de Raíz de Luna",      // famosa por sus plantas que solo florecen de noche
            "Aldea de Cenizal",           // reconstruida después de una invasión de muertos vivientes
            "Aldea de Sombra del Roble",  // construida alrededor de un árbol ancestral
            "Aldea de Páramo Verde",      // sobrevivió milagrosamente a la corrupción
            "Aldea de Lúgubria",          // nombre antiguo de una región maldita
    };

    /** Numerales para cuando la partida pase de la tabla (la aldea 30 vuelve a empezar por el primer nombre). */
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
