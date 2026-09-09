package com.chipoodle.devilrpg.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Define la posición de cada objetivo de progresión de forma <b>determinista</b> a partir del punto de
 * inicio del jugador (spawn) y del índice del objetivo. Así server y cliente calculan exactamente el
 * mismo punto sin necesidad de sincronizar la posición.
 * <p>
 * Todos los objetivos apuntan en <b>una dirección fija</b> desde el spawn (para que la flecha no salte de
 * dirección y no confunda). La <b>distancia</b> es seudoaleatoria pero determinista por índice de objetivo,
 * entre {@link #MIN_DISTANCE} y {@link #MAX_DISTANCE}, para que el jugador encuentre terrenos variados
 * (no siempre el mismo bioma a la misma distancia exacta) sin romper la sincronización.
 * <p>
 * Genera terrenos bien inaccesibles en cuenta el generador de la aldea: como el punto puede caer en
 * océano o montaña, {@code VillageGenerator} ya nivela el terreno o construye una isla flotante para que
 * la aldea siempre se asiente de forma segura en el punto exacto del objetivo.
 */
public final class ObjectiveTargets {

    /** Distancia mínima del objetivo desde el spawn (bloques). */
    public static final int MIN_DISTANCE = 800;
    /** Distancia máxima del objetivo desde el spawn (bloques). */
    public static final int MAX_DISTANCE = 1200;
    /**
     * Distancia mínima que aumenta cada objetivo respecto al anterior (bloques). Garantiza que los
     * objetivos no se solapen (separación >= OBJECTIVE_STEP - (MAX-MIN) = 200 bloques).
     */
    private static final int OBJECTIVE_STEP = 600;
    /** Radio horizontal (bloques) para considerar el objetivo alcanzado. */
    public static final int REACH_RADIUS = 24;
    /** Semilla base para la posición del objetivo (se combina con el índice). */
    private static final long SEED = 0x5DEECE66DL;
    /** Dirección fija (grados) desde el spawn hacia la que apuntan TODOS los objetivos. */
    private static final double FIXED_ANGLE_DEG = 45.0; // noreste

    private ObjectiveTargets() {
    }

    /** Posición (x,z) del objetivo {@code objectiveIndex}, a distancia seudoaleatoria determinista. */
    public static BlockPos targetOf(Vec3 spawn, int objectiveIndex) {
        double distance = distanceFor(objectiveIndex);
        double rad = Math.toRadians(FIXED_ANGLE_DEG);
        int x = (int) Math.floor(spawn.x + Math.cos(rad) * distance);
        int z = (int) Math.floor(spawn.z + Math.sin(rad) * distance);
        return new BlockPos(x, (int) Math.floor(spawn.y), z);
    }

    /**
     * Distancia seudoaleatoria determinista del objetivo: el primero cae entre 800 y 1200, y cada objetivo
     * siguiente se aleja al menos {@link #OBJECTIVE_STEP} bloques (con variación). Así los objetivos nunca
     * se superponen, sin romper la sincronización servidor/cliente.
     */
    private static double distanceFor(int objectiveIndex) {
        Random rnd = new Random(SEED + objectiveIndex);
        double base = MIN_DISTANCE + objectiveIndex * OBJECTIVE_STEP;
        double variation = rnd.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        return base + variation;
    }

    /** Distancia horizontal al cuadrado entre una posición y el objetivo. */
    public static double horizontalDistSqr(BlockPos from, BlockPos target) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        return dx * dx + dz * dz;
    }
}
