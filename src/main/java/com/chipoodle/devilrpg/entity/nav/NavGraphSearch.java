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
 * Núcleo de busqueda A* acotada, sin Minecraft. La validacion de movimiento sigue viviendo en el
 * adaptador de produccion; aqui solo se decide que sucesores ya validados entran en la busqueda.
 *
 * A BREAK edge has two different positions: {@code to} is the post-break traversable state
 * (the formerly solid target), while {@code standPos} is the physical attack cell. They must not
 * share a search-state key: reaching the attack cell by WALK does not mean the obstruction was removed.
 */
public final class NavGraphSearch {
    public record Edge(long to, double cost, NavRoute.Transition transition, int feet,
                       BlockPos actionPos, BlockPos standPos) {
        public Edge(long to, double cost, NavRoute.Transition transition, int feet) {
            this(to, cost, transition, feet, null, null);
        }

        public Edge(long to, double cost, NavRoute.Transition transition, int feet, BlockPos actionPos) {
            this(to, cost, transition, feet, actionPos, null);
        }
    }

    public interface Graph {
        void expand(long state, int feet, java.util.function.Consumer<Edge> out);

        boolean goal(long state, int feet);

        double heuristic(long state);

        double distance(long state);
    }

    public record Step(long state, NavRoute.Transition transition, int feet,
                       BlockPos actionPos, BlockPos standPos) {
        public Step(long state, NavRoute.Transition transition, int feet) {
            this(state, transition, feet, null, null);
        }

        public Step(long state, NavRoute.Transition transition, int feet, BlockPos actionPos) {
            this(state, transition, feet, actionPos, null);
        }
    }

    public record Result(boolean found, List<Step> route, long front, int expansions,
                         boolean hitBudget, String reason) {
    }

    private record Node(long state, double g, double f, double h) {
    }

    private NavGraphSearch() {
    }

    /**
     * Resumable A* state. Each call to {@link #advance(int)} expands at most the supplied
     * number of non-stale frontier nodes. All OPEN/CLOSED and predecessor state survives
     * between server ticks; the total expansion ceiling remains independent and unchanged.
     */
    public static final class Session {
        private final Graph graph;
        private final int maxExpansions;
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
        private boolean complete;
        private boolean hitBudget;
        private String reason = "pendiente";

        public Session(long start, int startFeet, Graph graph, int maxExpansions) {
            if (maxExpansions < 1) throw new IllegalArgumentException("maxExpansions must be positive");
            this.graph = graph;
            this.maxExpansions = maxExpansions;
            double h0 = graph.heuristic(start);
            open.add(new Node(start, 0.0D, h0, h0));
            bestG.put(start, 0.0D);
            feetTo.put(start, startFeet);
            front = start;
            bestDist = graph.distance(start);
        }

        /** @return true only when the search has reached a terminal state. */
        public boolean advance(int expansionSlice) {
            if (expansionSlice < 1) throw new IllegalArgumentException("expansionSlice must be positive");
            if (complete) return true;
            int expandedThisSlice = 0;
            while (!open.isEmpty() && expandedThisSlice < expansionSlice) {
                Node current = open.poll();
                if (closed.contains(current.state())) continue;
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
                int feet = feetTo.getOrDefault(current.state(), 0);
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
                    closed.remove(edge.to());
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

        public boolean complete() { return complete; }
        public int expansions() { return expansions; }
        public int frontierSize() { return open.size(); }

        public Result result() {
            if (!complete) throw new IllegalStateException("search is still pending");
            List<Step> route = new ArrayList<>();
            long cursor = found != Long.MIN_VALUE ? found : front;
            while (cursor != front && cursor != Long.MIN_VALUE) {
                Long prev = cameFrom.get(cursor);
                if (prev == null) break;
                route.add(new Step(cursor, transitionTo.getOrDefault(cursor, NavRoute.Transition.WALK),
                        feetTo.getOrDefault(cursor, 0), actionTo.get(cursor), standTo.get(cursor)));
                cursor = prev;
            }
            Collections.reverse(route);
            return new Result(found != Long.MIN_VALUE, route, front, expansions, hitBudget, reason);
        }
    }

    public static Session begin(long start, int startFeet, Graph graph, int maxExpansions) {
        return new Session(start, startFeet, graph, maxExpansions);
    }

    /** Compatibility wrapper for callers that deliberately require a synchronous search. */
    public static Result search(long start, int startFeet, Graph graph, int maxExpansions) {
        Session session = begin(start, startFeet, graph, maxExpansions);
        while (!session.advance(Math.max(1, maxExpansions))) {
            // The compatibility API retains its historical synchronous contract.
        }
        return session.result();
    }
}
}
