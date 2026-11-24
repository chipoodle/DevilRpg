package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A3 · <b>Implementación CONGELADA de la línea base del A* (mapas Java con cajas).</b>
 *
 * <p>Es una copia literal de {@code NavGraphSearch.Session} tal como estaba en {@code HEAD ae28c19a}
 * (antes de la optimización de colecciones primitivas de {@code NavSearchPerfTest}). NO se usa en
 * producción: existe solo como oráculo de rendimiento y de comportamiento.
 *
 * <p>Regla de mantenimiento: <b>no se toca</b>. Si mañana cambia el algoritmo de producción y este
 * oráculo deja de dar la misma ruta, la prueba de rendimiento falla a propósito: la comparación
 * antes/después deja de ser válida y hay que volver a congelar una línea base nueva.
 */
final class NavSearchBoxedBench {

    private record Node(long state, double g, double f, double h) {
    }

    static final class Sesion {
        private final NavGraphSearch.Graph graph;
        private final int maxExpansions;
        private final long start;
        private final int startFeet;
        private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f)
                .thenComparingDouble(Node::h));
        private final Map<Long, Double> bestG = new HashMap<>();
        private final Map<Long, Long> cameFrom = new HashMap<>();
        private final Map<Long, NavRoute.Transition> transitionTo = new HashMap<>();
        private final Map<Long, BlockPos> actionTo = new HashMap<>();
        private final Map<Long, BlockPos> standTo = new HashMap<>();
        private final Map<Long, Integer> feetTo = new HashMap<>();
        private final Set<Long> closed = new HashSet<>();
        private long front;
        private double bestDist;
        private long found = Long.MIN_VALUE;
        private int expansions;
        private int totalPops;
        private int totalStalePops;
        private int totalClosedSkips;
        private int totalReopens;
        private boolean complete;
        private boolean hitBudget;
        private String reason = "pendiente";

        Sesion(long start, int startFeet, NavGraphSearch.Graph graph, int maxExpansions) {
            if (maxExpansions < 1) throw new IllegalArgumentException("maxExpansions must be positive");
            this.graph = graph;
            this.maxExpansions = maxExpansions;
            this.start = start;
            this.startFeet = startFeet;
            double h0 = graph.heuristic(start);
            open.add(new Node(start, 0.0D, h0, h0));
            bestG.put(start, 0.0D);
            feetTo.put(start, startFeet);
            front = start;
            bestDist = graph.distance(start);
        }

        boolean advance(int expansionSlice) {
            if (expansionSlice < 1) throw new IllegalArgumentException("expansionSlice must be positive");
            if (complete) return true;
            int expandedThisSlice = 0;
            int poppedThisSlice = 0;
            int maxPopsThisSlice = expansionSlice > Integer.MAX_VALUE / 2
                    ? Integer.MAX_VALUE : expansionSlice * 2;
            while (!open.isEmpty() && expandedThisSlice < expansionSlice
                    && poppedThisSlice < maxPopsThisSlice) {
                Node current = open.poll();
                poppedThisSlice++;
                totalPops++;
                Double best = bestG.get(current.state());
                if (best == null || Double.compare(current.g(), best) != 0) {
                    totalStalePops++;
                    continue;
                }
                if (closed.contains(current.state())) {
                    totalClosedSkips++;
                    continue;
                }
                if (expansions >= maxExpansions) {
                    hitBudget = true;
                    complete = true;
                    reason = "presupuesto global agotado";
                    break;
                }
                closed.add(current.state());
                expansions++;
                expandedThisSlice++;
                double d = graph.distance(current.state());
                if (d < bestDist) {
                    bestDist = d;
                    front = current.state();
                }
                int feet = feetTo.getOrDefault(current.state(), startFeet);
                if (graph.goal(current.state(), feet)) {
                    found = current.state();
                    complete = true;
                    reason = "encontrado";
                    break;
                }
                final long from = current.state();
                final int fromFeet = feet;
                graph.expand(from, fromFeet, edge -> {
                    if (edge.transition() == NavRoute.Transition.BREAK
                            && (edge.actionPos() == null || edge.standPos() == null)) return;
                    double tentative = current.g() + edge.cost();
                    Double known = bestG.get(edge.to());
                    if (known != null && known <= tentative) return;
                    if (closed.remove(edge.to())) {
                        totalReopens++;
                    }
                    bestG.put(edge.to(), tentative);
                    feetTo.put(edge.to(), edge.feet());
                    cameFrom.put(edge.to(), from);
                    transitionTo.put(edge.to(), edge.transition());
                    actionTo.put(edge.to(), edge.actionPos());
                    standTo.put(edge.to(), edge.standPos());
                    double h = graph.heuristic(edge.to());
                    open.add(new Node(edge.to(), tentative, tentative + h, h));
                });
            }
            if (!complete && open.isEmpty()) {
                complete = true;
                reason = "frente alcanzable agotado";
            }
            return complete;
        }

        int expansions() { return expansions; }
        int frontierSize() { return open.size(); }
        int totalPops() { return totalPops; }
        int totalStalePops() { return totalStalePops; }
        int totalClosedSkips() { return totalClosedSkips; }
        int totalReopens() { return totalReopens; }

        NavGraphSearch.Result result() {
            if (!complete) throw new IllegalStateException("search is still pending");
            List<NavGraphSearch.Step> route = new ArrayList<>();
            long cursor = found != Long.MIN_VALUE ? found : front;
            while (cursor != start) {
                Long prev = cameFrom.get(cursor);
                if (prev == null) break;
                route.add(new NavGraphSearch.Step(cursor,
                        transitionTo.getOrDefault(cursor, NavRoute.Transition.WALK),
                        feetTo.getOrDefault(cursor, startFeet), actionTo.get(cursor), standTo.get(cursor)));
                cursor = prev;
            }
            Collections.reverse(route);
            return new NavGraphSearch.Result(found != Long.MIN_VALUE, route, front, expansions, hitBudget, reason);
        }
    }

    private NavSearchBoxedBench() {
    }
}
