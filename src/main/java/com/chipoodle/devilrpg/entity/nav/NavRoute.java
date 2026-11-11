package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A2 · <b>Modelo de ruta propio</b>: una secuencia ordenada de nodos con la transición EXPLÍCITA que hay que
 * ejecutar para pasar de uno al siguiente.
 * <p>
 * Motivo medido: con {@code PathNavigation} el fallo de los rezagados era que el planificador volvía a proponer el
 * MISMO punto ya rechazado ({@code plan=local-escape}, {@code waypoint=602,103,572}, {@code rejected=4},
 * {@code destinationStalled} 18→28→38, sin salir del encierro). Una ruta que nombra su transición permite además
 * <b>comparar rutas</b> y descartar la que repite la acción que ya falló, cosa que una {@code Path} opaca no deja.
 */
public final class NavRoute {

    /** Transición de terreno entre dos nodos consecutivos. */
    public enum Transition {
        /** Caminar en llano. */
        WALK,
        /** Subir un bloque. */
        STEP_UP,
        /** Saltar dos bloques. */
        JUMP_UP,
        /** Bajar (con control de altura segura). */
        DROP,
        /** Cruzar una puerta. */
        DOOR,
        /** Romper el bloque que estorba. */
        BREAK,
        /** Colocar un bloque de apoyo para poder pisar (puente sobre un hueco). */
        BRIDGE,
        /**
         * A2 Stage 4Q · CONSTRUIR un escalon: el nodo es la celda donde se COLOCA el bloque, y la posicion
         * transitable resultante es su celda SUPERIOR. Es una transicion de primera clase del modelo, no un efecto
         * secundario: entra en la ruta aceptada y se ejecuta desde la ruta.
         */
        BUILD
    }

    /** Un paso de la ruta: dónde y cómo se llega. */
    public record Node(BlockPos pos, Transition transition) {
    }

    private final List<Node> nodes;
    private final BlockPos destination;
    /** Identidad estructural: permite detectar que una ruta "nueva" es la misma que ya falló. */
    private final int signature;

    public NavRoute(List<Node> nodes, BlockPos destination) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.destination = destination.immutable();
        this.signature = buildSignature(this.nodes);
    }

    private static int buildSignature(List<Node> nodes) {
        int h = 1;
        for (Node n : nodes) {
            h = 31 * h + n.pos().hashCode();
            h = 31 * h + n.transition().ordinal();
        }
        return h;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public int length() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public BlockPos destination() {
        return destination;
    }

    public int signature() {
        return signature;
    }

    /** Nodo hacia el que hay que moverse ahora mismo (el primero sin alcanzar), o {@code null}. */
    public Node currentNode(int index) {
        return index >= 0 && index < nodes.size() ? nodes.get(index) : null;
    }

    /** Resumen corto para telemetría. */
    public String describe() {
        if (nodes.isEmpty()) {
            return "vacia";
        }
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, nodes.size() - 4);
        for (int i = from; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(n.transition()).append('@').append(n.pos().toShortString());
        }
        return "len=" + nodes.size() + " fin=" + destination.toShortString() + " ultimos=[" + sb + "]";
    }
}
