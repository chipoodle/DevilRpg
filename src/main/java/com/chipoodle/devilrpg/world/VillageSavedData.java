package com.chipoodle.devilrpg.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estado <b>persistente</b> de las aldeas, guardado por dimensión (como los raids de vanilla).
 * <p>
 * Antes todo el estado de {@link VillageManager} vivía en memoria, así que al reiniciar la partida:
 * <ul>
 *   <li>la aldea <b>se volvía a generar encima de la que ya había</b> (nivelaba el terreno, despejaba
 *       vegetación y reconstruía cabañas y valla, cargándose lo que el jugador hubiera construido cerca);</li>
 *   <li>el <b>asedio se reiniciaba</b>, aunque ya lo hubieras resuelto;</li>
 *   <li>volvía a salir el aviso de "divisas una aldea a lo lejos".</li>
 * </ul>
 * Se indexa por <b>índice de objetivo</b>, porque la posición de la aldea es el propio objetivo.
 * <p>
 * Desde la Iteración 3 guarda además el <b>estado de asentamiento</b> que hace que el mundo viva solo:
 * <ul>
 *   <li><b>Presión</b>: ticks de juego que la aldea lleva <b>sin que nadie la atienda</b>. No hace falta
 *       tickear nada: se acumula al consultarla ({@link #accruePressure(int, long)}), así que es determinista
 *       y avanza igual aunque el chunk esté descargado. Cuando la presión pasa el mínimo, las hordas empiezan
 *       a apuntar a esa aldea; al defenderla se reinicia.</li>
 *   <li><b>Caída</b>: la aldea se quedó sin aldeanos a causa de una horda. No vuelve a ser objetivo de hordas
 *       y, si era la del objetivo actual, este avanza (se perdió).</li>
 * </ul>
 */
public final class VillageSavedData extends SavedData {

    private static final String FILE_ID = "devilrpg_villages";

    /** Objetivos cuya aldea ya está construida: no se vuelve a generar el terreno. */
    private final Set<Integer> generated = new HashSet<>();
    /** Objetivos cuyo asedio ya se resolvió (salvada o caída): no se relanza otro. */
    private final Set<Integer> resolved = new HashSet<>();
    /** Avisos ya dados, como {@code "objetivo:uuidDelJugador"} (el aviso es por jugador). */
    private final Set<String> noticed = new HashSet<>();
    /** Presión acumulada por aldea (ticks de abandono) y desde qué tick de juego se cuenta. */
    private final Map<Integer, Integer> pressureTicks = new HashMap<>();
    private final Map<Integer, Long> pressureSince = new HashMap<>();
    /** Aldeas que ya han caído (sin aldeanos): dejan de ser objetivo de las hordas. */
    private final Set<Integer> fallen = new HashSet<>();

    public static VillageSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageSavedData::new, VillageSavedData::load), FILE_ID);
    }

    public static VillageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VillageSavedData data = new VillageSavedData();
        for (int index : tag.getIntArray("Generated")) {
            data.generated.add(index);
        }
        for (int index : tag.getIntArray("Resolved")) {
            data.resolved.add(index);
        }
        for (Tag element : tag.getList("Noticed", Tag.TAG_STRING)) {
            data.noticed.add(element.getAsString());
        }
        for (int index : tag.getIntArray("Fallen")) {
            data.fallen.add(index);
            data.resolved.add(index); // una aldea caída no vuelve a asediarse
        }
        for (Tag element : tag.getList("Pressure", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            int index = entry.getInt("Index");
            data.pressureTicks.put(index, entry.getInt("Ticks"));
            data.pressureSince.put(index, entry.getLong("Since"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray("Generated", toArray(generated));
        tag.putIntArray("Resolved", toArray(resolved));
        ListTag noticedTag = new ListTag();
        for (String key : noticed) {
            noticedTag.add(StringTag.valueOf(key));
        }
        tag.put("Noticed", noticedTag);
        tag.putIntArray("Fallen", toArray(fallen));
        ListTag pressureTag = new ListTag();
        for (Map.Entry<Integer, Integer> entry : pressureTicks.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("Index", entry.getKey());
            one.putInt("Ticks", entry.getValue());
            one.putLong("Since", pressureSince.getOrDefault(entry.getKey(), 0L));
            pressureTag.add(one);
        }
        tag.put("Pressure", pressureTag);
        return tag;
    }

    private static int[] toArray(Set<Integer> values) {
        int[] out = new int[values.size()];
        int i = 0;
        for (int value : values) {
            out[i++] = value;
        }
        return out;
    }

    /** ¿La aldea de este objetivo ya está construida? (entonces NO se vuelve a generar el terreno) */
    public boolean isGenerated(int objectiveIndex) {
        return generated.contains(objectiveIndex);
    }

    public void markGenerated(int objectiveIndex) {
        if (generated.add(objectiveIndex)) {
            setDirty();
        }
    }

    /** ¿Ya se resolvió el asedio de este objetivo? (salvada o caída; no se relanza otro) */
    public boolean isSiegeResolved(int objectiveIndex) {
        return resolved.contains(objectiveIndex);
    }

    public void markSiegeResolved(int objectiveIndex) {
        if (resolved.add(objectiveIndex)) {
            setDirty();
        }
    }

    public boolean isNoticed(int objectiveIndex, UUID player) {
        return noticed.contains(objectiveIndex + ":" + player);
    }

    public void markNoticed(int objectiveIndex, UUID player) {
        if (noticed.add(objectiveIndex + ":" + player)) {
            setDirty();
        }
    }

    // --- Estado de asentamiento (Iteración 3) -----------------------------------------------------

    /**
     * Suma a la presión de la aldea los ticks transcurridos desde la última vez que se miró y devuelve el
     * total acumulado. La primera consulta solo fija el punto de partida (presión 0).
     */
    public int accruePressure(int objectiveIndex, long gameTime) {
        long since = pressureSince.getOrDefault(objectiveIndex, gameTime);
        int previous = pressureTicks.getOrDefault(objectiveIndex, 0);
        long elapsed = Math.max(0L, gameTime - since);
        int accrued = (int) Math.min(Integer.MAX_VALUE, previous + elapsed);
        pressureSince.put(objectiveIndex, gameTime);
        if (accrued != previous) {
            pressureTicks.put(objectiveIndex, accrued);
            setDirty();
        }
        return accrued;
    }

    /** Presión actual (ticks de abandono) sin acumular tiempo nuevo. */
    public int getPressureTicks(int objectiveIndex) {
        return pressureTicks.getOrDefault(objectiveIndex, 0);
    }

    /** La aldea ha sido atendida (defendida o el jugador volvió): la presión vuelve a cero. */
    public void resetPressure(int objectiveIndex) {
        boolean changed = pressureTicks.remove(objectiveIndex) != null;
        pressureSince.remove(objectiveIndex);
        if (changed) {
            setDirty();
        }
    }

    /** ¿La aldea ya cayó (se quedó sin aldeanos por una horda)? */
    public boolean isFallen(int objectiveIndex) {
        return fallen.contains(objectiveIndex);
    }

    /** Marca la aldea como caída: deja de generar presión y no vuelve a ser objetivo de hordas. */
    public void markFallen(int objectiveIndex) {
        if (fallen.add(objectiveIndex)) {
            resolved.add(objectiveIndex); // caída = asedio resuelto (no se relanza)
            pressureTicks.remove(objectiveIndex);
            pressureSince.remove(objectiveIndex);
            setDirty();
        }
    }
}
