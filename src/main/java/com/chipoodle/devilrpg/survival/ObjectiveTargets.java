package com.chipoodle.devilrpg.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Define la posición de cada objetivo de progresión de forma <b>determinista</b> a partir del punto de
 * inicio del jugador (spawn) y del índice del objetivo. Así server y cliente calculan exactamente el
 * mismo punto sin necesidad de sincronizar la posición.
 * <p>
 * Todos los objetivos apuntan en <b>una dirección fija</b> desde el spawn (para que la flecha no salte de
 * dirección y no confunda), y solo cambia la <b>distancia</b>: el objetivo i está a
 * {@code BASE_DISTANCE + i*STEP_DISTANCE} bloques del spawn. Esto obliga a avanzar cada vez más lejos sin
 * establecerse, pero de forma clara y constante.
 */
public final class ObjectiveTargets {

    /** Distancia del primer objetivo desde el spawn (bloques). */
    public static final int BASE_DISTANCE = 800;
    /** Distancia adicional por cada objetivo superado (bloques). */
    public static final int STEP_DISTANCE = 600;
    /** Radio horizontal (bloques) para considerar el objetivo alcanzado. */
    public static final int REACH_RADIUS = 24;
    /** Dirección fija (grados) desde el spawn hacia la que apuntan TODOS los objetivos. */
    private static final double FIXED_ANGLE_DEG = 45.0; // noreste

    private ObjectiveTargets() {
    }

    /**
     * Posición (x,z) del objetivo {@code objectiveIndex}. Todos usan la misma dirección; solo cambia la
     * distancia. La Y del spawn se usa pero el alcance se comprueba en horizontal, así que no afecta.
     */
    public static BlockPos targetOf(Vec3 spawn, int objectiveIndex) {
        double distance = BASE_DISTANCE + (double) objectiveIndex * STEP_DISTANCE;
        double rad = Math.toRadians(FIXED_ANGLE_DEG);
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
