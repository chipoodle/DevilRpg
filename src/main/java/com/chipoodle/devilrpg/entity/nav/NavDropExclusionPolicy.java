package com.chipoodle.devilrpg.entity.nav;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Acotado y consciente del terreno: excluye una transicion DROP (origen -> aterrizaje) SOLO mientras
 * las blocks relevantes conservan la misma huella. Si el terreno cambia, la transicion vuelve a ser elegible.
 * Puro: sin Minecraft, para poder probarlo de forma determinista.
 */
public final class NavDropExclusionPolicy {
    public static final int MAX_ENTRADAS = 32;
    private final LinkedHashMap<Long, Long> rechazadas = new LinkedHashMap<>();

    public static long clave(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) ^ ((long) (y & 0xFFF) << 26) ^ (z & 0x3FFFFFF);
    }

    public static long clave(int fx, int fy, int fz, int tx, int ty, int tz) {
        return clave(fx, fy, fz) * 31L + clave(tx, ty, tz);
    }

    public void excluir(long transicion, long huellaTerreno) {
        rechazadas.remove(transicion);
        rechazadas.put(transicion, huellaTerreno);
        while (rechazadas.size() > MAX_ENTRADAS) {
            Long primera = rechazadas.keySet().iterator().next();
            rechazadas.remove(primera);
        }
    }

    public boolean excluida(long transicion, long huellaTerreno) {
        Long h = rechazadas.get(transicion);
        if (h == null) {
            return false;
        }
        if (h != huellaTerreno) {
            rechazadas.remove(transicion);
            return false;
        }
        return true;
    }

    public int tamano() {
        return rechazadas.size();
    }

    public void limpiar() {
        rechazadas.clear();
    }

    public Map<Long, Long> instantanea() {
        return Map.copyOf(rechazadas);
    }
}
