package com.chipoodle.devilrpg.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
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
 */
public final class VillageSavedData extends SavedData {

    private static final String FILE_ID = "devilrpg_villages";

    /** Objetivos cuya aldea ya está construida: no se vuelve a generar el terreno. */
    private final Set<Integer> generated = new HashSet<>();
    /** Objetivos cuyo asedio ya se resolvió (salvada o caída): no se relanza otro. */
    private final Set<Integer> resolved = new HashSet<>();
    /** Avisos ya dados, como {@code "objetivo:uuidDelJugador"} (el aviso es por jugador). */
    private final Set<String> noticed = new HashSet<>();

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
}
