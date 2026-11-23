package com.chipoodle.devilrpg.entity.nav;

import java.util.List;
import java.util.UUID;

/**
 * A3 · Prueba de la API de instrumentación {@link NavPlannerMetrics}.
 *
 * <p>Programa {@code main} sin JUnit, estilo del repo: cuenta aserciones y sale con código 1 si alguna falla.
 * Comprueba que el snapshot coincide EXACTAMENTE con lo inyectado por begin/advance/end/cancel, el no-op con el
 * flag apagado, la semántica de reanudada/replanificación/fallo/presupuesto, los agregados por entidad, el reset,
 * los topes de memoria y que los datos nulos o absurdos no lanzan excepciones.
 */
public final class NavPlannerMetricsTest {
    private static int comprobaciones;
    private static int fallos;

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    public static void main(String[] args) {
        // 1 · Estado inicial: el flag refleja la propiedad del sistema (por defecto false) y, apagado, es no-op.
        boolean propiedad = Boolean.getBoolean("devilrpg.nav.metrics");
        comprobar("el flag inicial coincide con -Ddevilrpg.nav.metrics (por defecto false)",
                NavPlannerMetrics.enabled() == propiedad);
        NavPlannerMetrics.setEnabled(false);
        NavPlannerMetrics.Snapshot antes = NavPlannerMetrics.snapshot();
        long idApagado = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.GLOBAL, A, 10L, 100);
        NavPlannerMetrics.advance(idApagado, 5, 500L);
        NavPlannerMetrics.end(idApagado, true, false, 3, "no deberia registrarse");
        NavPlannerMetrics.cancel(idApagado, "no deberia registrarse");
        NavPlannerMetrics.Snapshot despues = NavPlannerMetrics.snapshot();
        comprobar("begin devuelve SIN_BUSQUEDA con flag apagado", idApagado == NavPlannerMetrics.SIN_BUSQUEDA);
        comprobar("snapshot sigue vacio con flag apagado", totalesVacios(despues.totales())
                && despues.busquedas().isEmpty() && despues.abiertas().isEmpty()
                && despues.entidades().isEmpty());
        comprobar("snapshot no cambia por llamadas con flag apagado", despues.equals(antes));

        // 2 · Activación y reset.
        NavPlannerMetrics.setEnabled(true);
        NavPlannerMetrics.reset();
        comprobar("setEnabled(true) activa el flag", NavPlannerMetrics.enabled());
        comprobar("reset deja los totales a cero", totalesVacios(NavPlannerMetrics.snapshot().totales()));

