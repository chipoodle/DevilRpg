package com.chipoodle.devilrpg.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mapa autoritativo de dueños de parcela, independiente de Minecraft para poder
 * probarse. La clave incluye aldea+parcela; el valor es el UUID del dueño.
 */
public final class FarmPlotOwners {

    private final Map<String, UUID> owners = new LinkedHashMap<>();

    public UUID get(String key) {
        return owners.get(key);
    }

    public boolean claim(String key, UUID owner) {
        UUID current = owners.get(key);
        if (current != null && !current.equals(owner)) {
            return false;
        }
        if (current == null) {
            owners.put(key, owner);
        }
        return true;
    }

    /** Resolución determinista de duplicados históricos: gana el UUID menor. */
    public void putResolved(String key, UUID owner) {
        UUID current = owners.get(key);
        if (current == null || owner.compareTo(current) < 0) {
            owners.put(key, owner);
        }
    }

    public void release(String key, UUID owner) {
        if (owner.equals(owners.get(key))) {
            owners.remove(key);
        }
    }

    public Map<String, UUID> entries() {
        return Collections.unmodifiableMap(owners);
    }
}
