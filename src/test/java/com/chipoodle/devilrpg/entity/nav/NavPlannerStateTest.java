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
        comprobar("JUMP_UP legacy no es waypoint ordinario", !NavClimbPolicy.esWaypointDePie(NavRoute.Transition.JUMP_UP));
        comprobar("sucesor canonico BUILD usa la celda de pies resultante",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(301, 119, 300, true, 120),
                        new int[]{301, 120, 300}));
        comprobar("BUILD coloca debajo de la celda de pies",
                NavBuildTransitionPolicy.successorFeet(new net.minecraft.core.BlockPos(301, 119, 300))
                        .equals(new net.minecraft.core.BlockPos(301, 120, 300)));
        comprobar("NavPlanner BUILD canonical successor is the resulting feet cell",
                NavPlanner.sucesorCanonico(new net.minecraft.core.BlockPos(305, 119, 300),
                        NavObstruction.Kind.BLOCKED, NavRoute.Transition.BUILD, 120)
                        .equals(new net.minecraft.core.BlockPos(305, 120, 300)));
        comprobar("NavPlanner BUILD state successor is the resulting feet cell",
                NavPlanner.estadoSucesor(new net.minecraft.core.BlockPos(305, 119, 300),
                        NavRoute.Transition.BUILD, 120)
                        .equals(new net.minecraft.core.BlockPos(305, 120, 300)));
        comprobar("sucesor canonico STEP_UP usa pies",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(301, 119, 300, true, 120),
                        new int[]{301, 120, 300}));
        comprobar("sucesor canonico WALK conserva candidato",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(301, 120, 300, false, 120),
                        new int[]{301, 120, 300}));
        comprobar("sucesor canonico DROP usa la altura real de aterrizaje",
                NavPlanner.sucesorCanonico(new net.minecraft.core.BlockPos(305, 117, 300),
                        NavObstruction.Kind.DROP, NavRoute.Transition.DROP, 114)
                        .equals(new net.minecraft.core.BlockPos(305, 114, 300)));
        comprobar("estadoSucesor DROP usa la altura real de aterrizaje",
                NavPlanner.estadoSucesor(new net.minecraft.core.BlockPos(305, 117, 300),
                        NavRoute.Transition.DROP, 114)
                        .equals(new net.minecraft.core.BlockPos(305, 114, 300)));
        comprobar("sucesor canonico BREAK conserva candidato",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(305, 120, 300, false, 120),
                        new int[]{305, 120, 300}));
        comprobar("sucesor canonico BUILD usa pies sobre la celda colocada",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(305, 119, 300, true, 120),
                        new int[]{305, 120, 300}));
        comprobar("dos candidatos al mismo sucesor canonico colisionan de forma estable",
                java.util.Arrays.equals(NavSuccessorPolicy.sucesor(301, 118, 300, true, 120),
                        NavSuccessorPolicy.sucesor(301, 120, 300, false, 120)));
        NavDropExclusionPolicy ex = new NavDropExclusionPolicy();
        long k = NavDropExclusionPolicy.clave(1,2,3,4,5,6);
        ex.excluir(k, 100L);
        comprobar("DROP rechazado excluido con misma huella", ex.excluida(k, 100L));
        comprobar("DROP vuelve a ser elegible si cambia el terreno", !ex.excluida(k, 101L));
        long destino = NavDropExclusionPolicy.claveDestino(4, 5, 6);
        comprobar("clave de destino no colisiona con clave de arista",
                destino != NavDropExclusionPolicy.clave(0, 0, 0, 4, 5, 6));
        ex.excluir(destino, 200L);
        comprobar("landing queda registrado", ex.contiene(destino));
        comprobar("huella del landing queda registrada", Long.valueOf(200L).equals(ex.huellaRegistrada(destino)));
        comprobar("landing bloqueado excluye cualquier origen con misma huella", ex.excluida(destino, 200L));
        comprobar("landing vuelve a ser elegible al cambiar la columna", !ex.excluida(destino, 201L));
        comprobar("huella obsoleta se elimina", !ex.contiene(destino));
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

        // DROP graph identity, feet metadata, goal and subsequent expansion must all use the
        // same physical landing cell rather than the raw unsupported candidate.
        net.minecraft.core.BlockPos dropCandidate = new net.minecraft.core.BlockPos(1, 68, 0);
        net.minecraft.core.BlockPos dropState = NavPlanner.sucesorCanonico(dropCandidate,
                NavObstruction.Kind.DROP, NavRoute.Transition.DROP, 67);
        net.minecraft.core.BlockPos dropGoal = new net.minecraft.core.BlockPos(2, 67, 0);
        final boolean[] coherentDrop = {true};
        final boolean[] expandedLanding = {false};
        NavGraphSearch.Graph dropGraph = new NavGraphSearch.Graph() {
            public void expand(long state, int feet, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(state);
                coherentDrop[0] &= pos.getY() == feet;
                if (pos.getX() == 0) {
                    out.accept(new NavGraphSearch.Edge(dropState.asLong(), 1.0D,
                            NavRoute.Transition.DROP, 67));
                } else if (pos.equals(dropState)) {
                    expandedLanding[0] = true;
                    out.accept(new NavGraphSearch.Edge(dropGoal.asLong(), 1.0D,
                            NavRoute.Transition.WALK, 67));
                }
            }
            public boolean goal(long state, int feet) {
                net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(state);
                coherentDrop[0] &= pos.getY() == feet;
                return pos.equals(dropGoal) && feet == dropGoal.getY();
            }
            public double heuristic(long state) {
                return dropGoal.distSqr(net.minecraft.core.BlockPos.of(state));
            }
            public double distance(long state) {
                return dropGoal.distSqr(net.minecraft.core.BlockPos.of(state));
            }
        };
        NavGraphSearch.Result dropSearch = NavGraphSearch.search(
                new net.minecraft.core.BlockPos(0, 70, 0).asLong(), 70, dropGraph, 16);
        comprobar("DROP A* state is the physical landing feet cell",
                dropSearch.found() && dropSearch.route().get(0).state() == dropState.asLong());
        comprobar("DROP successor expansion receives matching feet height",
                expandedLanding[0] && coherentDrop[0]);

        NavGraphSearch.Session staleSearch = NavGraphSearch.begin(0L, 64, new NavGraphSearch.Graph() {
            public void expand(long state, int feet, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                if (state == 0L) {
                    out.accept(new NavGraphSearch.Edge(1L, 10.0D, NavRoute.Transition.WALK, feet));
                    out.accept(new NavGraphSearch.Edge(2L, 1.0D, NavRoute.Transition.WALK, feet));
                } else if (state == 2L) {
                    out.accept(new NavGraphSearch.Edge(1L, 1.0D, NavRoute.Transition.WALK, feet));
                }
            }
            public boolean goal(long state, int feet) { return false; }
            public double heuristic(long state) { return 0.0D; }
            public double distance(long state) { return 1.0D; }
        }, 16);
        while (!staleSearch.advance(16)) { /* drain deterministic fixture */ }
        comprobar("A* skips obsolete OPEN record after a better g-score",
                staleSearch.totalStalePops() == 1 && staleSearch.expansions() == 3);
        comprobar("A* exposes bounded pop and useful-expansion metrics",
                staleSearch.totalPops() == 4 && staleSearch.lastExpansions() == 3);

        NavGraphSearch.Session reopenedSearch = NavGraphSearch.begin(0L, 64, new NavGraphSearch.Graph() {
            public void expand(long state, int feet, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                if (state == 0L) {
                    out.accept(new NavGraphSearch.Edge(1L, 5.0D, NavRoute.Transition.WALK, feet));
                    out.accept(new NavGraphSearch.Edge(2L, 1.0D, NavRoute.Transition.WALK, feet));
                } else if (state == 2L) {
                    out.accept(new NavGraphSearch.Edge(1L, 1.0D, NavRoute.Transition.WALK, feet));
                }
            }
            public boolean goal(long state, int feet) { return false; }
            public double heuristic(long state) { return state == 2L ? 5.0D : 0.0D; }
            public double distance(long state) { return 1.0D; }
        }, 16);
        while (!reopenedSearch.advance(16)) { /* drain deterministic fixture */ }
        comprobar("A* records a real closed-state reopen",
                reopenedSearch.totalReopens() == 1 && reopenedSearch.expansions() == 4);
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
        NavGraphSearch.Graph chain = new NavGraphSearch.Graph() {
            public void expand(long s, int f, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                if (s < 4L) out.accept(new NavGraphSearch.Edge(s + 1L, 1.0D,
                        NavRoute.Transition.WALK, f));
            }
            public boolean goal(long s, int f) { return s == 4L; }
            public double heuristic(long s) { return 4.0D - s; }
            public double distance(long s) { return 4.0D - s; }
        };
        NavGraphSearch.Session incremental = NavGraphSearch.begin(0L, 64, chain, 20);
        comprobar("A* incremental inicia pendiente", !incremental.complete());
        comprobar("A* incremental no publica resultado parcial",
                throwsIllegalState(incremental));
        int previousExpansions = 0;
        int slices = 0;
        while (!incremental.advance(1)) {
            int currentExpansions = incremental.expansions();
            comprobar("slice incremental respeta una expansion",
                    currentExpansions - previousExpansions <= 1);
            previousExpansions = currentExpansions;
            slices++;
            if (slices > 20) break;
        }
        NavGraphSearch.Result incrementalResult = incremental.result();
        NavGraphSearch.Result synchronousResult = NavGraphSearch.search(0L, 64, chain, 20);
        comprobar("A* incremental completa dentro del presupuesto",
                incrementalResult.found() && incrementalResult.expansions() <= 20);
        comprobar("A* incremental conserva la ruta síncrona",
                incrementalResult.route().equals(synchronousResult.route()));
        comprobar("A* incremental reconstruye todos los pasos desde el origen",
                incrementalResult.route().size() == 4
                        && incrementalResult.route().get(0).state() == 1L
                        && incrementalResult.route().get(3).state() == 4L);
        comprobar("A* incremental requirió varios slices", slices >= 3);

        NavGraphSearch.Session bounded = NavGraphSearch.begin(100L, 64, new NavGraphSearch.Graph() {
            public void expand(long s, int f, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                out.accept(new NavGraphSearch.Edge(s + 1L, 1.0D, NavRoute.Transition.WALK, f));
                out.accept(new NavGraphSearch.Edge(s + 2L, 1.0D, NavRoute.Transition.WALK, f));
            }
            public boolean goal(long s, int f) { return false; }
            public double heuristic(long s) { return 0.0D; }
            public double distance(long s) { return 1.0D; }
        }, 9);
        while (!bounded.advance(2)) { /* resume with a strict per-slice ceiling */ }
        comprobar("A* incremental conserva el presupuesto total duro",
                bounded.expansions() == 9 && bounded.result().hitBudget());
        if (fallos > 0) { System.out.println("NAV_PLANNER_STATE_TEST: FAIL (" + fallos + ")"); System.exit(1); }
        System.out.println("NAV_PLANNER_STATE_TEST: OK");
    }
    private static boolean throwsIllegalState(NavGraphSearch.Session session) {
        try { session.result(); return false; }
        catch (IllegalStateException expected) { return true; }
    }
    private static void comprobar(String n, boolean ok) { if (!ok) { fallos++; System.out.println("  FALLO: " + n); } else { System.out.println("  ok: " + n); } }
}
