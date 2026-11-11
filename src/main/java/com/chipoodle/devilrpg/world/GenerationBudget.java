package com.chipoodle.devilrpg.world;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A2 REMEDIACIÓN 2 · <b>cupo compartido de generación por tick</b>.
 *
 * <p>La generación de una aldea completa es síncrona y costosa. `ObjectiveManager` y `manageNearby` podían
 * reclamar cada uno su propia aldea en el mismo tick del jugador. Este cupo, por nivel y tick de juego,
 * permite exactamente una concesión; el resto se difiere al tick siguiente (la marca `isGenerated` es
 * persistente, así que no se pierde trabajo).
 *
 * <p>Las claves son débiles: un {@code ServerLevel} descargado no queda retenido por este cupo.
 */
public final class GenerationBudget {

    private final Map<Object, Long> ultimoTickPorNivel = new WeakHashMap<>();

    /** Intenta reservar el cupo de este nivel para este tick. Devuelve false si ya se usó. */
    public boolean tryClaim(final Object levelKey, final long gameTick) {
        if (levelKey == null) {
            return false;
        }
        Long anterior = ultimoTickPorNivel.get(levelKey);
        if (anterior != null && anterior == gameTick) {
            return false;
        }
        ultimoTickPorNivel.put(levelKey, gameTick);
        return true;
    }

    /** Solo para pruebas/diagnóstico: limpia el estado. */
    public void clear() {
        ultimoTickPorNivel.clear();
    }
}
