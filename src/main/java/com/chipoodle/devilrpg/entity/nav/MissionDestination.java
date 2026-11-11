package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · <b>Geometria pura del destino de mision</b>.
 *
 * <p>El destino final de una mision de asedio TIENE que estar <b>dentro</b> del recinto de la aldea, no en el
 * anillo exterior. Este helper no conoce Minecraft: recibe el centro, la cota, el radio real del muro y un
 * predicado de "celda pisable". Asi la busqueda se puede probar con un fixture puro (sin servidor) y la
 * produccion le inyecta el predicado autoritativo ({@code dentroDelRecinto} + suelo/holgura reales).
 *
 * <p>Reglas de la busqueda, todas acotadas:
 * <ul>
 *   <li>Se empieza en el centro y se abren anillos concentricos hasta {@code radioMuro - margen}.</li>
 *   <li>Solo se aceptan celdas <b>estrictamente interiores</b> ({@code dx^2 + dz^2 <= (radioMuro - margen)^2}):
 *       el anillo del muro nunca puede ser el destino.</li>
 *   <li>La altura se prueba empezando por la cota (suelo de la plaza), luego cota+1 (encima de la plaza), luego
 *       hacia abajo/arriba dentro de la banda del recinto.</li>
 *   <li>La busqueda termina en la primera celda que el predicado acepte; si ninguna vale, devuelve {@code null}
 *       (nunca inventa una coordenada).</li>
 * </ul>
 */
public final class MissionDestination {

    /** Celda candidata, independiente de las clases de Minecraft. */
    public record Cell(int x, int y, int z) {
        public double horizontalDistanceSqrTo(final int cx, final int cz) {
            double dx = x - cx;
            double dz = z - cz;
            return dx * dx + dz * dz;
        }
    }

    /** Predicado de celda pisable. En produccion incluye mundo, proteccion de ruptura y holgura reales. */
    @FunctionalInterface
    public interface CellTest {
        boolean test(int x, int y, int z);
    }

    private MissionDestination() {
    }

    /**
     * Busca el destino interior mas cercano al centro que cumpla {@code test}.
     *
     * @param cx       X del centro autoritativo de la aldea
     * @param cz       Z del centro autoritativo de la aldea
     * @param cota     cota de la plaza (nivel al que se anda)
     * @param radioMuro radio real del perimetro de la aldea
     * @param margen   margen interior minimo respecto del muro (p. ej. 4)
     * @param abajo    bloques que se admiten por debajo de la cota (banda del recinto)
     * @param arriba   bloques que se admiten por encima de la cota (banda del recinto)
     */
    public static Cell findInterior(final int cx, final int cz, final int cota, final int radioMuro,
                                    final int margen, final int abajo, final int arriba,
                                    final CellTest test) {
        if (test == null || radioMuro <= 0) {
            return null;
        }
        final int radioMision = Math.max(0, radioMuro - Math.max(1, margen));
        final int limite = radioMision * radioMision;
        final int[] alturas = ordenDeAlturas(abajo, arriba);
        for (int r = 0; r <= radioMision; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    // Solo el borde del cuadrado de radio r: los anillos anteriores ya se recorrieron.
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    if (dx * dx + dz * dz > limite) {
                        continue;
                    }
                    for (int dy : alturas) {
                        int x = cx + dx;
                        int y = cota + dy;
                        int z = cz + dz;
                        if (test.test(x, y, z)) {
                            return new Cell(x, y, z);
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Orden de alturas: primero la cota, luego encima, luego debajo; nunca sale de la banda del recinto. */
    static int[] ordenDeAlturas(final int abajo, final int arriba) {
        int maxArriba = Math.max(0, arriba);
        int maxAbajo = Math.max(0, abajo);
        int[] orden = new int[maxArriba + maxAbajo + 1];
        int i = 0;
        orden[i++] = 0;
        for (int d = 1; d <= Math.max(maxArriba, maxAbajo); d++) {
            if (d <= maxArriba) {
                orden[i++] = d;
            }
            if (d <= maxAbajo) {
                orden[i++] = -d;
            }
        }
        // Sin huecos: si la banda es asimetrica el array puede quedar justo; se recorta si hiciera falta.
        if (i == orden.length) {
            return orden;
        }
        int[] recortado = new int[i];
        System.arraycopy(orden, 0, recortado, 0, i);
        return recortado;
    }

    /** Predicado horizontal puro del recinto (misma formula que {@code VillageManager.dentroDelRecinto}). */
    public static boolean dentroDelRadio(final int x, final int z, final int cx, final int cz,
                                         final double radio) {
        double dx = x + 0.5D - (cx + 0.5D);
        double dz = z + 0.5D - (cz + 0.5D);
        return dx * dx + dz * dz <= radio * radio;
    }

    /**
     * A2 FASE 1 · fija el destino de mision como <b>ULTIMO</b> elemento de la cadena, sin duplicarlo.
     * Es la regla que impide que un waypoint tactico (p. ej. el anillo exterior) sea el final de la mision:
     * el ejecutor solo declara {@code ARRIVED} cuando agota las etapas, y la ultima es siempre el interior.
     */
    public static <T> java.util.List<T> conDestinoFinal(final java.util.List<T> etapas, final T destino) {
        java.util.List<T> salida = new java.util.ArrayList<>(etapas == null ? java.util.List.of() : etapas);
        if (destino == null) {
            return salida;
        }
        if (!salida.isEmpty() && destino.equals(salida.get(salida.size() - 1))) {
            return salida;
        }
        salida.add(destino);
        return salida;
    }
}
