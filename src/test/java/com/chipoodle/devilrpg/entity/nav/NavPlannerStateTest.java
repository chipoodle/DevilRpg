package com.chipoodle.devilrpg.entity.nav;

public final class NavPlannerStateTest {
    private static int fallos;
    public static void main(String[] args) {
        comprobar("escalada con hueco y soporte", NavClimbPolicy.escaladaValida(true, true));
        comprobar("escalada sin hueco rechazada", !NavClimbPolicy.escaladaValida(false, true));
        comprobar("escalada sin soporte rechazada", !NavClimbPolicy.escaladaValida(true, false));
        comprobar("DROP es waypoint", NavClimbPolicy.esWaypointDePie(NavRoute.Transition.DROP));
        comprobar("BUILD no es waypoint", !NavClimbPolicy.esWaypointDePie(NavRoute.Transition.BUILD));
        comprobar("WALK es waypoint", NavClimbPolicy.esWaypointDePie(NavRoute.Transition.WALK));
        comprobar("STEP_UP es waypoint", NavClimbPolicy.esWaypointDePie(NavRoute.Transition.STEP_UP));
        comprobar("JUMP_UP es waypoint", NavClimbPolicy.esWaypointDePie(NavRoute.Transition.JUMP_UP));
        comprobar("sucesor canonico JUMP_UP delta0 usa pies validados",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(301, 118, 300, true, 120),
                        new int[]{301, 120, 300}));
        comprobar("sucesor canonico JUMP_UP normal usa pies",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(301, 118, 300, true, 120),
                        new int[]{301, 120, 300}));
        comprobar("sucesor canonico STEP_UP usa pies",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(301, 119, 300, true, 120),
                        new int[]{301, 120, 300}));
        comprobar("sucesor canonico WALK conserva candidato",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(301, 120, 300, false, 120),
                        new int[]{301, 120, 300}));
        comprobar("sucesor canonico DROP conserva candidato",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(305, 117, 300, false, 117),
                        new int[]{305, 117, 300}));
        comprobar("sucesor canonico BREAK conserva candidato",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(305, 120, 300, false, 120),
                        new int[]{305, 120, 300}));
        comprobar("sucesor canonico BUILD conserva candidato",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(305, 119, 300, false, 120),
                        new int[]{305, 119, 300}));
        comprobar("dos candidatos al mismo sucesor canonico colisionan de forma estable",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(301, 118, 300, true, 120),
                        NavSuccessorPolicy.sucesor(301, 120, 300, false, 120)));
        NavDropExclusionPolicy ex = new NavDropExclusionPolicy();
        long k = NavDropExclusionPolicy.clave(1,2,3,4,5,6);
        ex.excluir(k, 100L);
        comprobar("DROP rechazado excluido con misma huella", ex.excluida(k, 100L));
        comprobar("DROP vuelve a ser elegible si cambia el terreno", !ex.excluida(k, 101L));
        ex.excluir(k, 100L);
        for (int i = 0; i < 40; i++) ex.excluir(NavDropExclusionPolicy.clave(i,0,0, i,1,0), 7L);
        comprobar("exclusion acotada a 32", ex.tamano() <= NavDropExclusionPolicy.MAX_ENTRADAS);
        comprobar("DROP contiguo origen->aterrizaje", NavDropTransitionPolicy.aterrizajeContiguo(10,10,11,10));
        comprobar("DROP no contiguo rechazado", !NavDropTransitionPolicy.aterrizajeContiguo(10,10,12,10));
        comprobar("origen compatible a 1 bloque", NavDropTransitionPolicy.origenCompatible(10,64,10,11,64,10));
        comprobar("origen divergente rechazado", !NavDropTransitionPolicy.origenCompatible(12,64,10,10,64,10));
        comprobar("DROP ejecutable con hueco y soporte",
                NavDropTransitionPolicy.ejecutable(true, true, true, true, -2, 4));
        comprobar("DROP sin hueco rechazado",
                !NavDropTransitionPolicy.ejecutable(false, true, true, true, -2, 4));
        comprobar("DROP sin soporte rechazado",
                !NavDropTransitionPolicy.ejecutable(true, false, true, true, -2, 4));
        comprobar("DROP con origen divergente rechazado",
                !NavDropTransitionPolicy.ejecutable(true, true, false, true, -2, 4));
        NavDropExclusionPolicy ex2 = new NavDropExclusionPolicy();
        ex2.excluir(NavDropExclusionPolicy.clave(0,0,0,1,0,0), 7L);
        NavGraphSearch.Result sr = NavGraphSearch.search(0L, 0, new NavGraphSearch.Graph() {
            public void expand(long s, int f, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                if (s == 0L) {
                    if (!ex2.excluida(NavDropExclusionPolicy.clave(0,0,0,1,0,0), 7L)) {
                        out.accept(new NavGraphSearch.Edge(1L, 1.0D, NavRoute.Transition.DROP, 0));
                    }
                    out.accept(new NavGraphSearch.Edge(2L, 2.0D, NavRoute.Transition.WALK, 0));
                } else if (s == 2L) {
                    out.accept(new NavGraphSearch.Edge(3L, 1.0D, NavRoute.Transition.WALK, 0));
                }
            }
            public boolean goal(long s, int f) { return s == 3L; }
            public double heuristic(long s) { return s == 3L ? 0.0D : 1.0D; }
            public double distance(long s) { return s == 3L ? 0.0D : 1.0D; }
        }, 100);
        comprobar("busqueda evita DROP excluido y llega por alternativa",
                sr.found() && sr.route().stream().noneMatch(e -> e.state() == 1L));
        comprobar("busqueda acotada no supera presupuesto", sr.expansions() <= 100);
        NavGraphSearch.Result ciclo = NavGraphSearch.search(10L, 64, new NavGraphSearch.Graph() {
            public void expand(long s, int f, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                if (s == 10L) out.accept(new NavGraphSearch.Edge(11L, 1.0D, NavRoute.Transition.WALK, 64));
                else if (s == 11L) out.accept(new NavGraphSearch.Edge(10L, 1.0D, NavRoute.Transition.WALK, 64));
            }
            public boolean goal(long s, int f) { return s == 99L; }
            public double heuristic(long s) { return 1.0D; }
            public double distance(long s) { return 1.0D; }
        }, 5);
        comprobar("ciclo termina sin exceder presupuesto", !ciclo.found() && ciclo.expansions() <= 5);
        NavGraphSearch.Result rama = NavGraphSearch.search(1L, 64, new NavGraphSearch.Graph() {
            public void expand(long s, int f, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                for (int i = 1; i <= 56; i++) {
                    out.accept(new NavGraphSearch.Edge(s * 100L + i, 1.0D, NavRoute.Transition.WALK, f));
                }
            }
            public boolean goal(long s, int f) { return false; }
            public double heuristic(long s) { return 0.0D; }
            public double distance(long s) { return 1.0D; }
        }, 100);
        comprobar("grafo de 56 ramas agota presupuesto de forma acotada",
                !rama.found() && rama.hitBudget() && rama.expansions() == 100);
        comprobar("waypoint normal dentro de ventana",
                NavWaypointWindowPolicy.aceptable(4, 4, 6, false, 44, 6, 4));
        comprobar("waypoint normal fuera de vertical rechazado",
                !NavWaypointWindowPolicy.aceptable(4, 4, 7, false, 44, 6, 4));
        comprobar("DROP profundo dentro de caida segura aceptado",
                NavWaypointWindowPolicy.aceptable(1, 0, -10, true, 44, 6, 12));
        comprobar("DROP mas profundo que caida segura rechazado",
                !NavWaypointWindowPolicy.aceptable(1, 0, -13, true, 44, 6, 12));
        comprobar("waypoint fuera de horizontal rechazado",
                !NavWaypointWindowPolicy.aceptable(45, 0, 0, false, 44, 6, 12));
        NavGraphSearch.Result subida = NavGraphSearch.search(20L, 64, new NavGraphSearch.Graph() {
            public void expand(long s, int f, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                if (s == 20L) out.accept(new NavGraphSearch.Edge(21L, 1.0D, NavRoute.Transition.STEP_UP, 65));
            }
            public boolean goal(long s, int f) { return s == 21L; }
            public double heuristic(long s) { return s == 21L ? 0.0D : 1.0D; }
            public double distance(long s) { return s == 21L ? 0.0D : 1.0D; }
        }, 10);
        comprobar("ruta conserva STEP_UP y pies y+1", subida.found()
                && subida.route().get(0).transition() == NavRoute.Transition.STEP_UP
                && subida.route().get(0).feet() == 65);
        if (fallos > 0) { System.out.println("NAV_PLANNER_STATE_TEST: FAIL (" + fallos + ")"); System.exit(1); }
        System.out.println("NAV_PLANNER_STATE_TEST: OK");
    }
    private static void comprobar(String n, boolean ok) { if (!ok) { fallos++; System.out.println("  FALLO: " + n); } else { System.out.println("  ok: " + n); } }
}
