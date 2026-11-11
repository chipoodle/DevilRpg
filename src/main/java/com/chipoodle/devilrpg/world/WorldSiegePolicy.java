package com.chipoodle.devilrpg.world;

/**
 * A2 REMEDIACIÓN 2 · <b>Política pura del asedio del mundo</b>.
 *
 * <p>Un asedio del mundo no lo dispara un jugador y no tiene la pausa del asedio clásico. Antes podía quedar
 * registrado para siempre si sus atacantes se descargaban, y además se infería su muerte desde la última celda
 * conocida (una posición obsoleta). Esta política fija una regla determinista:
 * <ul>
 *   <li>Ola confirmada como limpia (todas sus bajas fueron descontadas) → la aldea resistió.</li>
 *   <li>Aldea sin aldeanos y con un jugador delante → la aldea cae.</li>
 *   <li>Si no se puede confirmar nada y se agota el plazo → se cierra <b>SIN victoria y SIN recompensa</b>.</li>
 * </ul>
 * El plazo se mide en ticks de juego desde el registro y no depende del jugador ni de los chunks.
 */
public final class WorldSiegePolicy {

    /** Plazo determinista: 10 minutos de juego desde el registro del asedio del mundo. */
    public static final long TIMEOUT_TICKS = 10L * 60L * 20L;

    public enum Outcome {
        /** Aún no toca resolver. */
        NONE,
        /** Ola confirmada: la aldea resistió (puede pagar recompensa a los defensores). */
        RESISTED_CONFIRMED,
        /** Aldea sin aldeanos y con jugador delante: cae. */
        FALLEN,
        /** Plazo agotado con la ola todavía activa: cerrar sin victoria ni recompensa. */
        TIMEOUT_ACTIVE,
        /** Plazo agotado sin poder confirmar el estado de la ola: cerrar sin victoria ni recompensa. */
        TIMEOUT_UNCONFIRMED
    }

    private WorldSiegePolicy() {
    }

    /**
     * @param waveConfirmed todas las bajas de la ola están confirmadas (normalmente lista vacía)
     * @param waveActive    hay al menos un asediador cargado y vivo
     * @param villageFallen la aldea no tiene aldeanos y hay un jugador delante
     * @param tickTicks     ticks de juego desde el registro del asedio
     */
    public static Outcome decidir(final boolean waveConfirmed, final boolean waveActive,
                                  final boolean villageFallen, final long tickTicks) {
        if (waveConfirmed) {
            return Outcome.RESISTED_CONFIRMED;
        }
        if (villageFallen) {
            return Outcome.FALLEN;
        }
        if (tickTicks >= TIMEOUT_TICKS) {
            return waveActive ? Outcome.TIMEOUT_ACTIVE : Outcome.TIMEOUT_UNCONFIRMED;
        }
        return Outcome.NONE;
    }
}
