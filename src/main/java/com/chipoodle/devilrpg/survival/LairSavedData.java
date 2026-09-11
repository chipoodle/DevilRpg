package com.chipoodle.devilrpg.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    /** Núcleo exacto de cada guarida limpiada, para poder COMPROBAR que de verdad está destruida. */
    private final Map<Integer, BlockPos> clearedCores = new HashMap<>();
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
        // Núcleo de cada guarida limpiada (puede no estar en datos antiguos: entonces se deja sin posición).
        for (Tag element : tag.getList("ClearedCores", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            int index = entry.getInt("Index");
            NbtUtils.readBlockPos(entry, "Core").ifPresent(pos -> data.clearedCores.put(index, pos));
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
        ListTag coresTag = new ListTag();
        for (Map.Entry<Integer, BlockPos> entry : clearedCores.entrySet()) {
            CompoundTag coreTag = new CompoundTag();
            coreTag.putInt("Index", entry.getKey());
            coreTag.put("Core", NbtUtils.writeBlockPos(entry.getValue()));
            coresTag.add(coreTag);
        }
        tag.put("ClearedCores", coresTag);
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

    /** Núcleo que tenía una guarida limpiada (para comprobar que de verdad está destruida), o {@code null}. */
    public BlockPos getClearedCore(int objectiveIndex) {
        return clearedCores.get(objectiveIndex);
    }

    /** ¿El sello de esta guarida ya está roto? (entonces el núcleo queda expuesto al volver) */
    public boolean isSealBroken(int objectiveIndex) {
        return sealBroken.contains(objectiveIndex);
    }

    public void markCleared(int objectiveIndex, BlockPos corePos) {
        boolean changed = cleared.add(objectiveIndex);
        if (corePos != null && !corePos.equals(clearedCores.put(objectiveIndex, corePos))) {
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    /**
     * Deshace la marca de "limpiada". Se usa cuando la marca era <b>errónea</b> (el núcleo sigue en el mundo):
     * una marca falsa dejaba la guarida muerta para siempre —sin oleadas, sin guardián y con la caja de
     * sellos en pie—, porque ese estado también se guardaba.
     */
    public void unmarkCleared(int objectiveIndex) {
        boolean changed = cleared.remove(objectiveIndex);
        if (clearedCores.remove(objectiveIndex) != null) {
            changed = true;
        }
        if (changed) {
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
