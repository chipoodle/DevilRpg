package com.chipoodle.devilrpg.entity.nav;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Núcleo de busqueda A* acotada, sin Minecraft. La validacion de movimiento sigue viviendo en el
 * adaptador de produccion; aqui solo se decide que sucesores ya validados entran en la busqueda.
 *
 * A BREAK edge has two different positions: {@code to} is the post-break traversable state
 * (the formerly solid target), while {@code standPos} is the physical attack cell. They must not
 * share a search-state key: reaching the attack cell by WALK does not mean the obstruction was removed.
 *
 * <p>A3/perf · <b>Estado con colecciones primitivas</b> ({@code Long2*OpenHashMap}, {@code LongOpenHashSet}
 * de fastutil, librería que ya aporta el runtime de Minecraft). No cambia el algoritmo, el orden de la
 * cola de prioridad ni los resultados: solo evita una caja {@code Long}/{@code Double}/{@code Integer} y
 * una entrada de {@code HashMap} por nodo y por relajación. Medido con {@code NavSearchPerfTest} en el
 * mismo JVM y el mismo grafo, con hash de ruta idéntico: las búsquedas de ≥500 expansiones bajan ~60-77%
 * de tiempo; las de <100 expansiones no muestran cambio fuera del ruido.
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
        private final long start;
        private final int startFeet;
        private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f)
                .thenComparingDouble(Node::h));
        // A3/perf · Colecciones PRIMITIVAS (fastutil, ya presente en el runtime de Minecraft):
        // mismo orden de extracción y mismos resultados, sin cajas Long/Double/Integer por nodo.
        // Menos allocación = menos presión de GC en búsquedas largas. Ver NavSearchPerfTest.
        private final Long2DoubleOpenHashMap bestG = new Long2DoubleOpenHashMap();
        private final Long2LongOpenHashMap cameFrom = new Long2LongOpenHashMap();
        private final Long2ObjectOpenHashMap<NavRoute.Transition> transitionTo = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<BlockPos> actionTo = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<BlockPos> standTo = new Long2ObjectOpenHashMap<>();
        private final Long2IntOpenHashMap feetTo = new Long2IntOpenHashMap();
        private final LongOpenHashSet closed = new LongOpenHashSet();
        private long front;
        private double bestDist;
        private long found = Long.MIN_VALUE;
        private int expansions;
        private int totalPops;
        private int totalStalePops;
        private int totalClosedSkips;
        private int totalReopens;
        private int lastPops;
        private int lastStalePops;
        private int lastClosedSkips;
        private int lastReopens;
        private int lastExpansions;
        private boolean complete;
        private boolean hitBudget;
        private String reason = "pendiente";

        public Session(long start, int startFeet, Graph graph, int maxExpansions) {
            if (maxExpansions < 1) throw new IllegalArgumentException("maxExpansions must be positive");
            this.graph = graph;
            this.maxExpansions = maxExpansions;
            this.start = start;
            this.startFeet = startFeet;
            double h0 = graph.heuristic(start);
            bestG.defaultReturnValue(Double.NaN);
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
            int poppedThisSlice = 0;
            int staleThisSlice = 0;
            int closedSkipsThisSlice = 0;
            final int[] reopensThisSlice = {0};
            int maxPopsThisSlice = expansionSlice > Integer.MAX_VALUE / 2
                    ? Integer.MAX_VALUE : expansionSlice * 2;
            while (!open.isEmpty() && expandedThisSlice < expansionSlice
                    && poppedThisSlice < maxPopsThisSlice) {
                Node current = open.poll();
                poppedThisSlice++;
                totalPops++;
                // A better path may have inserted a replacement OPEN record for this state.
                // Never expand an older record using its obsolete g-score.
                // defaultReturnValue(NaN) evita el doble hash containsKey+get en el bucle caliente.
                double best = bestG.get(current.state());
                if (Double.isNaN(best) || Double.compare(current.g(), best) != 0) {
                    totalStalePops++;
                    staleThisSlice++;
                    continue;
                }
                if (closed.contains(current.state())) {
                    totalClosedSkips++;
                    closedSkipsThisSlice++;
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
                    double known = bestG.get(edge.to());
                    if (!Double.isNaN(known) && known <= tentative) return;
                    if (closed.remove(edge.to())) {
                        totalReopens++;
                        reopensThisSlice[0]++;
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
            lastPops = poppedThisSlice;
            lastStalePops = staleThisSlice;
            lastClosedSkips = closedSkipsThisSlice;
            lastReopens = reopensThisSlice[0];
            lastExpansions = expandedThisSlice;
            if (!complete && open.isEmpty()) {
                complete = true;
                reason = "frente alcanzable agotado";
            }
            return complete;
        }

        public boolean complete() { return complete; }
        public int expansions() { return expansions; }
        public int frontierSize() { return open.size(); }
        public int totalPops() { return totalPops; }
        public int totalStalePops() { return totalStalePops; }
        public int totalClosedSkips() { return totalClosedSkips; }
        public int totalReopens() { return totalReopens; }
        public int lastPops() { return lastPops; }
        public int lastStalePops() { return lastStalePops; }
        public int lastClosedSkips() { return lastClosedSkips; }
        public int lastReopens() { return lastReopens; }
        public int lastExpansions() { return lastExpansions; }

        public Result result() {
            if (!complete) throw new IllegalStateException("search is still pending");
            List<Step> route = new ArrayList<>();
            long cursor = found != Long.MIN_VALUE ? found : front;
            while (cursor != start) {
                if (!cameFrom.containsKey(cursor)) break;
                long prev = cameFrom.get(cursor);
                route.add(new Step(cursor, transitionTo.getOrDefault(cursor, NavRoute.Transition.WALK),
                        feetTo.getOrDefault(cursor, startFeet), actionTo.get(cursor), standTo.get(cursor)));
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