        // 3 · begin: id, tipo, entidad, tick y presupuesto exactos.
        long idA = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.LOCAL, A, 100L, 50);
        NavPlannerMetrics.Snapshot s = NavPlannerMetrics.snapshot();
        NavPlannerMetrics.BusquedaSnapshot abiertaA = buscar(s.abiertas(), idA);
        comprobar("begin devuelve un id positivo", idA > NavPlannerMetrics.SIN_BUSQUEDA);
        comprobar("begin cuenta una busqueda en totales", s.totales().busquedas() == 1L);
        comprobar("begin registra una entidad", s.totales().entidades() == 1 && s.entidades().size() == 1);
        comprobar("begin no cierra la busqueda", s.busquedas().isEmpty() && abiertaA != null);
        comprobar("begin guarda tipo LOCAL", abiertaA != null && abiertaA.tipo() == NavPlannerMetrics.Tipo.LOCAL);
        comprobar("begin guarda la entidad", abiertaA != null && A.equals(abiertaA.entidad()));
        comprobar("begin guarda el tick de inicio", abiertaA != null && abiertaA.tickInicio() == 100L);
        comprobar("begin guarda el presupuesto maximo", abiertaA != null && abiertaA.presupuesto() == 50);
        comprobar("begin deja la busqueda ABIERTA sin avances",
                abiertaA != null && abiertaA.estado() == NavPlannerMetrics.Estado.ABIERTA
                        && abiertaA.avances() == 0 && abiertaA.expansiones() == 0 && abiertaA.nanos() == 0L);
        comprobar("begin deja motivo '-' y sin flags", abiertaA != null && "-".equals(abiertaA.motivo())
                && !abiertaA.reanudada() && !abiertaA.replanificacion() && !abiertaA.fallida()
                && !abiertaA.presupuestoAgotado() && abiertaA.longitudRuta() == 0);

        // 4 · advance: deltas, nanos, slices y reanudada.
        NavPlannerMetrics.advance(idA, 10, 1000L);
        NavPlannerMetrics.advance(idA, 15, 2000L);
        s = NavPlannerMetrics.snapshot();
        NavPlannerMetrics.BusquedaSnapshot enCursoA = buscar(s.abiertas(), idA);
        comprobar("advance acumula expansiones", enCursoA != null && enCursoA.expansiones() == 25);
        comprobar("advance acumula nanos", enCursoA != null && enCursoA.nanos() == 3000L);
        comprobar("advance cuenta avances", enCursoA != null && enCursoA.avances() == 2);
        comprobar("advance guarda el ultimo slice", enCursoA != null && enCursoA.ultimoSliceExpansiones() == 15);
        comprobar("mas de un avance marca reanudada", enCursoA != null && enCursoA.reanudada());
        comprobar("nodos por avance = expansiones/avances", enCursoA != null && enCursoA.nodosPorAvance() == 12);
        comprobar("totales acumulan expansiones y nanos",
                s.totales().expansiones() == 25L && s.totales().nanos() == 3000L);
        comprobar("totales cuentan una busqueda reanudada", s.totales().reanudadas() == 1L);
        NavPlannerMetrics.EntidadSnapshot agregadoA = buscarEntidad(s.entidades(), A);
        comprobar("agregado por entidad acumula expansiones y nanos",
                agregadoA != null && agregadoA.expansiones() == 25L && agregadoA.nanos() == 3000L);
        comprobar("agregado por entidad cuenta la busqueda",
                agregadoA != null && agregadoA.busquedas() == 1 && agregadoA.ultimaBusqueda() == idA);

        // 5 · end: cierre, estado, motivo, ruta y agregados.
        NavPlannerMetrics.end(idA, true, false, 7, "meta alcanzada");
        s = NavPlannerMetrics.snapshot();
        NavPlannerMetrics.BusquedaSnapshot cerradaA = buscar(s.busquedas(), idA);
        agregadoA = buscarEntidad(s.entidades(), A);
        comprobar("end mueve la busqueda a cerradas", s.abiertas().isEmpty() && cerradaA != null);
        comprobar("end guarda exito como COMPLETADA",
                cerradaA != null && cerradaA.estado() == NavPlannerMetrics.Estado.COMPLETADA
                        && !cerradaA.fallida());
        comprobar("end conserva expansiones y nanos", cerradaA != null
                && cerradaA.expansiones() == 25 && cerradaA.nanos() == 3000L && cerradaA.avances() == 2);
        comprobar("end guarda longitud de ruta y motivo", cerradaA != null
                && cerradaA.longitudRuta() == 7 && "meta alcanzada".equals(cerradaA.motivo()));
        comprobar("end cuenta completada y no fallo",
                s.totales().busquedasCompletadas() == 1L && s.totales().busquedasFallidas() == 0L);
        comprobar("end actualiza ultima busqueda de la entidad", agregadoA != null
                && agregadoA.ultimaBusqueda() == idA && agregadoA.ultimoTipo() == NavPlannerMetrics.Tipo.LOCAL
                && agregadoA.ultimoTick() == 100L && "meta alcanzada".equals(agregadoA.ultimoMotivo())
                && agregadoA.ultimaLongitudRuta() == 7);
        comprobar("end no deja busqueda abierta en la entidad", agregadoA != null
                && agregadoA.fallos() == 0 && agregadoA.canceladas() == 0);

        // 6 · Cierres duplicados y avances tardios son no-op.
        NavPlannerMetrics.Snapshot trasCierre = NavPlannerMetrics.snapshot();
        NavPlannerMetrics.end(idA, false, true, 99, "duplicado");
        NavPlannerMetrics.advance(idA, 500, 500L);
        NavPlannerMetrics.cancel(idA, "duplicado");
        comprobar("end/advance/cancel sobre una busqueda cerrada no cambian el snapshot",
                NavPlannerMetrics.snapshot().equals(trasCierre));

        // 7 · Replanificacion con busqueda abierta.
        long idB = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.GLOBAL, A, 101L, 30);
        NavPlannerMetrics.BusquedaSnapshot abiertaB = buscar(NavPlannerMetrics.snapshot().abiertas(), idB);
        comprobar("nueva busqueda tras cierre normal NO es replanificacion",
                abiertaB != null && !abiertaB.replanificacion());
        long idC = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.GLOBAL_EXACT, A, 102L, 30);
        NavPlannerMetrics.BusquedaSnapshot abiertaC = buscar(NavPlannerMetrics.snapshot().abiertas(), idC);
        comprobar("nueva busqueda con otra abierta ES replanificacion",
                abiertaC != null && abiertaC.replanificacion());
        comprobar("replanificacion se cuenta en totales",
                NavPlannerMetrics.snapshot().totales().replanificaciones() == 1L);
        NavPlannerMetrics.cancel(idB, "owner_cancelled");
        s = NavPlannerMetrics.snapshot();
        NavPlannerMetrics.BusquedaSnapshot canceladaB = buscar(s.busquedas(), idB);
        comprobar("cancel cierra como CANCELADA con motivo",
                canceladaB != null && canceladaB.estado() == NavPlannerMetrics.Estado.CANCELADA
                        && "owner_cancelled".equals(canceladaB.motivo()) && !canceladaB.fallida());
        comprobar("cancel se cuenta aparte de fallos",
                s.totales().canceladas() == 1L && s.totales().busquedasFallidas() == 0L);
        NavPlannerMetrics.end(idC, false, true, 0, "presupuesto global agotado");
        s = NavPlannerMetrics.snapshot();
        NavPlannerMetrics.BusquedaSnapshot fallidaC = buscar(s.busquedas(), idC);
        comprobar("end sin exito cierra como FALLIDA",
                fallidaC != null && fallidaC.estado() == NavPlannerMetrics.Estado.FALLIDA && fallidaC.fallida());
        comprobar("presupuesto agotado se guarda y se cuenta",
                fallidaC != null && fallidaC.presupuestoAgotado()
                        && s.totales().presupuestoAgotado() == 1L && s.totales().busquedasFallidas() == 1L);
        agregadoA = buscarEntidad(s.entidades(), A);
        comprobar("agregado por entidad cuenta fallos y presupuesto",
                agregadoA != null && agregadoA.fallos() == 1 && agregadoA.presupuestoAgotado() == 1);

        // 8 · Replanificacion tras cancelacion (misma entidad).
        long idB1 = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.LOCAL, B, 200L, 10);
        NavPlannerMetrics.cancel(idB1, "owner_cancelled");
        long idB2 = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.LOCAL, B, 201L, 10);
        NavPlannerMetrics.BusquedaSnapshot abiertaB2 = buscar(NavPlannerMetrics.snapshot().abiertas(), idB2);
        comprobar("nueva busqueda tras cancelacion ES replanificacion",
                abiertaB2 != null && abiertaB2.replanificacion());
        NavPlannerMetrics.end(idB2, true, false, 2, "meta alcanzada");
        NavPlannerMetrics.EntidadSnapshot agregadoB = buscarEntidad(NavPlannerMetrics.snapshot().entidades(), B);
        comprobar("agregado de B cuenta 2 busquedas, 1 cancelada y 1 replanificacion",
                agregadoB != null && agregadoB.busquedas() == 2 && agregadoB.canceladas() == 1
                        && agregadoB.replanificaciones() == 1 && agregadoB.fallos() == 0);
        comprobar("segunda entidad aparece en el agregado", NavPlannerMetrics.snapshot().entidades().size() == 2);

        // 9 · Entidad null: sin NPE, sin perder los totales globales.
        NavPlannerMetrics.Snapshot antesNull = NavPlannerMetrics.snapshot();
        long idNulo = NavPlannerMetrics.begin(null, null, -1L, -5);
        NavPlannerMetrics.Snapshot conNulo = NavPlannerMetrics.snapshot();
        NavPlannerMetrics.BusquedaSnapshot abiertaNula = buscar(conNulo.abiertas(), idNulo);
        comprobar("begin con tipo/entidad null no lanza y normaliza tipo",
                idNulo > NavPlannerMetrics.SIN_BUSQUEDA && abiertaNula != null
                        && abiertaNula.tipo() == NavPlannerMetrics.Tipo.SINTETICA
                        && abiertaNula.entidad() == null);
        comprobar("presupuesto negativo se acota a cero", abiertaNula != null && abiertaNula.presupuesto() == 0);
        NavPlannerMetrics.advance(idNulo, 5, 50L);
        NavPlannerMetrics.end(idNulo, true, false, 1, null);
        NavPlannerMetrics.Snapshot trasNulo = NavPlannerMetrics.snapshot();
        NavPlannerMetrics.BusquedaSnapshot cerradaNula = buscar(trasNulo.busquedas(), idNulo);
        comprobar("busqueda sin entidad conserva sus totales globales",
                trasNulo.totales().busquedas() == antesNull.totales().busquedas() + 1
                        && trasNulo.totales().expansiones() == antesNull.totales().expansiones() + 5
                        && trasNulo.totales().nanos() == antesNull.totales().nanos() + 50L);
        comprobar("motivo null se normaliza a '-'", cerradaNula != null && "-".equals(cerradaNula.motivo()));
        comprobar("entidad null no crea agregado por entidad",
                trasNulo.entidades().size() == antesNull.entidades().size());
        comprobar("dumpCsv tolera entidad null sin escribir 'null'",
                !NavPlannerMetrics.dumpCsv().contains("null"));

        // 10 · Datos raros: ids desconocidos, deltas/naños negativos y saturacion.
        NavPlannerMetrics.advance(NavPlannerMetrics.SIN_BUSQUEDA, 5, 5L);
        NavPlannerMetrics.advance(-7L, 5, 5L);
        NavPlannerMetrics.advance(9_999_999L, 5, 5L);
        NavPlannerMetrics.end(9_999_999L, true, false, 1, "desconocido");
        NavPlannerMetrics.cancel(9_999_999L, "desconocido");
        comprobar("ids desconocidos son no-op", buscar(NavPlannerMetrics.snapshot().busquedas(), 9_999_999L) == null);
        long idRaro = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.SINTETICA, null, Long.MIN_VALUE,
                Integer.MIN_VALUE);
        NavPlannerMetrics.advance(idRaro, -100, -100L);
        NavPlannerMetrics.BusquedaSnapshot raro = buscar(NavPlannerMetrics.snapshot().abiertas(), idRaro);
        comprobar("delta negativo no resta y cuenta un avance",
                raro != null && raro.expansiones() == 0 && raro.nanos() == 0L && raro.avances() == 1
                        && raro.ultimoSliceExpansiones() == 0);
        NavPlannerMetrics.advance(idRaro, Integer.MAX_VALUE, Long.MAX_VALUE);
        NavPlannerMetrics.advance(idRaro, Integer.MAX_VALUE, Long.MAX_VALUE);
        raro = buscar(NavPlannerMetrics.snapshot().abiertas(), idRaro);
        comprobar("expansiones y nanos se saturan sin desbordar",
                raro != null && raro.expansiones() == Integer.MAX_VALUE && raro.nanos() == Long.MAX_VALUE);
        NavPlannerMetrics.end(idRaro, false, false, -9, "");
        NavPlannerMetrics.BusquedaSnapshot raroCerrado = buscar(NavPlannerMetrics.snapshot().busquedas(), idRaro);
        comprobar("longitud negativa se acota y motivo vacio se normaliza",
                raroCerrado != null && raroCerrado.longitudRuta() == 0 && "-".equals(raroCerrado.motivo()));

        // 11 · Flag apagado despues de medir: no-op pero conserva lo ya recogido.
        NavPlannerMetrics.Snapshot conDatos = NavPlannerMetrics.snapshot();
        NavPlannerMetrics.setEnabled(false);
        long idOff = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.GLOBAL, C, 1L, 1);
        NavPlannerMetrics.advance(conDatos.abiertas().isEmpty() ? 1L : conDatos.abiertas().get(0).id(), 1, 1L);
        comprobar("con flag apagado begin vuelve a 0", idOff == NavPlannerMetrics.SIN_BUSQUEDA);
        comprobar("con flag apagado no se modifica el snapshot",
                NavPlannerMetrics.snapshot().equals(conDatos));
        NavPlannerMetrics.setEnabled(true);

        // 12 · reset: limpia datos y conserva el flag.
        NavPlannerMetrics.reset();
        comprobar("reset limpia totales, registros y entidades",
                totalesVacios(NavPlannerMetrics.snapshot().totales())
                        && NavPlannerMetrics.snapshot().busquedas().isEmpty()
                        && NavPlannerMetrics.snapshot().abiertas().isEmpty()
                        && NavPlannerMetrics.snapshot().entidades().isEmpty());
        comprobar("reset no cambia el flag", NavPlannerMetrics.enabled());

        // 13 · dumpLinea / dumpCsv: una linea de totales y CSV completo.
        long idD = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.LOCAL, C, 300L, 20);
        NavPlannerMetrics.advance(idD, 3, 300L);
        NavPlannerMetrics.end(idD, true, false, 4, "ruta");
        String linea = NavPlannerMetrics.dumpLinea();
        String[] campos = linea.split(",", -1);
        comprobar("dumpLinea es UNA linea CSV con 11 campos",
                linea.indexOf('\n') < 0 && campos.length == 11 && "METRICS".equals(campos[0]));
        comprobar("dumpLinea refleja los totales exactos",
                "1".equals(campos[1]) && "1".equals(campos[2]) && "0".equals(campos[3])
                        && "0".equals(campos[4]) && "3".equals(campos[5]) && "300".equals(campos[6])
                        && "0".equals(campos[7]) && "0".equals(campos[8]) && "0".equals(campos[9])
                        && "1".equals(campos[10]));
        String csv = NavPlannerMetrics.dumpCsv();
        comprobar("dumpCsv tiene cabecera de totales, busquedas y entidades",
                csv.contains(NavPlannerMetrics.cabeceraLinea()) && csv.contains("BUSQUEDA,id,tipo")
                        && csv.contains("ENTIDAD,entidad"));
        comprobar("dumpCsv incluye la busqueda y la entidad inyectadas",
                csv.contains("BUSQUEDA," + idD + ",LOCAL," + C) && csv.contains("ENTIDAD," + C)
                        && csv.contains(",ruta"));

        // 14 · Cotas de memoria: entidades y busquedas activas.
        NavPlannerMetrics.reset();
        for (int i = 0; i < NavPlannerMetrics.MAX_ENTIDADES + 20; i++) {
            UUID uuid = new UUID(0x1000L, i);
            long id = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.SINTETICA, uuid, i, 1);
            NavPlannerMetrics.end(id, true, false, 0, "ok");
        }
        NavPlannerMetrics.Snapshot saturado = NavPlannerMetrics.snapshot();
        int esperadas = NavPlannerMetrics.MAX_ENTIDADES + 20;
        comprobar("el mapa de entidades queda acotado", saturado.entidades().size() <= NavPlannerMetrics.MAX_ENTIDADES);
        comprobar("el desalojo no pierde los totales agregados",
                saturado.totales().busquedas() == esperadas && saturado.totales().busquedasCompletadas() == esperadas);
        comprobar("los registros cerrados quedan acotados",
                saturado.busquedas().size() <= NavPlannerMetrics.MAX_REGISTROS);

        NavPlannerMetrics.reset();
        for (int i = 0; i < NavPlannerMetrics.MAX_ACTIVAS + 10; i++) {
            NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.SINTETICA, null, i, 1);
        }
        NavPlannerMetrics.Snapshot activasSaturadas = NavPlannerMetrics.snapshot();
        comprobar("el mapa de busquedas activas queda acotado",
                activasSaturadas.abiertas().size() <= NavPlannerMetrics.MAX_ACTIVAS);
        comprobar("las busquedas desalojadas siguen contadas en totales",
                activasSaturadas.totales().busquedas() == NavPlannerMetrics.MAX_ACTIVAS + 10L);

        NavPlannerMetrics.reset();
        NavPlannerMetrics.setEnabled(false);

        // Evidencia final.
        System.out.println(NavPlannerMetrics.cabeceraLinea());
        System.out.println(NavPlannerMetrics.dumpLinea());
        if (fallos > 0) {
            System.out.println("NAV_PLANNER_METRICS_TEST: FAIL (" + fallos + " de " + comprobaciones + ")");
            System.exit(1);
        }
        System.out.println("NAV_PLANNER_METRICS_TEST: OK (" + comprobaciones + " comprobaciones)");
    }

    private static boolean totalesVacios(NavPlannerMetrics.TotalesSnapshot t) {
        return t.busquedas() == 0L && t.busquedasCompletadas() == 0L && t.busquedasFallidas() == 0L
                && t.canceladas() == 0L && t.expansiones() == 0L && t.nanos() == 0L
                && t.presupuestoAgotado() == 0L && t.replanificaciones() == 0L && t.reanudadas() == 0L
                && t.entidades() == 0;
    }

    private static NavPlannerMetrics.BusquedaSnapshot buscar(List<NavPlannerMetrics.BusquedaSnapshot> lista,
                                                             long id) {
        for (NavPlannerMetrics.BusquedaSnapshot busqueda : lista) {
            if (busqueda.id() == id) {
                return busqueda;
            }
        }
        return null;
    }

    private static NavPlannerMetrics.EntidadSnapshot buscarEntidad(List<NavPlannerMetrics.EntidadSnapshot> lista,
                                                                    UUID uuid) {
        for (NavPlannerMetrics.EntidadSnapshot entidad : lista) {
            if (uuid.equals(entidad.entidad())) {
                return entidad;
            }
        }
        return null;
    }

    private static void comprobar(String nombre, boolean ok) {
        comprobaciones++;
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + nombre);
        }
    }
}
