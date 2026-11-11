package com.chipoodle.devilrpg.entity.goal;

import java.util.ArrayList;
import java.util.List;

/**
 * Planificador de descubrimiento forestal <b>incremental y adaptativo</b>.
 *
 * <p>No conoce coordenadas de aldea, puertas, arboleda ni un rectángulo fijo. Recorre columnas del mundo en anillos
 * cuadrados que crecen desde el origen real del trabajador, a resolución de <b>un bloque</b>. Cada llamada consume como
 * máximo {@code budget} columnas, de modo que el trabajo por tick queda acotado. El cursor persiste entre llamadas: el
 * área explorada se amplía, no se reinicia, y un árbol en una columna que una rejilla de paso dos habría saltado sí se
 * visita.
 *
 * <p>La validación del árbol (tronco, hojas, suelo, protección, casilla de pie) NO vive aquí: la hace el goal con las
 * reglas de producción. Esta clase solo decide <b>qué columnas mirar</b> y en qué orden.
 */
public final class ForestryWorkPlanner {

    /** Una columna candidata del mundo. */
    public record Column(int x, int z) {
    }

    private int originX;
    private int originZ;
    private int ring;
    private int cursor;
    private boolean finished;

    /** Fija el origen del área de trabajo (normalmente la posición real del trabajador). */
    public void reset(int originX, int originZ) {
        this.originX = originX;
        this.originZ = originZ;
        this.ring = 0;
        this.cursor = 0;
        this.finished = false;
    }

    /** Restaura un cursor ya validado (sin confiar en caminos). */
    public void restore(int originX, int originZ, int ring, int cursor, boolean finished) {
        this.originX = originX;
        this.originZ = originZ;
        this.ring = Math.max(0, ring);
        this.cursor = Math.max(0, cursor);
        this.finished = finished;
    }

    public boolean isFinished() {
        return finished;
    }

    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public int ring() { return ring; }
    public int cursor() { return cursor; }

    /**
     * Devuelve el siguiente lote acotado de columnas. El anillo 0 es el origen; después cada anillo añade el perímetro
     * cuadrado completo, sin huecos de paso.
     *
     * @param budget columnas máximas a devolver en esta llamada
     * @param maxRing anillo máximo (en bloques) que tiene sentido explorar
     */
    public List<Column> nextColumns(final int budget, final int maxRing) {
        if (budget <= 0 || maxRing < 0) {
            return List.of();
        }
        List<Column> out = new ArrayList<>(Math.min(budget, 256));
        while (out.size() < budget && !finished) {
            if (ring > maxRing) {
                finished = true;
                break;
            }
            int perimeter = ring == 0 ? 1 : ring * 8;
            int index = cursor;
            int x;
            int z;
            if (ring == 0) {
                x = 0;
                z = 0;
            } else if (index < ring * 2 + 1) {
                // fila superior, incluidas ambas esquinas
                x = -ring + index;
                z = -ring;
            } else if (index < ring * 4) {
                // lado derecho, sin esquinas
                x = ring;
                z = -ring + 1 + (index - (ring * 2 + 1));
            } else if (index < ring * 6 + 1) {
                // fila inferior, incluidas ambas esquinas
                x = ring - (index - ring * 4);
                z = ring;
            } else {
                // lado izquierdo, sin esquinas
                x = -ring;
                z = ring - 1 - (index - (ring * 6 + 1));
            }
            out.add(new Column(originX + x, originZ + z));
            cursor++;
            if (cursor >= perimeter) {
                ring++;
                cursor = 0;
            }
        }
        return out;
    }
}
