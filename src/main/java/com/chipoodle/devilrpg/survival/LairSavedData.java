package com.chipoodle.devilrpg.survival;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Estado <b>persistente</b> de las guaridas, guardado por dimensión (como los raids de vanilla).
 * <p>
 * Antes todo el estado de {@link LairManager} vivía en memoria, así que al reiniciar la partida una guarida
 * <b>ya limpiada volvía a nacer</b>: se regeneraba el terreno, se volvía a colocar el núcleo y su guardián, y
 * el jugador podía cobrar la recompensa otra vez. Lo mismo con el sello: si lo habías roto, al volver
 * aparecía reconstruido.
 * <p>
 * Se guarda por <b>índice de objetivo</b>, porque la posición de la guarida es determinista a partir de él:
 * no hace falta guardar coordenadas.
 */
public final class LairSavedData extends SavedData {

    private static final String FILE_ID = "devilrpg_lairs";

    /** Objetivos cuya guarida ya fue limpiada (núcleo destruido): no se vuelven a generar. */
    private final Set<Integer> cleared = new HashSet<>();
    /** Objetivos cuyo sello ya fue roto: al volver, el núcleo queda expuesto (no se reconstruye la caja). */
    private final Set<Integer> sealBroken = new HashSet<>();

    /** Datos de guaridas del nivel dado (los crea la primera vez). */
    public static LairSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LairSavedData::new, LairSavedData::load), FILE_ID);
    }

    public static LairSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        LairSavedData data = new LairSavedData();
        for (int index : tag.getIntArray("Cleared")) {
            data.cleared.add(index);
        }
        for (int index : tag.getIntArray("SealBroken")) {
            data.sealBroken.add(index);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray("Cleared", toArray(cleared));
        tag.putIntArray("SealBroken", toArray(sealBroken));
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

    /** ¿Esta guarida ya fue limpiada? (entonces no se genera ni spawnea nada) */
    public boolean isCleared(int objectiveIndex) {
        return cleared.contains(objectiveIndex);
    }

    /** ¿El sello de esta guarida ya está roto? (entonces el núcleo queda expuesto al volver) */
    public boolean isSealBroken(int objectiveIndex) {
        return sealBroken.contains(objectiveIndex);
    }

    public void markCleared(int objectiveIndex) {
        if (cleared.add(objectiveIndex)) {
            setDirty();
        }
    }

    public void setSealBroken(int objectiveIndex, boolean broken) {
        boolean changed = broken ? sealBroken.add(objectiveIndex) : sealBroken.remove(objectiveIndex);
        if (changed) {
            setDirty();
        }
    }
}
