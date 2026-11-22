package com.chipoodle.devilrpg.entity.nav;

/** Bounded acceptance rule for a local standable objective when the mission requires descent. */
public final class NavLocalObjectiveProgressPolicy {
    private static final double MIN_HORIZONTAL_GAIN_SQR = 0.25D;

    private NavLocalObjectiveProgressPolicy() {
    }

    public static boolean aceptar(final int mobY, final int missionY, final int candidateY,
                                  final boolean rutaContinua, final boolean escalonPendiente) {
        if (escalonPendiente || rutaContinua) {
            return true;
        }
        // DESCENDER es progreso por si mismo. Ascender o mantenerse NO lo es: exige demostrar avance
        // horizontal (version de 8 argumentos). Antes esta linea era `missionY >= mobY || candidateY < mobY`
        // y, con la mision por ENCIMA, aceptaba CUALQUIER candidato: medido en `a2n-20261121-212053`,
        // IDX7/8 aceptaron un waypoint a 44 bloques que los alejaba (86 -> 108 de distancia al ancla).
        return candidateY < mobY;
    }

    /**
     * Same rule plus a horizontal-progress escape: a same-level candidate is valid when it
     * measurably reduces horizontal distance to the mission. A candidate that moves away is
     * still rejected.
     */
    public static boolean aceptar(final int mobY, final int missionY, final int candidateY,
                                  final double mobX, final double mobZ,
                                  final double missionX, final double missionZ,
                                  final double candidateX, final double candidateZ,
                                  final boolean rutaContinua, final boolean escalonPendiente) {
        if (aceptar(mobY, missionY, candidateY, rutaContinua, escalonPendiente)) {
            return true;
        }
        double mobDx = mobX - missionX;
        double mobDz = mobZ - missionZ;
        double candidateDx = candidateX - missionX;
        double candidateDz = candidateZ - missionZ;
        double mobD2 = mobDx * mobDx + mobDz * mobDz;
        double candidateD2 = candidateDx * candidateDx + candidateDz * candidateDz;
        return candidateD2 < mobD2 - MIN_HORIZONTAL_GAIN_SQR;
    }
}
