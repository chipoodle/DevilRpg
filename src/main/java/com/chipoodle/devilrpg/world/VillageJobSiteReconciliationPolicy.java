package com.chipoodle.devilrpg.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * C4 · <b>RECONCILIACIÓN DE PUESTOS: POTENTIAL_JOB_SITE Y TICKETS PERDIDOS</b>.
 * <p>
 * En vanilla {@code AcquirePoi} <b>no</b> pone {@code JOB_SITE}: coge el ticket y deja el POI en
 * {@code POTENTIAL_JOB_SITE}. El cerebro solo lo convierte en {@code JOB_SITE} con
 * {@code AssignProfessionFromJobSite}, y ese comportamiento exige que el aldeano esté a <b>menos de 2 bloques</b>
 * del POI (o que se le asigne al nacer). Mientras tanto el ticket está cogido por un POTENTIAL que puede no
 * convertirse nunca: el puesto queda <b>muerto</b> para los demás y su titular no tiene {@code JOB_SITE}.
 * <p>
 * Esta política decide, de forma <b>pura y determinista</b>, qué hacer con cada POTENTIAL:
 * <ul>
 *   <li><b>PROMOVER</b>: el POI existe, es del oficio del titular, está dentro de la aldea, el ticket ya está
 *       cogido y ningún <b>otro</b> aldeano lo tiene como {@code JOB_SITE}. Se copia a {@code JOB_SITE} sin volver
 *       a {@code take} (el ticket ya es suyo).</li>
 *   <li><b>LIBERAR_Y_LIMPIAR</b>: el POI existe, no lo tiene nadie como {@code JOB_SITE}, pero <b>no es del
 *       oficio</b> (el caso medido: un granjero con el soporte de pociones del clérigo). Se suelta el ticket
 *       <b>solo</b> en este caso, para que su titular legítimo pueda reclamarlo.</li>
 *   <li><b>LIMPIAR</b>: solo se borra la memoria, sin tocar el ticket. Es lo correcto cuando el POI ya no existe,
 *       está fuera de la aldea, lo tiene otro como {@code JOB_SITE} (no se le roba), el ticket no está cogido, o
 *       otro POTENTIAL del mismo POI es el que se promueve (no se suelta dos veces el mismo ticket).</li>
 * </ul>
 * El orden es estable por UUID, así que dos ciclos seguidos producen las mismas decisiones y, tras aplicarlas, la
 * segunda pasada no encuentra nada que hacer (<b>idempotencia</b>).
 */
public final class VillageJobSiteReconciliationPolicy {

    private VillageJobSiteReconciliationPolicy() {
    }

    /** Qué hacer con una memoria {@code POTENTIAL_JOB_SITE}. */
    public enum Accion {
        /** Copiar a {@code JOB_SITE} (sin volver a tomar el ticket). */
        PROMOVER,
        /** Soltar el ticket y borrar la memoria (POI de otro oficio y sin dueño {@code JOB_SITE}). */
        LIBERAR_Y_LIMPIAR,
        /** Borrar la memoria sin tocar el ticket. */
        LIMPIAR
    }

    /**
     * Foto mínima de un {@code POTENTIAL_JOB_SITE} para decidir. No contiene tipos de Minecraft: la traduce el
     * gestor y así la política se puede probar sin servidor.
     *
     * @param uuid                dueño de la memoria
     * @param poi                 posición empaquetada ({@code BlockPos.asLong})
     * @param poiExiste           el POI sigue registrado en el mundo
     * @param tipoDelOficio       el tipo del POI satisface {@code profesion.heldJobSite()}
     * @param dentroDeLaAldea     el POI está dentro del recinto del pueblo
     * @param duenioJobSiteAjeno  <b>otro</b> aldeano tiene ese POI como {@code JOB_SITE}
     * @param ticketCogido        el POI no tiene tickets libres ({@code free_tickets == 0}); si es {@code false} no
     *                            hay nada que promover ni que soltar
     */
    public record Potencial(UUID uuid, long poi, boolean poiExiste, boolean tipoDelOficio,
                            boolean dentroDeLaAldea, boolean duenioJobSiteAjeno, boolean ticketCogido) {
    }

    /** Decisión para un {@link Potencial}. */
    public record Decision(UUID uuid, long poi, Accion accion, String motivo) {
    }

