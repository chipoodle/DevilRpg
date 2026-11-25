package com.chipoodle.devilrpg.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * C4 · un puesto, un dueño: la política conserva el UUID menor por POI y marca sobrantes a los demás,
 * con orden estable y sin tocar asignaciones válidas.
 */
public final class VillageJobSiteOwnershipPolicyTest {
    public static void main(String[] args) {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID c = UUID.fromString("00000000-0000-0000-0000-000000000003");

        check("sin registros no hay sobrantes", sobrantes(List.of()).isEmpty());
        check("un solo duenio no produce sobrantes",
                sobrantes(List.of(new VillageJobSiteOwnershipPolicy.Registro(a, 10L))).isEmpty());
        check("POIs distintos no producen sobrantes",
                sobrantes(List.of(
                        new VillageJobSiteOwnershipPolicy.Registro(a, 10L),
                        new VillageJobSiteOwnershipPolicy.Registro(b, 20L))).isEmpty());

        Set<UUID> dup = sobrantes(List.of(
                new VillageJobSiteOwnershipPolicy.Registro(b, 10L),
                new VillageJobSiteOwnershipPolicy.Registro(a, 10L)));
        check("dos en el mismo POI: sobra el UUID mayor", dup.size() == 1 && dup.contains(b));
        check("el UUID menor conserva el puesto", !dup.contains(a));

        Set<UUID> tres = sobrantes(List.of(
                new VillageJobSiteOwnershipPolicy.Registro(c, 7L),
                new VillageJobSiteOwnershipPolicy.Registro(b, 7L),
                new VillageJobSiteOwnershipPolicy.Registro(a, 7L)));
        check("tres en el mismo POI: sobran dos", tres.size() == 2 && tres.contains(b) && tres.contains(c));

        Set<UUID> mixto = sobrantes(List.of(
                new VillageJobSiteOwnershipPolicy.Registro(a, 1L),
                new VillageJobSiteOwnershipPolicy.Registro(b, 1L),
                new VillageJobSiteOwnershipPolicy.Registro(c, 2L),
                new VillageJobSiteOwnershipPolicy.Registro(a, 2L)));
        check("sobrantes solo donde hay colision (b en 1, c en 2)",
                mixto.size() == 2 && mixto.contains(b) && mixto.contains(c));

        Set<UUID> repetidoMismo = sobrantes(List.of(
                new VillageJobSiteOwnershipPolicy.Registro(a, 5L),
                new VillageJobSiteOwnershipPolicy.Registro(a, 5L)));
        check("registro repetido del mismo UUID no lo marca", repetidoMismo.isEmpty());

        check("orden de entrada irrelevante",
                sobrantes(List.of(
                        new VillageJobSiteOwnershipPolicy.Registro(a, 9L),
                        new VillageJobSiteOwnershipPolicy.Registro(b, 9L))).equals(
                        sobrantes(List.of(
                                new VillageJobSiteOwnershipPolicy.Registro(b, 9L),
                                new VillageJobSiteOwnershipPolicy.Registro(a, 9L)))));

        // Idempotencia: tras excluir a los sobrantes, un segundo pase no encuentra nada y conserva al titular.
        java.util.List<VillageJobSiteOwnershipPolicy.Registro> todos = java.util.List.of(
                new VillageJobSiteOwnershipPolicy.Registro(a, 1L),
                new VillageJobSiteOwnershipPolicy.Registro(b, 1L),
                new VillageJobSiteOwnershipPolicy.Registro(c, 1L));
        java.util.Set<UUID> sobrantes1 = sobrantes(todos);
        java.util.List<VillageJobSiteOwnershipPolicy.Registro> despues = new java.util.ArrayList<>();
        for (VillageJobSiteOwnershipPolicy.Registro r : todos) {
            if (!sobrantes1.contains(r.uuid())) {
                despues.add(r);
            }
        }
        check("reconciliacion idempotente", sobrantes(despues).isEmpty());
        check("no destruye la asignacion valida", despues.size() == 1 && despues.get(0).uuid().equals(a));
        // Dos ciclos: los sobrantes se reasignan a POIs libres y el siguiente pase queda limpio.
        java.util.List<VillageJobSiteOwnershipPolicy.Registro> ciclo2 = new java.util.ArrayList<>(despues);
        long libre = 2L;
        for (UUID sobrante : sobrantes1) {
            ciclo2.add(new VillageJobSiteOwnershipPolicy.Registro(sobrante, libre++));
        }
        check("dos ciclos sin duplicados", sobrantes(ciclo2).isEmpty());
        probarReconciliacionDePotenciales();
        probarTicketsPerdidos();
        probarLibroAlDesaparecerTitular();
        probarRestauracionDelLibro();
        probarDuplicadosKeeperNoCargado();
        System.out.println("VILLAGE_JOB_SITE_OWNERSHIP_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    // ------------------------------------------------------------- C4/L3 · restauración del libro

    private static void probarDuplicadosKeeperNoCargado() {
        UUID keeper = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID a = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        UUID c = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

        check("L2 keeper no cargado: limpia a todos los cargados del POI",
                VillageJobSiteOwnershipPolicy.limpiarDuplicados(keeper, false, List.of(a, c))
                        .equals(Set.of(a, c)));
        check("L2 keeper cargado: conserva al titular y limpia a los demas",
                VillageJobSiteOwnershipPolicy.limpiarDuplicados(keeper, true, List.of(a, keeper, c))
                        .equals(Set.of(a, c)));
        check("L2 sin titular en el libro no se adivina",
                VillageJobSiteOwnershipPolicy.limpiarDuplicados(null, false, List.of(a)).isEmpty());
        check("L2 lista vacia no produce limpieza",
                VillageJobSiteOwnershipPolicy.limpiarDuplicados(keeper, false, List.of()).isEmpty());
        check("L2 UUID nulo no rompe la consulta",
                VillageJobSiteOwnershipPolicy.limpiarDuplicados(keeper, false, java.util.Arrays.asList(a, null))
                        .equals(Set.of(a)));
    }

    private static void probarRestauracionDelLibro() {
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID carol = UUID.fromString("00000000-0000-0000-0000-000000000003");
        long poi = 77L;

        var sinDuenios = new VillageJobSiteReconciliationPolicy.Restauracion(
                alice, poi, true, true, true, true, true, true, List.of());
        var plan = VillageJobSiteReconciliationPolicy.planificarRestauracion(sinDuenios);
        check("restauracion: sin duplicados se devuelve el puesto",
                plan.restaurar() && plan.limpiarMemorias().isEmpty());
        check("restauracion: el motivo no habla de limpieza", !plan.motivo().contains("limpia"));

        var conBob = new VillageJobSiteReconciliationPolicy.Restauracion(
                alice, poi, true, true, true, true, true, true, List.of(bob));
        var planBob = VillageJobSiteReconciliationPolicy.planificarRestauracion(conBob);
        check("restauracion: limpia al que no es titular",
                planBob.restaurar() && planBob.limpiarMemorias().equals(List.of(bob)));
        check("restauracion: el motivo nombra la limpieza", planBob.motivo().contains("limpia"));

        var conDuplicados = new VillageJobSiteReconciliationPolicy.Restauracion(
                alice, poi, true, true, true, true, true, true, List.of(carol, bob, bob, alice));
        var planDuplicados = VillageJobSiteReconciliationPolicy.planificarRestauracion(conDuplicados);
        check("restauracion: limpia ajenos sin duplicados y ordenados",
                planDuplicados.limpiarMemorias().equals(List.of(bob, carol)));
        check("restauracion: no limpia al propio titular", !planDuplicados.limpiarMemorias().contains(alice));
        check("restauracion: dos pasadas deciden igual",
                VillageJobSiteReconciliationPolicy.planificarRestauracion(conDuplicados).equals(planDuplicados));

        check("restauracion: no si el titular no esta cargado",
                !restaurar(new VillageJobSiteReconciliationPolicy.Restauracion(
                        alice, poi, false, true, true, true, true, true, List.of())).restaurar());
        check("restauracion: no si el titular ya tiene JOB",
                !restaurar(new VillageJobSiteReconciliationPolicy.Restauracion(
                        alice, poi, true, false, true, true, true, true, List.of())).restaurar());
        check("restauracion: no si el POI no existe",
                !restaurar(new VillageJobSiteReconciliationPolicy.Restauracion(
                        alice, poi, true, true, false, true, true, true, List.of())).restaurar());
        check("restauracion: no si el POI no es del oficio",
                !restaurar(new VillageJobSiteReconciliationPolicy.Restauracion(
                        alice, poi, true, true, true, false, true, true, List.of())).restaurar());
        check("restauracion: no si el POI esta fuera de la aldea",
                !restaurar(new VillageJobSiteReconciliationPolicy.Restauracion(
                        alice, poi, true, true, true, true, false, true, List.of())).restaurar());
        var sinTicket = restaurar(new VillageJobSiteReconciliationPolicy.Restauracion(
                alice, poi, true, true, true, true, true, false, List.of(bob)));
        check("restauracion: no sin ticket cogido y no limpia a nadie",
                !sinTicket.restaurar() && sinTicket.limpiarMemorias().isEmpty());
        check("restauracion: sin titular no restaura",
                !restaurar(new VillageJobSiteReconciliationPolicy.Restauracion(
                        null, poi, true, true, true, true, true, true, List.of(bob))).restaurar());
        check("restauracion: plan nulo no restaura",
                !VillageJobSiteReconciliationPolicy.planificarRestauracion(null).restaurar());
    }

    private static VillageJobSiteReconciliationPolicy.PlanRestauracion restaurar(
            VillageJobSiteReconciliationPolicy.Restauracion restauracion) {
        return VillageJobSiteReconciliationPolicy.planificarRestauracion(restauracion);
    }

    // ------------------------------------------------------------- C4/L1 · libro al desaparecer el titular

    private static void probarLibroAlDesaparecerTitular() {
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID carol = UUID.fromString("00000000-0000-0000-0000-000000000003");

        Map<Long, UUID> libro = new HashMap<>();
        libro.put(2L, alice);
        libro.put(1L, alice);
        libro.put(3L, bob);

        check("libro: el titular ausente devuelve solo sus puestos, ordenados",
                VillageJobSiteOwnershipPolicy.puestosDelTitular(libro, alice).equals(List.of(1L, 2L)));
        check("libro: no se tocan los puestos de otro titular",
                VillageJobSiteOwnershipPolicy.puestosDelTitular(libro, bob).equals(List.of(3L)));
        check("libro: un titular sin puestos devuelve vacio",
                VillageJobSiteOwnershipPolicy.puestosDelTitular(libro, carol).isEmpty());
        check("libro: titular nulo no revienta",
                VillageJobSiteOwnershipPolicy.puestosDelTitular(libro, null).isEmpty());
        check("libro: libro nulo no revienta",
                VillageJobSiteOwnershipPolicy.puestosDelTitular(null, alice).isEmpty());

        for (Long poi : VillageJobSiteOwnershipPolicy.puestosDelTitular(libro, alice)) {
            libro.remove(poi);
        }
        check("libro: tras olvidar al titular la consulta es idempotente",
                VillageJobSiteOwnershipPolicy.puestosDelTitular(libro, alice).isEmpty());
        check("libro: el otro titular conserva su puesto", libro.size() == 1
                && VillageJobSiteOwnershipPolicy.puestosDelTitular(libro, bob).equals(List.of(3L)));
    }

    // ------------------------------------------------------------------ C4 · reconciliación POTENTIAL/tickets

    private static void probarReconciliacionDePotenciales() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID nitwit = UUID.fromString("00000000-0000-0000-0000-000000000003");

        // PROMOCIÓN: POI del oficio, dentro de la aldea, sin JOB_SITE ajeno y con el ticket ya cogido.
        var promovible = new VillageJobSiteReconciliationPolicy.Potencial(a, 100L, true, true, true, false, true);
        check("POTENTIAL correcto se promueve",
                accion(planificar(List.of(promovible)), a) == VillageJobSiteReconciliationPolicy.Accion.PROMOVER);
        check("POTENTIAL correcto sin ticket cogido no se promueve",
                accion(planificar(List.of(
                        new VillageJobSiteReconciliationPolicy.Potencial(a, 100L, true, true, true, false, false))), a)
                        == VillageJobSiteReconciliationPolicy.Accion.LIMPIAR);

        // TIPO INCORRECTO: se libera el ticket (no hay JOB_SITE ajeno) para que lo reclame su titular.
        var tipoIncorrecto = new VillageJobSiteReconciliationPolicy.Potencial(a, 101L, true, false, true, false, true);
        check("POTENTIAL de tipo incorrecto libera el ticket",
                accion(planificar(List.of(tipoIncorrecto)), a)
                        == VillageJobSiteReconciliationPolicy.Accion.LIBERAR_Y_LIMPIAR);

        // NO ROBAR: si otro aldeano lo tiene como JOB_SITE, solo se borra la memoria.
        var deOtro = new VillageJobSiteReconciliationPolicy.Potencial(a, 102L, true, true, true, true, true);
        check("POTENTIAL con JOB_SITE ajeno no se toca (solo LIMPIAR)",
                accion(planificar(List.of(deOtro)), a) == VillageJobSiteReconciliationPolicy.Accion.LIMPIAR);
        check("POTENTIAL de tipo incorrecto con JOB_SITE ajeno tampoco libera",
                accion(planificar(List.of(
                        new VillageJobSiteReconciliationPolicy.Potencial(a, 102L, true, false, true, true, true))), a)
                        == VillageJobSiteReconciliationPolicy.Accion.LIMPIAR);

        // OBSOLETO / FUERA: solo se borra la memoria; nunca se llama a release sobre un POI que no existe.
        check("POTENTIAL de POI inexistente solo LIMPIA",
                accion(planificar(List.of(
                        new VillageJobSiteReconciliationPolicy.Potencial(a, 103L, false, true, true, false, true))), a)
                        == VillageJobSiteReconciliationPolicy.Accion.LIMPIAR);
        check("POTENTIAL fuera de la aldea con ticket cogido LIBERA y limpia",
                accion(planificar(List.of(
                        new VillageJobSiteReconciliationPolicy.Potencial(a, 104L, true, true, false, false, true))), a)
                        == VillageJobSiteReconciliationPolicy.Accion.LIBERAR_Y_LIMPIAR);
        check("POTENTIAL fuera de la aldea con ticket libre solo LIMPIA",
                accion(planificar(List.of(
                        new VillageJobSiteReconciliationPolicy.Potencial(a, 104L, true, true, false, false, false))), a)
                        == VillageJobSiteReconciliationPolicy.Accion.LIMPIAR);

        // NITWIT: no tiene estación. Si tuviera un POTENTIAL, se libera/limpia y NUNCA se promueve.
        var nitwitPot = new VillageJobSiteReconciliationPolicy.Potencial(nitwit, 105L, true, false, true, false, true);
        check("NITWIT no se promueve jamás",
                accion(planificar(List.of(nitwitPot)), nitwit)
                        == VillageJobSiteReconciliationPolicy.Accion.LIBERAR_Y_LIMPIAR);

        // DOS POTENTIALS EN EL MISMO POI: promueve el UUID menor y el otro NO suelta el ticket que necesita el primero.
        var primero = new VillageJobSiteReconciliationPolicy.Potencial(a, 106L, true, true, true, false, true);
        var segundo = new VillageJobSiteReconciliationPolicy.Potencial(b, 106L, true, true, true, false, true);
        var decisiones = planificar(List.of(segundo, primero)); // el orden de entrada no importa
        check("dos POTENTIAL en el mismo POI: promueve el UUID menor",
                accion(decisiones, a) == VillageJobSiteReconciliationPolicy.Accion.PROMOVER);
        check("dos POTENTIAL en el mismo POI: el otro solo LIMPIA (el manager confirma el ticket del ganador)",
                accion(decisiones, b) == VillageJobSiteReconciliationPolicy.Accion.LIMPIAR);

        // IDEMPOTENCIA: tras aplicar las decisiones, un segundo pase no encuentra nada que hacer.
        List<VillageJobSiteReconciliationPolicy.Potencial> estado = new java.util.ArrayList<>(List.of(promovible));
        aplicar(estado, planificar(estado));
        check("reconciliación de POTENTIAL idempotente", planificar(estado).isEmpty());

        // DOS CICLOS: el segundo ciclo promueve otro POTENTIAL distinto y tampoco deja residuo.
        estado.add(new VillageJobSiteReconciliationPolicy.Potencial(b, 200L, true, true, true, false, true));
        var segundoCiclo = planificar(estado);
        check("dos ciclos: el segundo POTENTIAL también se promueve",
                accion(segundoCiclo, b) == VillageJobSiteReconciliationPolicy.Accion.PROMOVER);
        aplicar(estado, segundoCiclo);
        check("dos ciclos sin residuo", planificar(estado).isEmpty());
    }

    private static void probarTicketsPerdidos() {
        check("ticket perdido se suelta",
                VillageJobSiteReconciliationPolicy.debeSoltarTicket(
                        new VillageJobSiteReconciliationPolicy.Estacion(1L, true, true, false, false)));
        check("no se le roba el ticket a un JOB_SITE real",
                !VillageJobSiteReconciliationPolicy.debeSoltarTicket(
                        new VillageJobSiteReconciliationPolicy.Estacion(1L, true, true, true, false)));
        check("no se le roba el ticket a un POTENTIAL legítimo",
                !VillageJobSiteReconciliationPolicy.debeSoltarTicket(
                        new VillageJobSiteReconciliationPolicy.Estacion(1L, true, true, false, true)));
        check("un ticket libre no se suelta",
                !VillageJobSiteReconciliationPolicy.debeSoltarTicket(
                        new VillageJobSiteReconciliationPolicy.Estacion(1L, true, false, false, false)));
        check("un POI inexistente no se suelta",
                !VillageJobSiteReconciliationPolicy.debeSoltarTicket(
                        new VillageJobSiteReconciliationPolicy.Estacion(1L, false, true, false, false)));
        check("ticketsPerdidos filtra y conserva el orden",
                VillageJobSiteReconciliationPolicy.ticketsPerdidos(List.of(
                        new VillageJobSiteReconciliationPolicy.Estacion(1L, true, true, false, false),
                        new VillageJobSiteReconciliationPolicy.Estacion(2L, true, true, true, false),
                        new VillageJobSiteReconciliationPolicy.Estacion(3L, true, true, false, false),
                        new VillageJobSiteReconciliationPolicy.Estacion(4L, true, true, false, true)))
                        .equals(List.of(1L, 3L)));
    }

    private static List<VillageJobSiteReconciliationPolicy.Decision> planificar(
            List<VillageJobSiteReconciliationPolicy.Potencial> potenciales) {
        return VillageJobSiteReconciliationPolicy.planificarPotenciales(potenciales);
    }

    private static VillageJobSiteReconciliationPolicy.Accion accion(
            List<VillageJobSiteReconciliationPolicy.Decision> decisiones, UUID uuid) {
        for (var d : decisiones) {
            if (d.uuid().equals(uuid)) {
                return d.accion();
            }
        }
        throw new AssertionError("sin decision para " + uuid);
    }

    /** Aplica las decisiones al estado: promover/limpiar quitan el POTENTIAL (el que se promueve pasa a JOB_SITE). */
    private static void aplicar(List<VillageJobSiteReconciliationPolicy.Potencial> estado,
                                List<VillageJobSiteReconciliationPolicy.Decision> decisiones) {
        java.util.Set<UUID> quitados = new java.util.HashSet<>();
        for (var d : decisiones) {
            quitados.add(d.uuid());
        }
        estado.removeIf(p -> quitados.contains(p.uuid()));
    }

    private static Set<UUID> sobrantes(List<VillageJobSiteOwnershipPolicy.Registro> registros) {
        return VillageJobSiteOwnershipPolicy.sobrantes(registros);
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) {
            throw new AssertionError(name);
        }
    }
}
