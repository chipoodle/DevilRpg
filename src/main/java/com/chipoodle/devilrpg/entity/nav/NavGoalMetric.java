package com.chipoodle.devilrpg.entity.nav;

/**
 * Metricas puras para la busqueda A2 global. No dependen de Minecraft para poder probar
 * la heuristica y el ranking del frente sin arrancar un servidor.
 */
public final class NavGoalMetric {

    private NavGoalMetric() {
    }

    /**
     * Cota inferior admisible del coste restante hasta la region de meta.
     * Cada arista desplaza como maximo un bloque horizontal y cuesta al menos 1; por tanto, la distancia horizontal restante es una cota inferior del coste. En vertical, una arista puede subir como maximo
     * Una arista puede subir como maximo {@code maxStepUp} o caer como maximo
     * {@code maxSafeDrop}; por ello la distancia vertical restante dividida por ese maximo
     * tambien es una cota inferior. El maximo de ambas cotas sigue siendo admisible.
     */
    public static double heuristic(int x, int y, int z, int goalX, int goalY, int goalZ,
                                   double horizontalRadiusSquared, double verticalTolerance,
                                   int maxStepUp, int maxSafeDrop) {
        double horizontal = horizontalRemaining(x, z, goalX, goalZ, horizontalRadiusSquared);
        double vertical = verticalRemaining(y, goalY, verticalTolerance);
        int maxVerticalStep = y < goalY ? Math.max(1, maxStepUp) : Math.max(1, maxSafeDrop);
        return Math.max(horizontal, vertical / maxVerticalStep);
    }

    /**
     * Distancia al cuadrado de la region de meta en 3D. Se usa solo para elegir el mejor
     * frente parcial cuando A* agota su presupuesto; no participa en el coste A*.
     */
    public static double frontDistanceSquared(int x, int y, int z, int goalX, int goalY, int goalZ,
                                              double horizontalRadiusSquared, double verticalTolerance) {
        double horizontal = horizontalRemaining(x, z, goalX, goalZ, horizontalRadiusSquared);
        double vertical = verticalRemaining(y, goalY, verticalTolerance);
        return horizontal * horizontal + vertical * vertical;
    }

    private static double horizontalRemaining(int x, int z, int goalX, int goalZ,
                                               double horizontalRadiusSquared) {
        double dx = x - goalX;
        double dz = z - goalZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        return Math.max(0.0D, distance - Math.sqrt(Math.max(0.0D, horizontalRadiusSquared)));
    }

    private static double verticalRemaining(int y, int goalY, double verticalTolerance) {
        return Math.max(0.0D, Math.abs(y - goalY) - Math.max(0.0D, verticalTolerance));
    }
}