    /**
     * Decide qué hacer con cada POTENTIAL. Orden estable por UUID y, dentro del mismo UUID, por POI.
     * <p>
     * Si dos POTENTIAL apuntan al mismo POI, solo se promueve uno (el UUID menor que sea elegible); al resto se les
     * borra la memoria <b>sin soltar el ticket</b>, porque el ticket que suelta uno es el que necesita el que se
     * promueve.
     */
    public static List<Decision> planificarPotenciales(final List<Potencial> potenciales) {
        List<Decision> out = new ArrayList<>();
        if (potenciales == null || potenciales.isEmpty()) {
            return out;
        }
        List<Potencial> ordenados = new ArrayList<>(potenciales);
        ordenados.sort(Comparator.comparing((Potencial p) -> p.uuid().toString())
                .thenComparingLong(Potencial::poi));
        Map<Long, Potencial> ganador = new HashMap<>();
        for (Potencial p : ordenados) {
            if (elegibleParaPromover(p)) {
                ganador.putIfAbsent(p.poi(), p); // el primero en orden de UUID se queda el POI
            }
        }
        for (Potencial p : ordenados) {
            Potencial seQueda = ganador.get(p.poi());
            Accion accion;
            String motivo;
            if (p.duenioJobSiteAjeno()) {
                accion = Accion.LIMPIAR;
                motivo = "el POI ya tiene JOB_SITE de otro aldeano";
            } else if (!p.poiExiste()) {
                accion = Accion.LIMPIAR;
                motivo = "el POI ya no existe: solo se borra la memoria";
            } else if (!p.dentroDeLaAldea()) {
                // Fuera de la aldea no se promueve; si el ticket esta cogido y no hay JOB_SITE ajeno, liberarlo
                // evita el ticket colgado de un POTENTIAL que nunca se va a convertir (y no se toca el de un duenio
                // real).
                accion = p.ticketCogido() ? Accion.LIBERAR_Y_LIMPIAR : Accion.LIMPIAR;
                motivo = p.ticketCogido()
                        ? "el POI esta fuera de la aldea: se libera su ticket y se borra el POTENTIAL"
                        : "el POI esta fuera de la aldea y su ticket esta libre: solo se borra la memoria";
            } else if (seQueda != null && !seQueda.uuid().equals(p.uuid())) {
                accion = Accion.LIMPIAR;
                motivo = "otro POTENTIAL del mismo POI es el que se promueve";
            } else if (!p.ticketCogido()) {
                accion = Accion.LIMPIAR;
                motivo = "el ticket no esta cogido: no hay nada que promover";
            } else if (!p.tipoDelOficio()) {
                accion = Accion.LIBERAR_Y_LIMPIAR;
                motivo = "el POI no es del oficio y no lo tiene ningun JOB_SITE: se libera el ticket";
            } else {
                accion = Accion.PROMOVER;
                motivo = "POI correcto, dentro de la aldea y sin JOB_SITE ajeno";
            }
            out.add(new Decision(p.uuid(), p.poi(), accion, motivo));
        }
        return out;
    }

    private static boolean elegibleParaPromover(final Potencial p) {
        return p.poiExiste() && p.tipoDelOficio() && p.dentroDeLaAldea()
                && !p.duenioJobSiteAjeno() && p.ticketCogido();
    }

    /**
     * Foto mínima de una estación ocupada para decidir si su ticket está <b>perdido</b>.
     *
     * @param poi                  posición empaquetada
     * @param existe               el POI sigue registrado
     * @param ticketCogido         {@code free_tickets == 0}
     * @param conDuenioJobSite     algún aldeano lo tiene como {@code JOB_SITE} real
     * @param conPotencialLegitimo algún aldeano del oficio lo tiene como POTENTIAL legítimo (se promoverá)
     */
    public record Estacion(long poi, boolean existe, boolean ticketCogido, boolean conDuenioJobSite,
                           boolean conPotencialLegitimo) {
    }

    /**
     * Un ticket solo se suelta si el POI existe, está cogido (sin tickets libres) y <b>no</b> hay ni un
     * {@code JOB_SITE} real ni un POTENTIAL legítimo que lo respalde. Así no se le roba el puesto a nadie: ni a un
     * dueño {@code JOB_SITE} (aunque esté en un chunk descargado  en cuanto se le vea) ni al titular que todavía
     * tiene el POI como POTENTIAL y está a punto de promoverse.
     */
    public static boolean debeSoltarTicket(final Estacion estacion) {
        return estacion != null
                && estacion.existe()
                && estacion.ticketCogido()
                && !estacion.conDuenioJobSite()
                && !estacion.conPotencialLegitimo();
    }

    /** Posiciones de las estaciones cuyo ticket se puede soltar, en el orden de entrada. */
    public static List<Long> ticketsPerdidos(final List<Estacion> estaciones) {
        List<Long> out = new ArrayList<>();
        if (estaciones == null) {
            return out;
        }
        for (Estacion e : estaciones) {
            if (debeSoltarTicket(e)) {
                out.add(e.poi());
            }
        }
        return out;
    }
}
