package com.chipoodle.devilrpg.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * C4 · <b>UN PUESTO, UN DUEÑO (memoria)</b>.
 * <p>
 * En un mundo reabierto, dos aldeanos pueden conservar la <b>misma</b> memoria {@code JOB_SITE} si el guardado
 * capturó una reasignación transitoria del reparto. Esta política decide, de forma <b>pura y determinista</b>,
 * qué aldeanos <b>sobran</b> de cada puesto: se queda el primero en orden estable por UUID y los demás pierden
 * su memoria para que el reparto les dé otra estación (o ninguna este latido).
 * <p>
 * No suelta el ticket del POI: la estación sigue perteneciendo a su titular real y la lógica existente
 * («ocupada sin dueño» → se suelta y se vuelve a coger) la reconcilia. No borra asignaciones válidas: solo
 * actúa cuando <b>dos o más</b> aldeanos comparten exactamente la misma celda.
 */
public final class VillageJobSiteOwnershipPolicy {

    /** Un aldeano y el POI que tiene como {@code JOB_SITE}. */
    public record Registro(UUID uuid, long poi) {
    }

    private VillageJobSiteOwnershipPolicy() {
    }

    /**
     * Devuelve los aldeanos que deben perder la memoria {@code JOB_SITE} por compartir POI con otro.
     * <p>
     * Reglas: por cada POI se conserva el aldeano con el UUID menor en orden lexicográfico (estable entre
     * latidos y entre cargas); todos los demás son sobrantes. Entradas con POI repetido del <b>mismo</b> UUID
     * (no debería ocurrir) no producen sobrantes duplicados.
     */
    public static Set<UUID> sobrantes(final List<Registro> registros) {
        Set<UUID> out = new HashSet<>();
        if (registros == null || registros.isEmpty()) {
            return out;
        }
        List<Registro> ordenados = new ArrayList<>(registros);
        ordenados.sort(Comparator.comparing((Registro r) -> r.uuid().toString()));
        java.util.Map<Long, UUID> duenio = new java.util.HashMap<>();
        for (Registro r : ordenados) {
            UUID previo = duenio.putIfAbsent(r.poi(), r.uuid());
            if (previo != null && !previo.equals(r.uuid())) {
                out.add(r.uuid());
            }
        }
        return out;
    }
}
