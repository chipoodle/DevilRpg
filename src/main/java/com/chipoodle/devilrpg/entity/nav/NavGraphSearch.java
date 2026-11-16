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

    public static Result search(long start, int startFeet, Graph graph, int maxExpansions) {
        // Desempate determinista por h: en rejillas amplias hay enormes mesetas con el mismo f.
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f)
                .thenComparingDouble(Node::h));
        Map<Long, Double> bestG = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, NavRoute.Transition> transitionTo = new HashMap<>();
        Map<Long, BlockPos> actionTo = new HashMap<>();
        Map<Long, BlockPos> standTo = new HashMap<>();
        Map<Long, Integer> feetTo = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        double h0 = graph.heuristic(start);
        open.add(new Node(start, 0.0D, h0, h0));
        bestG.put(start, 0.0D);
        feetTo.put(start, startFeet);
        long front = start;
        double bestDist = graph.distance(start);
        int expansions = 0;
        boolean hitBudget = false;
        long found = Long.MIN_VALUE;
        while (!open.isEmpty()) {
            Node current = open.poll();
            if (!closed.add(current.state())) {
                continue;
            }
            if (expansions >= maxExpansions) {
                hitBudget = true;
                break;
            }
            expansions++;
            double d = graph.distance(current.state());
            if (d < bestDist) {
                bestDist = d;
                front = current.state();
            }
            int feet = feetTo.getOrDefault(current.state(), startFeet);
            if (graph.goal(current.state(), feet)) {
                found = current.state();
                break;
            }
            final long from = current.state();
            final int fromFeet = feet;
            graph.expand(from, fromFeet, edge -> {
                // A BREAK edge without both positions cannot be executed safely. Never fall back
                // to treating the solid target or a missing stand as a movement waypoint.
                if (edge.transition() == NavRoute.Transition.BREAK
                        && (edge.actionPos() == null || edge.standPos() == null)) {
                    return;
                }
                double tentative = current.g() + edge.cost();
                Double known = bestG.get(edge.to());
                if (known != null && known <= tentative) {
                    return;
                }
                // Igual que el A* anterior: un estado cerrado puede reabrirse si aparece un g menor.
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
        List<Step> route = new ArrayList<>();
        long cursor = found != Long.MIN_VALUE ? found : front;
        while (cursor != start) {
            Long prev = cameFrom.get(cursor);
            if (prev == null) {
                break;
            }
            route.add(new Step(cursor, transitionTo.getOrDefault(cursor, NavRoute.Transition.WALK),
                    feetTo.getOrDefault(cursor, startFeet), actionTo.get(cursor), standTo.get(cursor)));
            cursor = prev;
        }
        Collections.reverse(route);
        String reason = found != Long.MIN_VALUE ? "encontrado"
                : (hitBudget ? "presupuesto global agotado" : "frente alcanzable agotado");
        return new Result(found != Long.MIN_VALUE, route, front, expansions, hitBudget, reason);
    }
}
