package com.chipoodle.devilrpg.world;

import java.util.List;

/**
 * A2 FASES 5/6 · prueba enfocada (sin servidor) del ciclo de vida de una ola:
 * una ola no nacida no gana, una ola con entidades descargadas no se declara limpia, y una ola nacida y
 * confirmada como baja si termina. Se ejecuta con {@code javac}+{@code java}.
 */
public final class SiegeWaveStateTest {

    private static int fallos;

    public static void main(String[] args) {
        // 1. Ola NO nacida (0 insertados): nunca victoria.
        comprobar("ola no nacida != limpia",
                SiegeWaveState.evaluar(false, List.of()) == SiegeWaveState.WaveState.NUNCA_NACIDA);
        comprobar("ola no nacida no paga recompensa",
                !SiegeWaveState.victoriaConRecompensa(SiegeWaveState.evaluar(false, List.of())));

        // 2. Ola nacida y todos sus UUID descontados: limpia legitima.
        comprobar("ola nacida y vacia es limpia",
                SiegeWaveState.evaluar(true, List.of()) == SiegeWaveState.WaveState.LIMPIA_CONFIRMADA);

        // 3. Ola con un vivo: activa.
        comprobar("ola con un vivo sigue activa",
                SiegeWaveState.evaluar(true, List.of(SiegeWaveState.EntityState.VIVA))
                        == SiegeWaveState.WaveState.ACTIVA);

        // 4. Ola con bajas confirmadas: limpia.
        comprobar("ola con bajas confirmadas es limpia",
                SiegeWaveState.evaluar(true, List.of(
                        SiegeWaveState.EntityState.MUERTA_O_AUSENTE_CONFIRMADA,
                        SiegeWaveState.EntityState.MUERTA_O_AUSENTE_CONFIRMADA))
                        == SiegeWaveState.WaveState.LIMPIA_CONFIRMADA);

        // 5. Entidad descargada: NO se declara limpia (ni victoria ni derrota).
        comprobar("entidad descargada => sin confirmar",
                SiegeWaveState.evaluar(true, List.of(SiegeWaveState.EntityState.DESCONOCIDA))
                        == SiegeWaveState.WaveState.SIN_CONFIRMAR);
        comprobar("descargada no paga recompensa",
                !SiegeWaveState.victoriaConRecompensa(SiegeWaveState.WaveState.SIN_CONFIRMAR));
        comprobar("descargada se cierra sin recompensa",
                SiegeWaveState.salvacionSinRecompensa(SiegeWaveState.WaveState.SIN_CONFIRMAR));

        // 6. Mezcla: una baja confirmada y una descargada => sin confirmar (no basta con la baja).
        comprobar("mezcla baja+descargada => sin confirmar",
                SiegeWaveState.evaluar(true, List.of(
                        SiegeWaveState.EntityState.MUERTA_O_AUSENTE_CONFIRMADA,
                        SiegeWaveState.EntityState.DESCONOCIDA)) == SiegeWaveState.WaveState.SIN_CONFIRMAR);

        // 7. Mezcla: una viva y una descargada => activa (lo observable manda).
        comprobar("mezcla viva+descargada => activa",
                SiegeWaveState.evaluar(true, List.of(
                        SiegeWaveState.EntityState.VIVA,
                        SiegeWaveState.EntityState.DESCONOCIDA)) == SiegeWaveState.WaveState.ACTIVA);

        if (fallos > 0) {
            System.out.println("SIEGE_WAVE_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("SIEGE_WAVE_TEST: OK (todas las aserciones verdes)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + nombre);
        } else {
            System.out.println("  ok: " + nombre);
        }
    }
}
