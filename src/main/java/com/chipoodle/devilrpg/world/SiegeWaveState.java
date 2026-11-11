package com.chipoodle.devilrpg.world;

import java.util.List;

/**
 * A2 FASES 5/6 · <b>Estado puro de una ola de asedio</b>.
 *
 * <p>La distincion que faltaba: {@code level.getEntity(uuid) == null} NO es "muerto". Una entidad puede estar
 * temporalmente descargada. Este tipo obliga a clasificar cada UUID en una de tres categorias observables y
 * deriva de ellas el estado de la ola, sin tocar Minecraft (se puede probar con un fixture puro).
 *
 * <ul>
 *   <li>{@link EntityState#VIVA}: cargada y viva.</li>
 *   <li>{@link EntityState#MUERTA_O_AUSENTE_CONFIRMADA}: muerta observada, o su ultima celda conocida sigue
 *       cargada y la entidad ya no existe (baja/retirada real).</li>
 *   <li>{@link EntityState#DESCONOCIDA}: su chunk esta descargado o no hay posicion: NO se puede afirmar nada.</li>
 * </ul>
 */
public final class SiegeWaveState {

    /** Estado observable de un asediador concreto. */
    public enum EntityState {
        VIVA,
        MUERTA_O_AUSENTE_CONFIRMADA,
        DESCONOCIDA
    }

    /** Estado derivado de la ola completa. */
    public enum WaveState {
        /** La ola nunca nacio (0 insertados): NO puede producir victoria. */
        NUNCA_NACIDA,
        /** Nacio y todas sus entidades estan confirmadas como bajas: victoria legitima. */
        LIMPIA_CONFIRMADA,
        /** Queda al menos una entidad cargada y viva. */
        ACTIVA,
        /** Nacio y quedan UUID cuyo estado no se puede observar (descargados): NO se declara limpia. */
        SIN_CONFIRMAR
    }

    private SiegeWaveState() {
    }

    /**
     * Deriva el estado de la ola. Una lista vacia solo es "limpia" si la ola NACIO ({@code waveSpawned});
     * si no nacio, es {@link WaveState#NUNCA_NACIDA} y jamas debe pagar recompensa.
     */
    public static WaveState evaluar(final boolean waveSpawned, final List<EntityState> estados) {
        if (!waveSpawned) {
            return WaveState.NUNCA_NACIDA;
        }
        if (estados == null || estados.isEmpty()) {
            return WaveState.LIMPIA_CONFIRMADA;
        }
        boolean algunaDesconocida = false;
        for (EntityState e : estados) {
            if (e == EntityState.VIVA) {
                return WaveState.ACTIVA;
            }
            if (e == EntityState.DESCONOCIDA) {
                algunaDesconocida = true;
            }
        }
        return algunaDesconocida ? WaveState.SIN_CONFIRMAR : WaveState.LIMPIA_CONFIRMADA;
    }

    /** Solo una ola nacida y confirmada como limpia puede pagar la recompensa de victoria. */
    public static boolean victoriaConRecompensa(final WaveState estado) {
        return estado == WaveState.LIMPIA_CONFIRMADA;
    }

    /** Una ola sin confirmar se cierra como "salvada sin recompensa", nunca como victoria limpia. */
    public static boolean salvacionSinRecompensa(final WaveState estado) {
        return estado == WaveState.SIN_CONFIRMAR;
    }
}
