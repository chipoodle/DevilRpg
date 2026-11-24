package com.chipoodle.devilrpg.entity.nav;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AV · <b>Contrato headless del catálogo de escenarios runtime</b> (no arranca Minecraft).
 *
 * <p>El arnés {@code tools/nav/NavScenarioHarness.java} declara su catálogo en un bloque de texto estable
 * ({@code NAV-SCENARIO-CATALOG v1}) y lo construye en {@code construirCatalogo()}. Este test comprueba, sin
 * ejecutar servidor ni mundo:
 * <ol>
 *   <li>que el bloque y los builders declaran EXACTAMENTE los 21 escenarios exigidos, sin duplicados;</li>
 *   <li>que cada fila declara las transiciones/acciones/esperas/presupuesto/radio/roturas que el contrato fija;</li>
 *   <li>que el lanzador y las propiedades Gradle existen y no dependen de {@code a2nDiagnostics};</li>
 *   <li>que las políticas puras que las geometrías necesitan siguen admitiendo lo que el catálogo promete
 *       (STEP_UP +1, BUILD +1/-3, DROP -3, rechazo de -4 y de subida +2);</li>
 *   <li>que el arnés usa la instrumentación existente (enabled/reset/dumpCsv) y publica el contrato de índice y
 *       las líneas NAVSCN/METRICS convenidas con el agregador de rendimiento.</li>
 * </ol>
 *
 * <p><b>Límite honesto:</b> esto valida el CONTRATO declarado, no que cada geometría produzca la ruta esperada en
 * runtime. Esa evidencia la da el arnés al ejecutarse con {@code runServer} (preflight con el planner real + criterios
 * por escenario). No se declara PASS de runtime desde este test.
 */
public final class NavScenarioContractTest {

    private static int comprobaciones;
    private static int fallos;

    private static final String[] IDS = {
            "campo_abierto", "paso_arriba", "caida", "romper", "construir",
            "paso_arriba_romper", "paso_arriba_construir", "caida_romper", "caida_construir",
            "romper_construir", "caida_romper_construir", "escalera", "columna", "muro", "tunel",
            "terreno_dinamico", "inaccesible", "astar_reanudacion", "presupuesto_agotado",
            "bloqueo_recuperacion", "limites"
    };

    private static final String CABECERA_INDICE =
            "escenario,repeticion,entidad,tipo_busqueda,presupuesto,encontrado,presupuesto_agotado,"
                    + "longitud_ruta,expansiones,nanos_metricas,nanos_pared,avances,ticks,aristas,"
                    + "candidatos,encolados,motivo,metrics_csv";

