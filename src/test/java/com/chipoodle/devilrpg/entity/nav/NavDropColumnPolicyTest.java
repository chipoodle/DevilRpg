package com.chipoodle.devilrpg.entity.nav;

import java.util.OptionalInt;
import java.util.Set;

/** Regresión de columnas de caída: un bloque de puente nunca debe quedar dentro del corredor vertical. */
public final class NavDropColumnPolicyTest {
    public static void main(String[] args) {
        Set<Integer> bloqueos = Set.of(97, 99);

        OptionalInt libre = NavDropColumnPolicy.primerBloqueoY(94, 96, bloqueos::contains);
        comprobar("columna despejada", libre.isEmpty());

        OptionalInt bloqueadoArriba = NavDropColumnPolicy.primerBloqueoY(94, 97, bloqueos::contains);
        comprobar("detecta el primer bloque en Y=97", bloqueadoArriba.isPresent() && bloqueadoArriba.getAsInt() == 97);

        OptionalInt bloqueadoMasAlto = NavDropColumnPolicy.primerBloqueoY(94, 100, bloqueos::contains);
        comprobar("devuelve el primer bloqueo, no el último", bloqueadoMasAlto.isPresent()
                && bloqueadoMasAlto.getAsInt() == 97);

        OptionalInt sinCaida = NavDropColumnPolicy.primerBloqueoY(97, 96, bloqueos::contains);
        comprobar("no examina celdas cuando el destino no está debajo", sinCaida.isEmpty());

        OptionalInt incluyePrimerBloqueSobreAterrizaje = NavDropColumnPolicy.primerBloqueoY(96, 97,
                y -> y == 97);
        comprobar("incluye la primera celda sobre el aterrizaje", incluyePrimerBloqueSobreAterrizaje.isPresent()
                && incluyePrimerBloqueSobreAterrizaje.getAsInt() == 97);

        comprobar("rechaza puente dentro de la columna del escalon (589,93 -> 589,97)",
                NavDropColumnPolicy.puenteInvadeColumnaDeEscalon(589, 93, 512, 589, 97, 512, 3));
        comprobar("permite puente en columna X/Z adyacente",
                !NavDropColumnPolicy.puenteInvadeColumnaDeEscalon(589, 93, 512, 590, 97, 512, 3));
        comprobar("permite puente por debajo del landing",
                !NavDropColumnPolicy.puenteInvadeColumnaDeEscalon(589, 93, 512, 589, 94, 512, 3));
        comprobar("permite puente fuera de la caida segura",
                !NavDropColumnPolicy.puenteInvadeColumnaDeEscalon(589, 93, 512, 589, 98, 512, 3));
        comprobar("no bloquea con presupuesto de caida nulo",
                !NavDropColumnPolicy.puenteInvadeColumnaDeEscalon(589, 93, 512, 589, 97, 512, 0));

        System.out.println("NAV_DROP_COLUMN_POLICY_TEST: OK (todas las aserciones verdes)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) {
            throw new AssertionError(nombre);
        }
    }
}
