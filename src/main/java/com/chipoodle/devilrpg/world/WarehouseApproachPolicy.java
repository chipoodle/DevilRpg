package com.chipoodle.devilrpg.world;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Política pura de elección de la casilla de descarga del almacén. No consulta el mundo ni la navegación:
 * el adaptador de Minecraft calcula los hechos y esta clase solo filtra y ordena de forma determinista.
 */
public final class WarehouseApproachPolicy {

    private WarehouseApproachPolicy() {
    }

    /** Hechos ya comprobados por el adaptador. {@code distanceSqr} es la distancia al aldeano. */
    public record Candidate(int x, int y, int z, boolean standable, boolean inRange, boolean reachable,
                            double distanceSqr) {

        public boolean eligible() {
            return standable && inRange && reachable;
        }
    }

    private static final Comparator<Candidate> RANKING = Comparator
            .comparingDouble(Candidate::distanceSqr)
            .thenComparingInt(Candidate::x)
            .thenComparingInt(Candidate::y)
            .thenComparingInt(Candidate::z);

    /** La casilla elegible más cercana; empate resuelto por coordenadas. Vacío si no hay ninguna. */
    public static Optional<Candidate> select(List<Candidate> candidates) {
        return candidates.stream().filter(Candidate::eligible).min(RANKING);
    }
}
