package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A2 · <b>Planificador acotado propio</b> (A* sobre la rejilla, con transiciones de terreno explícitas).
 * <p>
 * Sustituye a {@code PathNavigation}/`WalkNodeEvaluator` como planificador PRINCIPAL de {@code AggressiveZombieEntity}.
 * Diferencias deliberadas frente al de vanilla, cada una atada a un fallo medido:
 * <ul>
 *   <li><b>Presupuesto duro</b> de expansiones por búsqueda: el cuelgue de 60 s del watchdog vino de gastar 9
 *       anillos × 6× nodos en un solo tick. Aquí nunca se pasa de {@code maxExpansions}.</li>
 *   <li><b>Costo por transición</b>: romper cuesta mucho más que caminar, así que el planificador solo rompe cuando
 *       no hay alternativa razonable — no convierte el túnel en la opción preferida.</li>
 *   <li><b>Rutas comparables</b> ({@link NavRoute#signature()}) para poder rechazar de verdad una ruta repetida.</li>
 *   <li><b>Descenso seguro</b>: una caída mayor que {@code maxSafeDrop} es intransitable, no un atajo.</li>
 * </ul>
 */
public final class NavPlanner {
    private static final boolean DIAG_DROP = Boolean.getBoolean("devilrpg.a2n.diagDrop");
    private static BlockPos DIAG_START;

    /** Coste base de caminar una celda. */
    private static final double COST_WALK = 1.0D;
    private static final double COST_STEP = 1.4D;
    private static final double COST_JUMP = 2.2D;
    private static final double COST_DROP = 1.8D;
    /** Romper es caro: solo cuando de verdad no hay paso. */
    private static final double COST_BREAK = 12.0D;
    private static final double COST_BRIDGE = 14.0D;
    /**
     * A2 Stage 4Q · Coste de CONSTRUIR un escalon. Caro a proposito: construir es la ultima opcion, nunca un atajo
     * preferido frente a caminar, saltar o rodear.
     */
    private static final double COST_BUILD = 16.0D;
    /** Penalización por cambio de dirección, para que las rutas no zigzagueen. */
    private static final double COST_TURN = 0.3D;

    /** Resultado de una planificación. */
    /**
     * Resultado de una planificacion, con resumen ACOTADO (una linea; nunca un log por nodo).
     * `clasif` desglosa los candidatos por categoria de obstruccion y `encolados` cuenta los aceptados.
     */
    public record Result(NavRoute route, int expansions, boolean hitBudget, String summary) {
    }

    // ================================================================================================
    // A2 Stage 7C · CAPA GLOBAL ACOTADA.
    // AUDITORIA (Stage 7C) de la busqueda LOCAL de arriba, dicha sin adornos: es una busqueda
    // best-first con estructura de A* — OPEN por `f`, `g` acumulado, conjunto cerrado, aristas
    // validadas y reconstruccion por `cameFrom` — pero NO es una A* CORRECTA:
    //   (a) la heuristica NO es admisible: cobra 1.2/bloque vertical cuando un salto de 2 bloques
    //       cuesta 2.2 (< 2.4), asi que puede sobreestimar y devolver rutas no optimas;
    //   (b) un nodo cerrado NO se reabre aunque luego se encuentre un `g` menor;
    //   (c) TERMINA al ENCOLAR la meta (`found = candidate`), no al extraerla, de modo que la ruta
    //       devuelta puede ser peor que la optima.
    // Nada de eso rompe la CORRECCION (toda ruta devuelta es ejecutable: cada arista paso la
    // validacion), pero invalida llamarla A*. Se deja INTACTA porque es la capa que ejecuta el
    // contrato local y ya esta medida. Para el tramo LARGO se anade abajo `planGlobal`, que SI es
    // una A* correcta sobre el MISMO grafo de movimientos validados (heuristica admisible,
    // reapertura de cerrados y terminacion al extraer la meta), acotada por radio y presupuesto.
    // ================================================================================================

    /** A2 Stage 7C · Tolerancia horizontal (al cuadrado) para dar por alcanzada la meta global. */
    private static final double RADIO_DE_LLEGADA_SQR = 2.25D;

    /**
     * A2 Stage 7C · Resultado de una planificacion GLOBAL.
     * {@code frente} es la celda de pie ALCANZADA mas cercana a la meta cuando no se llego a ella: es el
     * unico destino admisible para el siguiente tramo (no se elige por distancia, se elige porque se llego).
     */
    public record GlobalResult(NavRoute route, BlockPos frente, boolean alcanzoMision, int expansions,
                               boolean hitBudget, String reason, String summary) {
    }

    /** Motivo por el que una arista NO es ejecutable (para no perder los contadores del diagnostico). */
    private enum Rechazo {
        NINGUNO, CAIDA_INSEGURA, DESNIVEL, NODO_NO_PISABLE, CLASIFICACION
    }

    /** Una arista ya validada: coste, etiqueta y nivel de pies; o el motivo del rechazo. */
    private record Arista(NavObstruction.Kind kind, double coste, NavRoute.Transition transicion, int piesHasta,
                          Rechazo rechazo) {
        boolean valida() {
            return rechazo == Rechazo.NINGUNO;
        }
    }

    /**
     * A2 Stage 7C · VALIDACION UNICA DE ARISTA, compartida por la busqueda local y la global.
     * Es exactamente el bloque que estaba en linea dentro del bucle local (Stage 4Q/6D FIX 2): mismas
     * reglas, mismo orden y mismos motivos de rechazo. Así el grafo de la capa global es el mismo que
     * el de la capa local y no hay dos verdades sobre que es un movimiento legal.
     */
    private static Arista validarArista(CollisionGetter level, Mob mob, BlockPos from, BlockPos candidate,
                                        int piesDesde, NavObstruction.Policies policies,
                                        java.util.Set<BlockPos> construidas) {
        NavObstruction.Kind kind = NavObstruction.classify(level, mob, candidate, policies);
        boolean diagBorde = DIAG_DROP && DIAG_START != null && from.equals(DIAG_START);
        if (diagBorde) {
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info(
                    "[DROPACCEPT] EDGE from={} cand={} piesDesde={} kind={} fits={} support={} feet={} head={} below={}",
                    from.toShortString(), candidate.toShortString(), piesDesde, kind,
                    NavObstruction.fits(level, mob, candidate), NavObstruction.hasSupport(level, candidate),
                    level.getBlockState(candidate), level.getBlockState(candidate.above()),
                    level.getBlockState(candidate.below()));
        }
        double cost = costOf(kind);
        NavRoute.Transition transicion = transitionOf(kind);
        int piesHasta = piesResultantes(kind, candidate, level, policies);
        if (cost < 0 && construidas != null && construidas.contains(candidate)) {
            cost = COST_WALK;
            transicion = NavRoute.Transition.WALK;
            piesHasta = candidate.getY();
        } else if (cost < 0) {
            if (esConstruible(level, mob, candidate, policies)) {
                cost = COST_BUILD;
                transicion = NavRoute.Transition.BUILD;
                piesHasta = candidate.getY() + 1;
            }
        }
        if (cost > 0 && kind == NavObstruction.Kind.DROP) {
            int caida = NavObstruction.caidaReal(level, candidate, policies.maxSafeDrop() + 2);
            if (caida > policies.maxSafeDrop()) {
                if (esConstruible(level, mob, candidate, policies)) {
                    cost = COST_BUILD;
                    transicion = NavRoute.Transition.BUILD;
                    piesHasta = candidate.getY() + 1;
                } else {
                    if (NavTrace.on()) {
                        NavTrace.ev("DROP_UNSAFE", "candidate=" + candidate.toShortString()
                                + " caida=" + caida + " maxSafeDrop=" + policies.maxSafeDrop()
                                + " constructible=false");
                    }
                    return new Arista(kind, -1.0D, transicion, PIES_NO_EJECUTABLE, Rechazo.CAIDA_INSEGURA);
                }
            }
        }
        if (cost < 0) {
            return new Arista(kind, -1.0D, transicion, PIES_NO_EJECUTABLE, Rechazo.CLASIFICACION);
        }
        if (piesHasta == PIES_NO_EJECUTABLE
                || piesHasta - piesDesde > SUBIDA_MAXIMA
                || piesDesde - piesHasta > policies.maxSafeDrop()) {
            return new Arista(kind, cost, transicion, piesHasta, Rechazo.DESNIVEL);
        }
        if (kind == NavObstruction.Kind.DROP) {
            BlockPos aterrizaje = new BlockPos(candidate.getX(), piesHasta, candidate.getZ());
            if (!NavObstruction.fits(level, mob, aterrizaje) || !NavObstruction.hasSupport(level, aterrizaje)) {
                return new Arista(kind, cost, transicion, piesHasta, Rechazo.DESNIVEL);
            }
        }
        if (transicion != NavRoute.Transition.BUILD) {
            transicion = etiquetaDeMovimiento(kind, piesHasta - piesDesde);
            cost = costeDeTransicion(transicion);
        }
        // A2 REMEDIACIÓN 3 · NO CORTAR LA ESQUINA DEL MURO.
        // Medido en a2n-20261019-203508: el primer nodo local de idx8 era 690,70,604 desde 691,70,605;
        // ambos extremos son aire, pero las dos celdas laterales de la diagonal (691,604 y 690,605) son
        // del anillo del muro (radio 62) y estan bloqueadas a la altura del cuerpo. El planificador solo
        // miraba el destino, aceptaba el WALK diagonal y el movimiento vanilla no podia empezar; la
        // recuperacion chocaba con la guarda de ruta y terminaba en FAILED. Se rechaza la diagonal solo
        // cuando AMBOS laterales estan bloqueados: no se prohibe una diagonal valida junto a una pared.
        if (from != null && transicion != NavRoute.Transition.BREAK
                && transicion != NavRoute.Transition.BRIDGE
                && from.getX() != candidate.getX() && from.getZ() != candidate.getZ()) {
            BlockPos ladoX = new BlockPos(candidate.getX(), piesDesde, from.getZ());
            BlockPos ladoZ = new BlockPos(from.getX(), piesDesde, candidate.getZ());
            if (!NavObstruction.fits(level, mob, ladoX) && !NavObstruction.fits(level, mob, ladoZ)) {
                return new Arista(kind, cost, transicion, piesHasta, Rechazo.NODO_NO_PISABLE);
            }
        }
        if (kind == NavObstruction.Kind.BREAKABLE && !NavObstruction.hasSupport(level, candidate)) {
            return new Arista(kind, cost, transicion, piesHasta, Rechazo.NODO_NO_PISABLE);
        }
        boolean escalada = kind == NavObstruction.Kind.STEP_UP || kind == NavObstruction.Kind.JUMP_UP;
        if (escalada) {
            BlockPos pies = new BlockPos(candidate.getX(), piesHasta, candidate.getZ());
            if (!NavClimbPolicy.escaladaValida(NavObstruction.fits(level, mob, pies), NavObstruction.hasSupport(level, pies))) {
                return new Arista(kind, cost, transicion, piesHasta, Rechazo.NODO_NO_PISABLE);
            }
        }
        boolean nodoEsPosicionDePie = piesHasta == candidate.getY() || escalada;
        if (!nodoEsPosicionDePie && kind != NavObstruction.Kind.DROP
                && transicion != NavRoute.Transition.BUILD) {
            return new Arista(kind, cost, transicion, piesHasta, Rechazo.NODO_NO_PISABLE);
        }
        if (diagBorde) {
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info(
                    "[DROPACCEPT] EDGE_OK from={} cand={} kind={} trans={} piesHasta={} fits={} support={}",
                    from.toShortString(), candidate.toShortString(), kind, transicion, piesHasta,
                    NavObstruction.fits(level, mob, candidate), NavObstruction.hasSupport(level, candidate));
        }
        return new Arista(kind, cost, transicion, piesHasta, Rechazo.NINGUNO);
    }

    /**
     * A2 Stage 7C · PLANIFICACION GLOBAL ACOTADA: A* CORRECTA sobre el grafo de movimientos validados.
     * <p>
     * Diferencias deliberadas con {@link #plan}: (1) heuristica ADMISIBLE (solo distancia horizontal, que
     * es cota inferior porque ninguna arista cuesta menos que su desplazamiento horizontal); (2) un nodo
     * cerrado SE REABRE si aparece un `g` menor; (3) TERMINA al EXTRAER la meta, no al encolarla; (4)
     * acotada por radio horizontal y por presupuesto de expansiones, y si no llega devuelve el FRENTE
     * alcanzado mas cercano a la meta (nunca una celda elegida por distancia sin camino).
     * <p>
     * La usa {@code NavExecutor} para elegir el tramo siguiente cuando la mision esta fuera de la ventana
     * local: el objetivo local pasa a ser un waypoint DE LA RUTA GLOBAL, no una celda "mas cerca".
     */
    public static GlobalResult planGlobal(CollisionGetter level, Mob mob, BlockPos start, BlockPos objetivo,
                                          int maxRadio, int verticalRange, int maxExpansions,
                                          NavObstruction.Policies policies,
                                          java.util.Set<BlockPos> construidas) {
        return planGlobal(level, mob, start, objetivo, maxRadio, verticalRange, maxExpansions,
                policies, construidas, null);
    }

    public static GlobalResult planGlobal(CollisionGetter level, Mob mob, BlockPos start, BlockPos objetivo,
                                          int maxRadio, int verticalRange, int maxExpansions,
                                          NavObstruction.Policies policies,
                                          java.util.Set<BlockPos> construidas,
                                          NavDropExclusionPolicy exclusiones) {
        if (start.equals(objetivo)) {
            return new GlobalResult(new NavRoute(List.of(new NavRoute.Node(objetivo, NavRoute.Transition.WALK)),
                    objetivo), objetivo, true, 0, false, "inicio==fin",
                    "global inicio==fin " + objetivo.toShortString());
        }
        final double ox = objetivo.getX() + 0.5D;
        final double oy = objetivo.getY() + 0.5D;
        final double oz = objetivo.getZ() + 0.5D;
        final int[] contadores = new int[2]; // aristas, encolados
        NavGraphSearch.Graph graph = new NavGraphSearch.Graph() {
            @Override
            public void expand(long state, int feet, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                BlockPos pos = BlockPos.of(state);
                for (int[] off : OFFSETS) {
                    BlockPos next = new BlockPos(pos.getX() + off[0], pos.getY(), pos.getZ() + off[1]);
                    for (int dy = -verticalRange; dy <= verticalRange; dy++) {
                        BlockPos candidate = next.offset(0, dy, 0);
                        if (Math.abs(candidate.getX() - start.getX()) > maxRadio
                                || Math.abs(candidate.getZ() - start.getZ()) > maxRadio) {
                            continue;
                        }
                        Arista arista = validarArista(level, mob, pos, candidate, feet, policies, construidas);
                        contadores[0]++;
                        if (!arista.valida()) {
                            continue;
                        }
                        if (exclusiones != null && arista.transicion() == NavRoute.Transition.DROP) {
                            BlockPos landing = sucesorCanonico(candidate, arista.kind(), arista.transicion(), arista.piesHasta());
                            long clave = NavDropExclusionPolicy.clave(pos.getX(), pos.getY(), pos.getZ(),
                                    landing.getX(), landing.getY(), landing.getZ());
                            long huella = huellaTerreno(level, landing);
                            if (exclusiones.excluida(clave, huella)) {
                                continue;
                            }
                        }
                        double cost = arista.coste();
                        if (off[0] != 0 && off[1] != 0) {
                            cost *= 1.4142D;
                        }
                        BlockPos estado = sucesorCanonico(candidate, arista.kind(), arista.transicion(), arista.piesHasta());
                        contadores[1]++;
                        out.accept(new NavGraphSearch.Edge(estado.asLong(), cost,
                                arista.transicion(), arista.piesHasta()));
                    }
                }
            }

            @Override
            public boolean goal(long state, int feet) {
                return esMetaGlobal(BlockPos.of(state), feet, ox, oy, oz);
            }

            @Override
            public double heuristic(long state) {
                return heuristicaGlobal(BlockPos.of(state), objetivo);
            }

            @Override
            public double distance(long state) {
                return horizontalAlCuadrado(BlockPos.of(state), objetivo);
            }
        };
        NavGraphSearch.Result resultado = NavGraphSearch.search(start.asLong(), start.getY(), graph, maxExpansions);
        int expansions = resultado.expansions();
        boolean hitBudget = resultado.hitBudget();
        BlockPos found = resultado.found() ? BlockPos.of(resultado.route().get(resultado.route().size() - 1).state()) : null;
        BlockPos frente = BlockPos.of(resultado.front());
        double mejorDistFrente = horizontalAlCuadrado(frente, objetivo);
        StringBuilder resumen = new StringBuilder();
        resumen.append("global inicio=").append(start.toShortString())
                .append(" meta=").append(objetivo.toShortString())
                .append(" radio=").append(maxRadio).append(" V=").append(verticalRange)
                .append(" exp=").append(expansions).append("/").append(maxExpansions)
                .append(" presupuesto=").append(hitBudget)
                .append(" aristas=").append(contadores[0]).append(" encolados=").append(contadores[1]);
        if (!resultado.found()) {
            String motivo = hitBudget ? "presupuesto global agotado"
                    : "el frente alcanzable se agoto dentro del radio " + maxRadio;
            resumen.append(" frente=").append(frente.toShortString())
                    .append(" dFrente=").append(String.format(java.util.Locale.ROOT, "%.1f",
                            Math.sqrt(mejorDistFrente)))
                    .append(" motivo=").append(motivo);
            List<NavRoute.Node> nodosFrente = new ArrayList<>();
            for (NavGraphSearch.Step paso : resultado.route()) {
                nodosFrente.add(new NavRoute.Node(BlockPos.of(paso.state()), paso.transition()));
            }
            resumen.append(" rutaFrente=").append(nodosFrente.size());
            return new GlobalResult(new NavRoute(nodosFrente, frente), frente, false, expansions, hitBudget,
                    motivo, resumen.toString());
        }
        List<NavRoute.Node> nodes = new ArrayList<>();
        for (NavGraphSearch.Step paso : resultado.route()) {
            nodes.add(new NavRoute.Node(BlockPos.of(paso.state()), paso.transition()));
        }
        resumen.append(" ruta=").append(nodes.size());
        return new GlobalResult(new NavRoute(nodes, objetivo), objetivo, true, expansions, hitBudget,
                "meta alcanzada", resumen.toString());
    }

    /** A2 Stage 7C · heuristica ADMISIBLE: distancia horizontal (ninguna arista cuesta menos que eso). */
    private static double heuristicaGlobal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalAlCuadrado(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** A2 Stage 7C · ¿esta celda de pie alcanza la meta global (misma altura y a menos de 1.5 bloques)? */
    private static boolean esMetaGlobal(BlockPos pos, int pies, double ox, double oy, double oz) {
        double dx = (pos.getX() + 0.5D) - ox;
        double dz = (pos.getZ() + 0.5D) - oz;
        double dy = (pies + 0.5D) - oy;
        return dx * dx + dz * dz <= RADIO_DE_LLEGADA_SQR && Math.abs(dy) <= 1.0D;
    }

    private static final int[][] OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /**
     * A2 Stage 6D · FIX 2 · SUBIDA MAXIMA de una arista (1 = escalon, 2 = salto). Es la capacidad real del ente:
     * no se admite un desplazamiento vertical mayor por el hecho de que los dos extremos sean transitables.
     */
    private static final int SUBIDA_MAXIMA = 2;

    /** Valle de "no es un movimiento ejecutable" para {@link #piesResultantes}. */
    private static final int PIES_NO_EJECUTABLE = Integer.MIN_VALUE;

    /**
     * A2 Stage 6D · FIX 2 · NIVEL DE PIES que resulta de ejecutar la transicion hacia {@code cell}.
     * <p>
     * Medido en `a2n-20261016-142948`: la ruta aceptada contenia la arista `JUMP_UP@129,85,-8 -> WALK@130,81,-8`,
     * es decir un desplazamiento VERTICAL de 4 bloques con etiqueta `WALK`, solo porque los dos extremos se
     * clasificaban por separado como transitables. La clasificacion de una celda NO es la validez de una arista:
     * esta funcion da la magnitud que sí lo decide.
     */
    private static long huellaTerreno(CollisionGetter level, BlockPos p) {
        long h = level.getBlockState(p).hashCode();
        h = h * 31L + level.getBlockState(p.above()).hashCode();
        h = h * 31L + level.getBlockState(p.below()).hashCode();
        return h;
    }

    static BlockPos estadoSucesor(BlockPos candidate, NavRoute.Transition transicion, int piesHasta) {
        return (transicion == NavRoute.Transition.STEP_UP || transicion == NavRoute.Transition.JUMP_UP)
                ? new BlockPos(candidate.getX(), piesHasta, candidate.getZ()) : candidate;
    }

    /**
     * Sucesor canonico de una arista: si la clasificacion es de escalada, el estado es la celda de pie
     * validada (piesHasta), aunque la etiqueta se haya normalizado a WALK por un delta 0.
     */
    static BlockPos sucesorCanonico(BlockPos candidate, NavObstruction.Kind kind,
                                    NavRoute.Transition transicion, int piesHasta) {
        boolean escalada = kind == NavObstruction.Kind.STEP_UP || kind == NavObstruction.Kind.JUMP_UP;
        int[] s = NavSuccessorPolicy.sucesor(candidate.getX(), candidate.getY(), candidate.getZ(),
                escalada, piesHasta);
        return new BlockPos(s[0], s[1], s[2]);
    }

    private static int piesResultantes(NavObstruction.Kind kind, BlockPos cell, CollisionGetter level,
                                       NavObstruction.Policies policies) {
        return switch (kind) {
            // La celda es la posicion de pie: se camina (o se rompe) hasta quedar en ella.
            case PASSABLE, BREAKABLE -> cell.getY();
            // Celda sin suelo: se cae hasta el primer suelo firme por debajo.
            case DROP -> cell.getY() - NavObstruction.caidaReal(level, cell, policies.maxSafeDrop() + 2);
            // Celda bloqueada a la altura de los pies con hueco encima: se sube 1.
            case STEP_UP -> cell.getY() + 1;
            // Bloque de dos de alto con hueco encima: se salta 2.
            case JUMP_UP -> cell.getY() + 2;
            // Intocable o infranqueable: no es un movimiento.
            case PROTECTED, BLOCKED -> PIES_NO_EJECUTABLE;
        };
    }

    private NavPlanner() {
    }

    private record Node(BlockPos pos, double g, double f, double dirX, double dirZ) {
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        double dy = (a.getY() - b.getY()) * 1.2D;
        return Math.sqrt(dx * dx + dz * dz) + Math.abs(dy);
    }

    /**
     * Busca una ruta de {@code start} a {@code goal}.
     *
     * @param maxExpansions presupuesto DURO de expansiones; al agotarlo se devuelve lo mejor encontrado (o vacío),
     *                      nunca se sigue buscando.
     */
    public static Result plan(CollisionGetter level, Mob mob, BlockPos start, BlockPos goal,
                              int horizontalRange, int verticalRange, int maxExpansions,
                              NavObstruction.Policies policies) {
        return plan(level, mob, start, goal, horizontalRange, verticalRange, maxExpansions, policies, null);
    }

    /**
     * A2 Stage 4Q · Igual que {@link #plan} pero con la <b>geometria construida</b>: las celdas de
     * {@code construidas} se consideran pisables aunque el mundo aun no las tenga, de modo que un escalon
     * construido pasa a formar parte de la ruta aceptada en vez de ser un efecto secundario ciego.
     */
    public static Result plan(CollisionGetter level, Mob mob, BlockPos start, BlockPos goal,
                              int horizontalRange, int verticalRange, int maxExpansions,
                              NavObstruction.Policies policies,
                              java.util.Set<BlockPos> construidas) {
        DIAG_START = DIAG_DROP ? start.immutable() : null;
        if (start.equals(goal)) {
            return new Result(new NavRoute(List.of(new NavRoute.Node(goal, NavRoute.Transition.WALK)), goal), 0, false,
                    "inicio==fin: ya se esta en el destino");
        }
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<BlockPos, Double> bestG = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, NavRoute.Transition> transitionTo = new HashMap<>();
        /** A2 Stage 6D · FIX 2 · nivel de PIES con el que se llega a cada nodo (no siempre es `pos.getY()`). */
        Map<BlockPos, Integer> piesDe = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        open.add(new Node(start, 0.0D, heuristic(start, goal), 0.0D, 0.0D));
        bestG.put(start, 0.0D);
        piesDe.put(start, start.getY());

        int expansions = 0;
        boolean hitBudget = false;
        BlockPos found = null;
        // A2 diagnostico: desglose por categoria de obstruccion y candidatos realmente encolados.
        int[] porCategoria = new int[NavObstruction.Kind.values().length];
        int evaluados = 0;
        /** A2 Stage 4V · aristas rechazadas por superar la caida segura y no ser construibles. */
        final int[] porCategoriaRechazadasPorCaida = {0};
        /** A2 Stage 6D · FIX 2 · aristas rechazadas porque el desplazamiento vertical real no es ejecutable. */
        int aristasRechazadasPorDesnivel = 0;
        /** A2 Stage 6D · FIX 2 · aristas rechazadas porque su nodo NO es una posicion de pie real. */
        int aristasRechazadasPorNodoNoPisable = 0;
        int encolados = 0;
        boolean metaEncolada = false;

        while (!open.isEmpty()) {
            Node current = open.poll();
            BlockPos pos = current.pos();
            if (!closed.add(pos)) {
                continue;
            }
            if (++expansions > maxExpansions) {
                hitBudget = true;
                break;
            }
            if (pos.equals(goal)) {
                found = pos;
                break;
            }
            for (int[] off : OFFSETS) {
                BlockPos next = new BlockPos(pos.getX() + off[0], pos.getY(), pos.getZ() + off[1]);
                // Y se resuelve probando niveles cercanos: el planificador decide la altura, no la asume.
                for (int dy = -verticalRange; dy <= verticalRange; dy++) {
                    BlockPos candidate = next.offset(0, dy, 0);
                    if (Math.abs(candidate.getX() - start.getX()) > horizontalRange
                            || Math.abs(candidate.getZ() - start.getZ()) > horizontalRange) {
                        continue;
                    }
                    // A2 Stage 7C · la validacion de arista vive en UN solo sitio (`validarArista`) y la comparten
                    // la busqueda local y la global: mismo grafo, mismas reglas, mismos contadores.
                    Arista arista = validarArista(level, mob, pos, candidate,
                            piesDe.getOrDefault(pos, pos.getY()), policies, construidas);
                    NavObstruction.Kind kind = arista.kind();
                    evaluados++;
                    porCategoria[kind.ordinal()]++;
                    switch (arista.rechazo()) {
                        case CAIDA_INSEGURA -> porCategoriaRechazadasPorCaida[0]++;
                        case DESNIVEL -> aristasRechazadasPorDesnivel++;
                        case NODO_NO_PISABLE -> aristasRechazadasPorNodoNoPisable++;
                        default -> { }
                    }
                    if (!arista.valida()) {
                        continue;
                    }
                    double cost = arista.coste();
                    NavRoute.Transition transicion = arista.transicion();
                    int piesHasta = arista.piesHasta();
                    if (off[0] != 0 && off[1] != 0) {
                        cost *= 1.4142D; // diagonal
                    }
                    if (current.dirX() != 0.0D || current.dirZ() != 0.0D) {
                        boolean sameDir = Math.signum(off[0]) == Math.signum(current.dirX())
                                && Math.signum(off[1]) == Math.signum(current.dirZ());
                        if (!sameDir) {
                            cost += COST_TURN;
                        }
                    }
                    double tentative = current.g() + cost;
                    BlockPos sucesor = sucesorCanonico(candidate, arista.kind(), transicion, piesHasta);
                    Double known = bestG.get(sucesor);
                    if (known != null && known <= tentative) {
                        continue;
                    }
                    bestG.put(sucesor, tentative);
                    // El estado es la celda de pie validada, no el candidato crudo.
                    piesDe.put(sucesor, piesHasta);
                    encolados++;
                    if (sucesor.equals(goal)) {
                        metaEncolada = true;
                    }
                    cameFrom.put(sucesor, pos);
                    transitionTo.put(sucesor, transicion);
                    open.add(new Node(sucesor, tentative, tentative + heuristic(sucesor, goal),
                            off[0], off[1]));
                    if (sucesor.equals(goal)) {
                        found = sucesor;
                    }
                }
            }
            if (found != null && found.equals(goal) && closed.contains(pos)) {
                break;
            }
        }

        // Resumen ACOTADO: una sola linea por plan. Nunca se registra nada por nodo.
        StringBuilder resumen = new StringBuilder();
        resumen.append("inicio=").append(start.toShortString())
                .append(" fin=").append(goal.toShortString())
                .append(" H=").append(horizontalRange).append(" V=").append(verticalRange)
                .append(" exp=").append(expansions).append("/").append(maxExpansions)
                .append(" presupuesto=").append(hitBudget)
                .append(" evaluados=").append(evaluados).append(" encolados=").append(encolados)
                .append(" metaEncolada=").append(metaEncolada).append(" alcanzada=").append(found != null)
                .append(" rechazadasPorCaida=").append(porCategoriaRechazadasPorCaida[0])
                .append(" rechazadasPorDesnivel=").append(aristasRechazadasPorDesnivel)
                .append(" rechazadasPorNodoNoPisable=").append(aristasRechazadasPorNodoNoPisable);
        for (NavObstruction.Kind k : NavObstruction.Kind.values()) {
            if (porCategoria[k.ordinal()] > 0) {
                resumen.append(' ').append(k.name()).append('=').append(porCategoria[k.ordinal()]);
            }
        }
        if (found == null) {
            return new Result(new NavRoute(List.of(), goal), expansions, hitBudget, resumen.toString());
        }
        // Reconstrucción hacia atrás.
        List<NavRoute.Node> nodes = new ArrayList<>();
        BlockPos cursor = found;
        while (cursor != null && !cursor.equals(start)) {
            NavRoute.Transition t = transitionTo.getOrDefault(cursor, NavRoute.Transition.WALK);
            nodes.add(new NavRoute.Node(cursor, t));
            cursor = cameFrom.get(cursor);
        }
        java.util.Collections.reverse(nodes);
        resumen.append(" ruta=").append(nodes.size());
        return new Result(new NavRoute(nodes, goal), expansions, hitBudget, resumen.toString());
    }

    /**
     * A2 Stage 4Q · ¿Se puede CONSTRUIR un escalon en esta celda? Espeja las precondiciones de la primitiva de
     * colocacion de la casa (`ponerEscalon`): celda vacia, dos huecos encima, bloque con colision debajo, y fuera
     * de la aldea. La proteccion NO se debilita: si la politica la protege, no es construible.
     */
    private static boolean esConstruible(CollisionGetter level, Mob mob, BlockPos pos,
                                         NavObstruction.Policies policies) {
        if (policies.isProtected(pos) || !policies.canPlace(pos)) {
            return false;
        }
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                || !level.getBlockState(pos.above(2)).isAir()) {
            return false;
        }
        net.minecraft.world.level.block.state.BlockState debajo = level.getBlockState(pos.below());
        return !debajo.isAir() && !debajo.getCollisionShape(level, pos.below()).isEmpty();
    }

    /**
     * A2 Stage 6D · FIX 2 · Etiqueta COHERENTE con el desplazamiento vertical REAL de la arista.
     * Del nivel de pies resultante sale la accion fisica: bajar es {@code DROP}, +1 es {@code STEP_UP}, +2 es
     * {@code JUMP_UP} y 0 es {@code WALK}. Sin esto, una caida de 3 bloques podia viajar como `WALK`.
     */
    private static NavRoute.Transition etiquetaDeMovimiento(NavObstruction.Kind kind, int delta) {
        if (kind == NavObstruction.Kind.BREAKABLE) {
            return NavRoute.Transition.BREAK;
        }
        if (kind == NavObstruction.Kind.DROP) {
            return NavRoute.Transition.DROP;
        }
        if (delta < 0) {
            return NavRoute.Transition.DROP;
        }
        if (delta == 1) {
            return NavRoute.Transition.STEP_UP;
        }
        if (delta >= 2) {
            return NavRoute.Transition.JUMP_UP;
        }
        return NavRoute.Transition.WALK;
    }

    /** A2 Stage 6D · FIX 2 · Coste de la accion EFECTIVA (coherente con la etiqueta ya corregida). */
    private static double costeDeTransicion(NavRoute.Transition t) {
        return switch (t) {
            case DROP -> COST_DROP;
            case STEP_UP -> COST_STEP;
            case JUMP_UP -> COST_JUMP;
            case BREAK -> COST_BREAK;
            case BRIDGE -> COST_BRIDGE;
            case BUILD -> COST_BUILD;
            default -> COST_WALK; // WALK, DOOR
        };
    }

    /** Coste de una transición, o negativo si es intransitable. */
    private static double costOf(NavObstruction.Kind kind) {
        return switch (kind) {
            case PASSABLE -> COST_WALK;
            case STEP_UP -> COST_STEP;
            case JUMP_UP -> COST_JUMP;
            case DROP -> COST_DROP;
            case BREAKABLE -> COST_BREAK;
            case PROTECTED, BLOCKED -> -1.0D;
        };
    }

    private static NavRoute.Transition transitionOf(NavObstruction.Kind kind) {
        return switch (kind) {
            case PASSABLE -> NavRoute.Transition.WALK;
            case STEP_UP -> NavRoute.Transition.STEP_UP;
            case JUMP_UP -> NavRoute.Transition.JUMP_UP;
            case DROP -> NavRoute.Transition.DROP;
            case BREAKABLE -> NavRoute.Transition.BREAK;
            case PROTECTED, BLOCKED -> NavRoute.Transition.WALK;
        };
    }
}
