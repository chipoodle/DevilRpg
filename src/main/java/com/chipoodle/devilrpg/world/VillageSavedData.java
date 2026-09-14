package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 *       tickear nada: se acumula al consultarla ({@link #accruePressure(int, long, double)}), así que es
 *       determinista y avanza igual aunque el chunk esté descargado. Cuando la presión pasa el mínimo, las
 *       hordas empiezan a apuntar a esa aldea; al defenderla se reinicia. Una aldea <b>debilitada</b>
 *       (pocos aldeanos) acumula presión más rápido.</li>
 *   <li><b>Caída</b>: la aldea se quedó sin aldeanos a causa de una horda. No vuelve a ser objetivo de hordas,
 *       queda en <b>ruinas</b> y, si era la del objetivo actual, este avanza (se perdió).</li>
 *   <li><b>Salud</b>: aldeanos vivos la última vez que se pudo mirar la aldea ({@link #getHealth}), cuándo se
 *       repobló por última vez y la <b>comida</b> almacenada (la granja la produce, los aldeanos la comen).</li>
 * </ul>
 */
public final class VillageSavedData extends SavedData {

    private static final String FILE_ID = "devilrpg_villages";

    /** La aldea todavía no se ha podido mirar (chunk descargado), así que su salud es desconocida. */
    public static final int HEALTH_UNKNOWN = -1;

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
    /** Salud de la aldea = aldeanos vivos la última vez que se pudo contar. */
    private final Map<Integer, Integer> health = new HashMap<>();
    /** Última vez que se repobló la aldea (tick de juego), para ir de uno en uno. */
    private final Map<Integer, Long> repopulatedAt = new HashMap<>();
    /** Comida almacenada: la granja la produce y cada aldeano consume. */
    private final Map<Integer, Integer> food = new HashMap<>();
    /** Desde cuándo la aldea pasa hambre (0 = tiene comida). Sirve para las consecuencias del hambre. */
    private final Map<Integer, Long> starvingSince = new HashMap<>();

    /**
     * Planos de las aldeas: qué bloque debería haber en cada sitio (fuera del suelo natural). Los usa el
     * <b>aldeano obrero</b> para saber qué falta y volver a ponerlo bloque a bloque. Se guardan como paleta +
     * dos arrays paralelos (posiciones comprimidas con {@link BlockPos#asLong} e índices de la paleta) para que
     * el guardado no engorde.
     */
    public record Blueprint(List<BlockState> palette, long[] positions, int[] states) {
        public int size() {
            return positions.length;
        }

        public BlockPos posAt(int index) {
            return BlockPos.of(positions[index]);
        }

        public BlockState stateAt(int index) {
            return palette.get(states[index]);
        }
    }

    private final Map<Integer, Blueprint> blueprints = new HashMap<>();
    /**
     * Versión del <b>trazado</b> de la aldea que ya tiene aplicada ({@link VillageManager#CURRENT_LAYOUT}). Sirve
     * para arreglar las aldeas ya construidas cuando cambia el diseño: si su versión es menor, el gestor vuelve a
     * levantar las partes afectadas (p. ej. la granja, cuyo agua quedaba un bloque por debajo de la tierra).
     */
    private final Map<Integer, Integer> layout = new HashMap<>();
    /**
     * ¿Esta aldea ya tiene las <b>casas del juego</b>? Las aldeas de partidas viejas tienen cabañas procedurales
     * ({@code VillageGenerator.hut}) y hay que sustituirlas una vez. Se guarda para no volver a reconstruirlas
     * (rehacer una casa borra lo que hubiera dentro).
     */
    private final Map<Integer, Boolean> casasNuevas = new HashMap<>();

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
        // Salud, repoblación y comida de cada aldea (una entrada por aldea).
        for (Tag element : tag.getList("Settlement", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            int index = entry.getInt("Index");
            if (entry.contains("Health")) {
                data.health.put(index, entry.getInt("Health"));
            }
            if (entry.contains("RepopulatedAt")) {
                data.repopulatedAt.put(index, entry.getLong("RepopulatedAt"));
            }
            if (entry.contains("StarvingSince")) {
                data.starvingSince.put(index, entry.getLong("StarvingSince"));
            }
            if (entry.contains("Layout")) {
                data.layout.put(index, entry.getInt("Layout"));
            }
            if (entry.contains("CasasNuevas")) {
                data.casasNuevas.put(index, entry.getBoolean("CasasNuevas"));
            }
            data.food.put(index, entry.getInt("Food"));
        }
        // Planos de las aldeas (paleta de estados + posiciones + índices).
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag element : tag.getList("Blueprints", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            List<BlockState> palette = new ArrayList<>();
            for (Tag stateTag : entry.getList("Palette", Tag.TAG_COMPOUND)) {
                palette.add(NbtUtils.readBlockState(blocks, (CompoundTag) stateTag));
            }
            data.blueprints.put(entry.getInt("Index"),
                    new Blueprint(palette, entry.getLongArray("Pos"), entry.getIntArray("State")));
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
        ListTag settlementTag = new ListTag();
        Set<Integer> villages = new HashSet<>(health.keySet());
        villages.addAll(repopulatedAt.keySet());
        villages.addAll(food.keySet());
        villages.addAll(starvingSince.keySet());
        villages.addAll(layout.keySet());
        villages.addAll(casasNuevas.keySet());
        for (int index : villages) {
            CompoundTag one = new CompoundTag();
            one.putInt("Index", index);
            if (health.containsKey(index)) {
                one.putInt("Health", health.get(index));
            }
            if (repopulatedAt.containsKey(index)) {
                one.putLong("RepopulatedAt", repopulatedAt.get(index));
            }
            if (starvingSince.containsKey(index)) {
                one.putLong("StarvingSince", starvingSince.get(index));
            }
            if (layout.containsKey(index)) {
                one.putInt("Layout", layout.get(index));
            }
            if (casasNuevas.containsKey(index)) {
                one.putBoolean("CasasNuevas", casasNuevas.get(index));
            }
            one.putInt("Food", food.getOrDefault(index, 0));
            settlementTag.add(one);
        }
        tag.put("Settlement", settlementTag);
        ListTag blueprintTag = new ListTag();
        for (Map.Entry<Integer, Blueprint> entry : blueprints.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("Index", entry.getKey());
            ListTag paletteTag = new ListTag();
            for (BlockState state : entry.getValue().palette()) {
                paletteTag.add(NbtUtils.writeBlockState(state));
            }
            one.put("Palette", paletteTag);
            one.putLongArray("Pos", entry.getValue().positions());
            one.putIntArray("State", entry.getValue().states());
            blueprintTag.add(one);
        }
        tag.put("Blueprints", blueprintTag);
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
     * Suma a la presión de la aldea los ticks transcurridos desde la última vez que se miró (multiplicados por
     * {@code multiplier}, que es como se premia a las aldeas débiles) y devuelve el total acumulado. La primera
     * consulta solo fija el punto de partida (presión 0).
     */
    public int accruePressure(int objectiveIndex, long gameTime) {
        return accruePressure(objectiveIndex, gameTime, 1.0D);
    }

    public int accruePressure(int objectiveIndex, long gameTime, double multiplier) {
        long since = pressureSince.getOrDefault(objectiveIndex, gameTime);
        int previous = pressureTicks.getOrDefault(objectiveIndex, 0);
        long elapsed = Math.max(0L, gameTime - since);
        long weight = (long) (elapsed * Math.max(0.0D, multiplier));
        int accrued = (int) Math.min(Integer.MAX_VALUE, previous + weight);
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

    // --- Salud del asentamiento (Iteración 3, paso 2) ---------------------------------------------

    /**
     * Aldeanos vivos la última vez que se pudo contar ({@link #HEALTH_UNKNOWN} si nunca se ha mirado, p. ej.
     * porque el chunk está descargado). No se cuenta en vivo a propósito: cuando el jugador está lejos el
     * recuento daría 0 y la aldea parecería muerta.
     */
    public int getHealth(int objectiveIndex) {
        return health.getOrDefault(objectiveIndex, HEALTH_UNKNOWN);
    }

    public void setHealth(int objectiveIndex, int villagers) {
        if (health.getOrDefault(objectiveIndex, HEALTH_UNKNOWN) != villagers) {
            health.put(objectiveIndex, villagers);
            setDirty();
        }
    }

    /** Tick de juego en el que se repobló la aldea por última vez. */
    public long getRepopulatedAt(int objectiveIndex) {
        return repopulatedAt.getOrDefault(objectiveIndex, 0L);
    }

    public void markRepopulated(int objectiveIndex, long gameTime) {
        repopulatedAt.put(objectiveIndex, gameTime);
        setDirty();
    }

    /** Comida almacenada en la aldea (la produce la granja, la comen los aldeanos). */
    public int getFood(int objectiveIndex) {
        return food.getOrDefault(objectiveIndex, 0);
    }

    public void setFood(int objectiveIndex, int value) {
        int clamped = Math.max(0, value);
        if (food.getOrDefault(objectiveIndex, 0) != clamped) {
            food.put(objectiveIndex, clamped);
            setDirty();
        }
    }

    /**
     * Desde cuándo la aldea pasa hambre (tick de juego; {@code 0} = tiene comida). Con esto se miden las
     * consecuencias: mientras dura, los aldeanos están débiles, y si se alarga muere alguno.
     */
    public long getStarvingSince(int objectiveIndex) {
        return starvingSince.getOrDefault(objectiveIndex, 0L);
    }

    public void setStarvingSince(int objectiveIndex, long gameTime) {
        long anterior = starvingSince.getOrDefault(objectiveIndex, 0L);
        if (anterior != gameTime) {
            if (gameTime == 0L) {
                starvingSince.remove(objectiveIndex);
            } else {
                starvingSince.put(objectiveIndex, gameTime);
            }
            setDirty();
        }
    }

    // --- Plano de la aldea (Iteración 3, obrero) ---------------------------------------------------

    public boolean hasBlueprint(int objectiveIndex) {
        return blueprints.containsKey(objectiveIndex);
    }

    /** El plano de esa aldea, o {@code null} si todavía no se ha capturado. */
    public Blueprint getBlueprint(int objectiveIndex) {
        return blueprints.get(objectiveIndex);
    }

    public void setBlueprint(int objectiveIndex, Blueprint blueprint) {
        blueprints.put(objectiveIndex, blueprint);
        setDirty();
    }

    /** Tira el plano para que se vuelva a capturar (cuando cambia el trazado o la forma de capturarlo). */
    public void clearBlueprint(int objectiveIndex) {
        if (blueprints.remove(objectiveIndex) != null) {
            setDirty();
        }
    }

    /** Versión del trazado de la aldea que ya tiene aplicada ({@code 0} = de antes de llevar la cuenta). */
    public int getLayout(int objectiveIndex) {
        return layout.getOrDefault(objectiveIndex, 0);
    }

    public void setLayout(int objectiveIndex, int version) {
        if (layout.getOrDefault(objectiveIndex, 0) != version) {
            layout.put(objectiveIndex, version);
            setDirty();
        }
    }

    /** ¿Esta aldea ya tiene las casas del juego (y no las cabañas procedurales de las partidas viejas)? */
    public boolean hasNewHouses(int objectiveIndex) {
        return casasNuevas.getOrDefault(objectiveIndex, false);
    }

    public void setNewHouses(int objectiveIndex, boolean value) {
        if (casasNuevas.getOrDefault(objectiveIndex, false) != value) {
            casasNuevas.put(objectiveIndex, value);
            setDirty();
        }
    }
}