    public static void main(String[] args) {
        try {
            Path raiz = raiz();
            Path arnes = raiz.resolve("tools/nav/NavScenarioHarness.java");
            Path lanzador = raiz.resolve("tools/nav/lanzar-nav-harness.ps1");
            Path gradle = raiz.resolve("build.gradle");
            check("existe tools/nav/NavScenarioHarness.java", Files.isRegularFile(arnes));
            check("existe tools/nav/lanzar-nav-harness.ps1", Files.isRegularFile(lanzador));
            check("existe build.gradle", Files.isRegularFile(gradle));
            String fuente = Files.readString(arnes);
            String launcher = Files.readString(lanzador);
            String build = Files.readString(gradle);

            check("el arnés declara el bloque de catálogo v1", fuente.contains("NAV-SCENARIO-CATALOG v1"));
            List<Fila> filas = leerCatalogo(fuente);
            Map<String, Fila> porId = new LinkedHashMap<>();
            for (Fila f : filas) {
                check("id no duplicado: " + f.id, !porId.containsKey(f.id));
                porId.put(f.id, f);
            }
            check("catálogo con 21 escenarios (hay " + porId.size() + ")", porId.size() == IDS.length);
            for (String id : IDS) {
                check("catálogo contiene " + id, porId.containsKey(id));
            }

            Map<String, Fila> contrato = contrato();
            for (String id : IDS) {
                Fila real = porId.get(id);
                Fila esperada = contrato.get(id);
                if (real == null || esperada == null) {
                    continue;
                }
                check(id + ": transiciones " + esperada.transiciones + " (real " + real.transiciones + ")",
                        real.transiciones.equals(esperada.transiciones));
                check(id + ": acciones " + esperada.acciones + " (real " + real.acciones + ")",
                        real.acciones.equals(esperada.acciones));
                check(id + ": llegada/fallo", real.llegada == esperada.llegada && real.fallo == esperada.fallo);
                check(id + ": presupuesto de ticks " + esperada.ticks + " (real " + real.ticks + ")",
                        real.ticks == esperada.ticks);
                check(id + ": radio " + esperada.radio + " (real " + real.radio + ")", real.radio == esperada.radio);
                check(id + ": roturasMin " + esperada.roturas + " (real " + real.roturas + ")",
                        real.roturas == esperada.roturas);
                check(id + ": nota no vacía", !real.nota.isBlank());
            }

            Set<String> builders = builders(fuente);            check("builders declarados = " + IDS.length + " (hay " + builders.size() + ")", builders.size() == IDS.length);
            check("builders y catálogo coinciden", builders.equals(new LinkedHashSet<>(List.of(IDS))));

            check("lanzador activa -PnavHarness=true", launcher.contains("-PnavHarness=true"));
            check("lanzador sincroniza el arnés canónico", launcher.contains("NavScenarioHarness.java"));
            check("lanzador usa build/av y no run/", launcher.contains("build\\av") || launcher.contains("build/av"));
            check("lanzador pasa run/rep/escenarios", launcher.contains("-PnavHarnessRun")
                    && launcher.contains("-PnavHarnessRep") && launcher.contains("-PnavHarnessScenarios"));
            check("build.gradle conoce navHarness", build.contains("includeNavHarness")
                    && build.contains("navHarnessRun"));
            check("build.gradle registra el contrato headless", build.contains("navScenarioContractTest"));
            check("build.gradle pasa la raíz a2n.raiz para el run", build.contains("systemProperty 'a2n.raiz'"));
            check("build.gradle excluye el arnés de navegación fuera de -PnavHarness",
                    build.contains("exclude 'com/chipoodle/devilrpg/debug/NavScenarioHarness.java'"));
            check("el arnés tiene guardia ENABLED por -PnavHarnessRun",
                    fuente.contains("System.getProperty(\"devilrpg.nav.harness.run\")")
                            && fuente.contains("if (!ENABLED)"));

            check("el arnés habilita NavPlannerMetrics", fuente.contains("NavPlannerMetrics.setEnabled(true)"));
            check("el arnés reinicia métricas por escenario", fuente.contains("NavPlannerMetrics.reset()"));
            check("el arnés vuelca dumpCsv() literal", fuente.contains("NavPlannerMetrics.dumpCsv()"));
            check("el arnés usa dumpLinea() al cierre", fuente.contains("NavPlannerMetrics.cabeceraLinea()"));
            check("cabecera de índice exacta", fuente.contains(CABECERA_INDICE));
            check("el arnés emite NAVSCN,", fuente.contains("\"NAVSCN,\""));
            check("el arnés emite METRICS al cierre", fuente.contains("\"METRICS,\""));
            check("el arnés emite NAV_FIN", fuente.contains("NAV_FIN"));
            check("el arnés no escribe en run/ ni run/saves", !fuente.contains("Path.of(\"run"));
            check("el arnés no usa tools/c4n", !fuente.contains("tools/c4n"));

            // Los escenarios especiales se cablean con su tipo, no por texto libre.
            check("astar_reanudacion usa Tipo.AESTRELLA", fuente.contains(".tipo(Tipo.AESTRELLA)"));
            check("inaccesible usa Tipo.INALCANZABLE", fuente.contains(".tipo(Tipo.INALCANZABLE)"));
            check("presupuesto_agotado usa Tipo.PRESUPUESTO", fuente.contains(".tipo(Tipo.PRESUPUESTO)"));
            check("limites usa Tipo.LIMITES", fuente.contains(".tipo(Tipo.LIMITES)"));
            check("bloqueo_recuperacion usa Tipo.BLOQUEO", fuente.contains(".tipo(Tipo.BLOQUEO)"));
            check("terreno_dinamico usa Tipo.DINAMICO", fuente.contains(".tipo(Tipo.DINAMICO)"));

            // Políticas puras que sostienen las geometrías declaradas.
            check("STEP_UP +1 es transición ordinaria",
                    NavVerticalTransitionPolicy.ordinaryTransition(1) == NavRoute.Transition.STEP_UP);
            check("subida +2 NO es transición ordinaria",
                    NavVerticalTransitionPolicy.ordinaryTransition(2) == null);
            check("BUILD admite +1", NavBuildTransitionPolicy.allowsEdgeDelta(1, 3));
            check("BUILD admite -3", NavBuildTransitionPolicy.allowsEdgeDelta(-3, 3));
            check("BUILD rechaza -4", !NavBuildTransitionPolicy.allowsEdgeDelta(-4, 3));
            check("BUILD rechaza +2", !NavBuildTransitionPolicy.allowsEdgeDelta(2, 3));
            check("DROP -3 ejecutable", NavDropTransitionPolicy.ejecutable(true, true, true, true, -3, 3));
            check("DROP -4 no ejecutable", !NavDropTransitionPolicy.ejecutable(true, true, true, true, -4, 3));
            check("DROP exige aterrizaje estrictamente debajo",
                    NavDropTransitionPolicy.descensoFisicoValido(201, 198)
                            && !NavDropTransitionPolicy.descensoFisicoValido(198, 198));
            check("STEP_UP exige celda de pies válida",
                    NavFeetCellPolicy.isValid(true, false, false, false, false, false, true)
                            && !NavFeetCellPolicy.isValid(true, false, false, false, false, false, false));
            check("TUNNEL respeta tope por misión",
                    TunnelPlanPolicy.stepsAvailable(0) == 3 && TunnelPlanPolicy.stepsAvailable(4) == 2
                            && TunnelPlanPolicy.stepsAvailable(6) == 0);
        } catch (Throwable t) {
            fallos++;
            System.out.println("EXCEPCION NO CONTROLADA: " + t);
            t.printStackTrace(System.out);
        }
        if (fallos > 0) {
            System.out.println("NAV_SCENARIO_CONTRACT_TEST: FAIL (" + fallos + " fallos de "
                    + comprobaciones + ")");
            System.exit(1);
        }
        System.out.println("NAV_SCENARIO_CONTRACT_TEST: OK (" + comprobaciones
                + " comprobaciones; contrato declarado, NO evidencia runtime)");
    }

