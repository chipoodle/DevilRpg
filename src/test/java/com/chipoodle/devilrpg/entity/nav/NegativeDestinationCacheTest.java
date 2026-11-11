package com.chipoodle.devilrpg.entity.nav;

/** A2 REMEDIACIÓN 2 · prueba pura del reintento de la caché negativa de destino. */
public final class NegativeDestinationCacheTest {

    private static int fallos;

    public static void main(String[] args) {
        NegativeDestinationCache cache = new NegativeDestinationCache();
        comprobar("primera búsqueda permitida", cache.puedeBuscar(0L));
        cache.fallo(0L);
        comprobar("fallo no se repite en el mismo tick", !cache.puedeBuscar(0L));
        comprobar("fallo no se repite antes del intervalo", !cache.puedeBuscar(NegativeDestinationCache.RETRY_TICKS - 1L));
        comprobar("se reintenta al cumplir el intervalo", cache.puedeBuscar(NegativeDestinationCache.RETRY_TICKS));
        comprobar("se contabiliza el fallo", cache.intentosFallidos() == 1);

        cache.fallo(NegativeDestinationCache.RETRY_TICKS);
        cache.fallo(NegativeDestinationCache.RETRY_TICKS * 2L);
        comprobar("acumula intentos", cache.intentosFallidos() == 3);

        cache.exito();
        comprobar("éxito limpia el estado", cache.puedeBuscar(1L) && cache.intentosFallidos() == 0);

        if (fallos > 0) {
            System.out.println("NEGATIVE_CACHE_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("NEGATIVE_CACHE_TEST: OK (todas las aserciones verdes)");
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
