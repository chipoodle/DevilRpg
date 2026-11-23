package com.chipoodle.devilrpg.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * C4 · <b>INVARIANTES DEL CENSO DE LA ALDEA</b> (puro, determinista).
 * <p>
 * Comprueba un censo de aldeanos contra el contrato del pueblo: <b>12 puestos</b> de profesión, <b>4 guardias de
 * fundación</b> separados, profesiones dentro de su cupo, identidades únicas y <b>un solo dueño por puesto</b>.
 * <p>
 * No crea ni modifica entidades: es la red de seguridad que usan el arnés de aceptación y las pruebas para
 * detectar duplicados, pérdidas o cruces de responsabilidad sin depender de contar entidades a ojo.
 */
public final class VillageRosterInvariants {

    /** Miembro del censo: identidad, profesión (texto del enum), puesto (0 = sin puesto) y si es fundador. */
    public record Miembro(UUID uuid, String profesion, long puesto, boolean fundador) {
    }

    /** Resultado: lista de violaciones legibles; vacía = censo coherente. */
    public record Resultado(List<String> violaciones) {
        public boolean ok() {
            return violaciones.isEmpty();
        }
    }

    private VillageRosterInvariants() {
    }

    /**
     * @param roster            censo completo (profesionales y fundadores)
     * @param puestosEsperados  número de puestos de profesión del pueblo (12)
     * @param cupoPorOficio     cupo por profesión ({@code VillageGenerator.puestosPorOficio()})
     * @param fundadoresEsperados número de guardias de fundación (4)
     */
    public static Resultado comprobar(final List<Miembro> roster, final int puestosEsperados,
                                      final Map<String, Integer> cupoPorOficio, final int fundadoresEsperados) {
        List<String> violaciones = new ArrayList<>();
        if (roster == null) {
            violaciones.add("censo_nulo");
            return new Resultado(violaciones);
        }
        Set<UUID> uuids = new HashSet<>();
        Set<Long> puestos = new HashSet<>();
        Map<String, Integer> porOficio = new HashMap<>();
        int profesionales = 0;
        int fundadores = 0;
        for (Miembro m : roster) {
            if (m == null || m.uuid() == null) {
                violaciones.add("miembro_nulo");
                continue;
            }
            if (!uuids.add(m.uuid())) {
                violaciones.add("uuid_duplicado:" + m.uuid());
            }
            if (m.fundador()) {
                fundadores++;
            } else {
                profesionales++;
                String prof = m.profesion() == null ? "" : m.profesion();
                if (prof.isEmpty() || "none".equals(prof)) {
                    violaciones.add("profesion_vacia:" + m.uuid());
                } else {
                    if (cupoPorOficio == null || !cupoPorOficio.containsKey(prof)) {
                        violaciones.add("profesion_fuera_del_cupo:" + prof + ":" + m.uuid());
                    } else {
                        porOficio.merge(prof, 1, Integer::sum);
                    }
                }
            }
            if (m.puesto() != 0L && !puestos.add(m.puesto())) {
                violaciones.add("puesto_duplicado:" + m.puesto());
            }
        }
        if (profesionales != puestosEsperados) {
            violaciones.add("profesionales:" + profesionales + "/" + puestosEsperados);
        }
        if (fundadores != fundadoresEsperados) {
            violaciones.add("fundadores:" + fundadores + "/" + fundadoresEsperados);
        }
        if (cupoPorOficio != null) {
            for (var e : porOficio.entrySet()) {
                int cupo = cupoPorOficio.getOrDefault(e.getKey(), 0);
                if (e.getValue() > cupo) {
                    violaciones.add("oficio_excedido:" + e.getKey() + ":" + e.getValue() + "/" + cupo);
                }
            }
        }
        return new Resultado(violaciones);
    }
}