    private static Path raiz() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (Files.isRegularFile(p.resolve("tools/nav/NavScenarioHarness.java"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("no encuentro la raiz del repo desde " + Path.of("").toAbsolutePath());
    }

    private static List<Fila> leerCatalogo(String fuente) {
        List<Fila> filas = new ArrayList<>();
        for (String linea : fuente.split("\\R")) {
            String limpia = linea.trim();
            if (!limpia.startsWith("// SC|")) {
                continue;
            }
            String[] c = limpia.substring("// SC|".length()).split("\\|", -1);
            if (c.length > 0 && "id".equals(c[0])) {
                continue; // cabecera del bloque
            }
            check("fila de catálogo con 9 campos: " + limpia, c.length == 9);
            if (c.length != 9) {
                continue;
            }
            filas.add(new Fila(c[0], csv(c[1]), csv(c[2]), Boolean.parseBoolean(c[3]),
                    Boolean.parseBoolean(c[4]), Integer.parseInt(c[5]), Integer.parseInt(c[6]),
                    Integer.parseInt(c[7]), c[8]));
        }
        return filas;
    }

    private static Set<String> builders(String fuente) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = Pattern.compile("PENDIENTES\\.add\\(e\\(\"([a-z0-9_]+)\"").matcher(fuente);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    private static Set<String> csv(String valor) {
        Set<String> out = new LinkedHashSet<>();
        if (valor == null || valor.isBlank()) {
            return out;
        }
        for (String s : valor.split(",")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    private static Map<String, Fila> contrato() {
        Map<String, Fila> m = new LinkedHashMap<>();
        add(m, "campo_abierto", "", "", true, false, 600, 20, 0);
        add(m, "paso_arriba", "STEP_UP", "STEP_UP", true, false, 600, 20, 0);
        add(m, "caida", "DROP", "DROP", true, false, 700, 20, 0);
        add(m, "romper", "BREAK", "BREAK", true, false, 700, 20, 1);
        add(m, "construir", "BUILD", "BUILD", true, false, 1200, 20, 0);
        add(m, "paso_arriba_romper", "STEP_UP,BREAK", "STEP_UP,BREAK", true, false, 1100, 20, 1);
        add(m, "paso_arriba_construir", "STEP_UP,BUILD", "STEP_UP,BUILD", true, false, 1400, 20, 0);
        add(m, "caida_romper", "DROP,BREAK", "DROP,BREAK", true, false, 1100, 20, 1);
        add(m, "caida_construir", "BUILD,DROP", "BUILD,DROP", true, false, 1200, 20, 0);
        add(m, "romper_construir", "BREAK,BUILD", "BREAK,BUILD", true, false, 1500, 20, 1);
        add(m, "caida_romper_construir", "DROP,BREAK,BUILD", "DROP,BREAK,BUILD", true, false, 1800, 20, 1);
        add(m, "escalera", "STEP_UP", "STEP_UP", true, false, 800, 20, 0);
        add(m, "columna", "DROP", "DROP", true, false, 700, 20, 0);
        add(m, "muro", "BREAK", "BREAK", true, false, 1200, 20, 2);
        add(m, "tunel", "", "TUNNEL", true, false, 1800, 20, 0);
        add(m, "terreno_dinamico", "", "", true, false, 1000, 20, 0);
        add(m, "inaccesible", "", "", false, true, 1200, 20, 0);
        add(m, "astar_reanudacion", "", "", true, false, 1800, 40, 0);
        add(m, "presupuesto_agotado", "", "", false, false, 40, 20, 0);
        add(m, "bloqueo_recuperacion", "", "", true, false, 1500, 20, 0);
        add(m, "limites", "", "", false, false, 40, 20, 0);
        return m;
    }

    private static void add(Map<String, Fila> m, String id, String trans, String acc, boolean llegada,
                            boolean fallo, int ticks, int radio, int roturas) {
        m.put(id, new Fila(id, csv(trans), csv(acc), llegada, fallo, ticks, radio, roturas, "contrato"));
    }

    private static void check(String nombre, boolean ok) {
        comprobaciones++;
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + nombre);
        }
    }

    private record Fila(String id, Set<String> transiciones, Set<String> acciones, boolean llegada,
                        boolean fallo, int ticks, int radio, int roturas, String nota) {
    }
}
