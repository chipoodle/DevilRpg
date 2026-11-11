package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 FASE 1 · prueba enfocada (sin servidor ni Minecraft) de la geometria del destino de mision.
 *
 * <p>Se ejecuta con {@code javac} + {@code java} directamente: ni {@link MissionDestination} ni esta clase
 * tocan clases de Minecraft. Comprueba exactamente la aceptacion de la fase:
 * <ul>
 *   <li>el destino final es una celda <b>estrictamente interior</b> al perimetro;</li>
 *   <li>ninguna celda del anillo exterior (radio del muro o mayor) puede ser el destino;</li>
 *   <li>la busqueda es acotada y devuelve {@code null} si no hay candidato (nunca inventa).</li>
 * </ul>
 */
public final class MissionDestinationTest {

    private static int fallos;

    public static void main(String[] args) {
        int radioMuro = 62;
        int margen = 4;
        int maxInterior = radioMuro - margen; // 58
        int cx = 629;
        int cz = 597;
        int cota = 71;

        // 1. Plaza llana: el centro ya es pisable -> destino = centro.
        MissionDestination.Cell centro = MissionDestination.findInterior(cx, cz, cota, radioMuro, margen,
                6, 16, (x, y, z) -> y == cota);
        comprobar("centro llano devuelve el centro",
                centro != null && centro.x() == cx && centro.z() == cz && centro.y() == cota);

        // 2. Centro ocupado (plaza solida): debe elegir otro punto INTERIOR, no el anillo exterior.
        MissionDestination.Cell otro = MissionDestination.findInterior(cx, cz, cota, radioMuro, margen,
                6, 16, (x, y, z) -> y == cota
                        && !(x == cx && z == cz)
                        && Math.hypot(x - cx, z - cz) >= 3.0D);
        comprobar("centro ocupado devuelve una celda interior", otro != null);
        if (otro != null) {
            double d = Math.hypot(otro.x() - cx, otro.z() - cz);
            comprobar("destino interior <= radioMuro - margen (d=" + d + ")", d <= maxInterior);
            comprobar("destino interior dentro del perimetro real",
                    MissionDestination.dentroDelRadio(otro.x(), otro.z(), cx, cz, radioMuro));
            comprobar("destino NO cae en el anillo exterior", d < radioMuro);
        }

        // 3. Solo hay celdas en el anillo exterior o fuera: la busqueda NO puede devolverlas.
        MissionDestination.Cell fuera = MissionDestination.findInterior(cx, cz, cota, radioMuro, margen,
                6, 16, (x, y, z) -> y == cota && Math.hypot(x - cx, z - cz) >= radioMuro);
        comprobar("el anillo exterior no es un destino valido", fuera == null);

        // 4. Sin candidato: busqueda acotada, termina y devuelve null.
        MissionDestination.Cell ninguno = MissionDestination.findInterior(cx, cz, cota, radioMuro, margen,
                6, 16, (x, y, z) -> false);
        comprobar("sin candidato devuelve null (no inventa)", ninguno == null);

        // 5. Orden de alturas: 0, +1, -1, +2, -2... dentro de la banda, sin salirse ni repetir.
        int[] orden = MissionDestination.ordenDeAlturas(6, 16);
        comprobar("orden de alturas empieza en la cota", orden.length > 0 && orden[0] == 0);
        boolean dentroDeBanda = true;
        java.util.Set<Integer> vistos = new java.util.HashSet<>();
        for (int dy : orden) {
            dentroDeBanda &= dy >= -6 && dy <= 16;
            vistos.add(dy);
        }
        comprobar("orden de alturas no sale de la banda del recinto", dentroDeBanda);
        comprobar("orden de alturas cubre +16 y -6", vistos.contains(16) && vistos.contains(-6));

        // 6. El predicado horizontal coincide con la formula del recinto (centro + 0.5).
        comprobar("predicado horizontal: centro dentro", MissionDestination.dentroDelRadio(cx, cz, cx, cz, 62));
        comprobar("predicado horizontal: 62 bloques justos dentro",
                MissionDestination.dentroDelRadio(cx + 62, cz, cx, cz, 62));
        comprobar("predicado horizontal: 63 fuera",
                !MissionDestination.dentroDelRadio(cx + 63, cz, cx, cz, 62));

        // 7. Regla de cadena: el destino de mision es SIEMPRE el ultimo elemento; un waypoint exterior
        //    intermedio no puede quedar como final ni duplicarse.
        java.util.List<String> cadena = new java.util.ArrayList<>(java.util.List.of("escalera-96", "escalera-80",
                "anillo-exterior-64"));
        java.util.List<String> conInterior = MissionDestination.conDestinoFinal(cadena, "interior-58");
        comprobar("el ultimo elemento de la cadena es el interior",
                "interior-58".equals(conInterior.get(conInterior.size() - 1)));
        comprobar("el waypoint exterior sigue siendo intermedio, no final",
                conInterior.indexOf("anillo-exterior-64") < conInterior.size() - 1);
        comprobar("no se duplica el interior",
                MissionDestination.conDestinoFinal(conInterior, "interior-58").size() == conInterior.size());
        comprobar("sin destino no se altera la cadena",
                MissionDestination.conDestinoFinal(cadena, null).equals(cadena));

        if (fallos > 0) {
            System.out.println("PHASE1_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("PHASE1_TEST: OK (todas las aserciones verdes)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + nombre);
        } else {
            System.out.println("  ok: " + nombre);
        }
    }
}
