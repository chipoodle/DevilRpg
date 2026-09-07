package com.chipoodle.devilrpg.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Define la posición de cada objetivo de progresión de forma <b>determinista</b> a partir del punto de
 * inicio del jugador (spawn) y del índice del objetivo. Así server y cliente calculan exactamente el
 * mismo punto sin necesidad de sincronizar la posición.
 * <p>
 * El objetivo i está a {@code BASE_DISTANCE + i*STEP_DISTANCE} bloques del spawn, en una dirección fija
 * (ángulo áureo) para que el jugador "salga" del spawn en direcciones variadas cada vez. Esto fuerza a
 * avanzar cada vez más lejos y a no establecerse en un solo lugar.
 */
public final class ObjectiveTargets {

    /** Distancia del primer objetivo desde el spawn (bloques). */
    public static final int BASE_DISTANCE = 800;
    /** Distancia adicional por cada objetivo superado (bloques). */
    public static final int STEP_DISTANCE = 600;
    /** Radio horizontal (bloques) para considerar el objetivo alcanzado. */
    public static final int REACH_RADIUS = 24;
    /** Ángulo base (grados) para fijar la dirección del primer objetivo. */
    private static final double BASE_ANGLE_DEG = 137.5; // ángulo áureo

    private ObjectiveTargets() {
    }

    /**
     * Posición (x,z) del objetivo {@code objectiveIndex}. Se usa la Y del spawn pero el alcance se
     * comprueba en horizontal, así que la Y no afecta a la dirección mostrada.
     */
    public static BlockPos targetOf(Vec3 spawn, int objectiveIndex) {
        double distance = BASE_DISTANCE + (double) objectiveIndex * STEP_DISTANCE;
        double rad = Math.toRadians(BASE_ANGLE_DEG * (objectiveIndex + 1));
        int x = (int) Math.floor(spawn.x + Math.cos(rad) * distance);
        int z = (int) Math.floor(spawn.z + Math.sin(rad) * distance);
        return new BlockPos(x, (int) Math.floor(spawn.y), z);
    }

    /** Distancia horizontal al cuadrado entre una posición y el objetivo. */
    public static double horizontalDistSqr(BlockPos from, BlockPos target) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        return dx * dx + dz * dz;
    }
}
