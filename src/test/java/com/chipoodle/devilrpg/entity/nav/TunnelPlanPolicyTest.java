package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · presupuesto y dirección del túnel subordinado: topes duros, enfriamiento y solo cardinales que
 * acerquen de verdad a la misión.
 */
public final class TunnelPlanPolicyTest {
    public static void main(String[] args) {
        // Topes y enfriamiento.
        check("plan is allowed when cooldown expired and caps are free",
                TunnelPlanPolicy.canRequest(1_000L, 1_000L, 0, 0));
        check("plan is denied during cooldown",
                !TunnelPlanPolicy.canRequest(999L, 1_000L, 0, 0));
        check("plan is denied at the per-mission plan cap",
                !TunnelPlanPolicy.canRequest(1_000L, 0L, TunnelPlanPolicy.MAX_PLANS_PER_MISSION, 0));
        check("plan is denied at the per-mission step cap",
                !TunnelPlanPolicy.canRequest(1_000L, 0L, 0, TunnelPlanPolicy.MAX_STEPS_PER_MISSION));
        check("caps are strictly positive and bounded",
                TunnelPlanPolicy.MAX_STEPS_PER_PLAN > 0 && TunnelPlanPolicy.MAX_PLANS_PER_MISSION > 0
                        && TunnelPlanPolicy.MAX_STEPS_PER_MISSION >= TunnelPlanPolicy.MAX_STEPS_PER_PLAN
                        && TunnelPlanPolicy.PLAN_COOLDOWN_TICKS > 0);

        // Pasos disponibles por activación sin agotar el tope por misión.
        check("fresh mission offers a full activation",
                TunnelPlanPolicy.stepsAvailable(0) == TunnelPlanPolicy.MAX_STEPS_PER_PLAN);
        check("mission cap shrinks the activation",
                TunnelPlanPolicy.stepsAvailable(TunnelPlanPolicy.MAX_STEPS_PER_MISSION - 1) == 1);
        check("exhausted mission offers nothing",
                TunnelPlanPolicy.stepsAvailable(TunnelPlanPolicy.MAX_STEPS_PER_MISSION) == 0);
        check("over-consumed mission clamps to zero",
                TunnelPlanPolicy.stepsAvailable(TunnelPlanPolicy.MAX_STEPS_PER_MISSION + 5) == 0);

        // Direcciones: solo cardinales y ordenadas por progreso real.
        int[][] east = TunnelPlanPolicy.directionsToward(0, 0, 10, 0);
        check("east mission leads with +x", east[0][0] == 1 && east[0][1] == 0);
        int[][] west = TunnelPlanPolicy.directionsToward(0, 0, -10, 0);
        check("west mission leads with -x", west[0][0] == -1 && west[0][1] == 0);
        int[][] north = TunnelPlanPolicy.directionsToward(0, 0, 0, -10);
        check("north mission leads with -z", north[0][0] == 0 && north[0][1] == -1);
        int[][] south = TunnelPlanPolicy.directionsToward(0, 0, 0, 10);
        check("south mission leads with +z", south[0][0] == 0 && south[0][1] == 1);
        check("all four directions are cardinal and unique", allCardinalAndUnique(east)
                && allCardinalAndUnique(TunnelPlanPolicy.directionsToward(3, 7, 9, 2)));
        check("diagonal mission never emits a diagonal step", allCardinalAndUnique(
                TunnelPlanPolicy.directionsToward(0, 0, 10, 10))
                && TunnelPlanPolicy.directionsToward(0, 0, 10, 10)[0][0]
                + TunnelPlanPolicy.directionsToward(0, 0, 10, 10)[0][1] != 0);

        // Prioridad del escalonado sobre el túnel.
        check("viable descent construction has priority over the tunnel",
                TunnelPlanPolicy.constructionHasPriority(true, false, false));
        check("exhausted construction releases the tunnel",
                !TunnelPlanPolicy.constructionHasPriority(true, true, false));
        check("blocked descent releases the tunnel",
                !TunnelPlanPolicy.constructionHasPriority(true, false, true));
        check("mission not below the feet never blocks the tunnel",
                !TunnelPlanPolicy.constructionHasPriority(false, false, false));

        // Progreso horizontal medible.
        check("one block toward the mission is positive progress",
                TunnelPlanPolicy.horizontalProgress(0, 0, 1, 0, 10, 0) > 0.99D);
        check("moving away is negative progress",
                TunnelPlanPolicy.horizontalProgress(0, 0, -1, 0, 10, 0) < 0.0D);
        check("a diagonal cardinal step still makes measurable progress",
                TunnelPlanPolicy.horizontalProgress(0, 0, 1, 0, 10, 10)
                        > TunnelPlanPolicy.MIN_HORIZONTAL_PROGRESS);
        check("sideways movement does not reduce the distance to the mission",
                TunnelPlanPolicy.horizontalProgress(0, 0, 0, 1, 10, 0) < 0.0D);
        check("minimum progress is a positive bound",
                TunnelPlanPolicy.MIN_HORIZONTAL_PROGRESS > 0.0D
                        && TunnelPlanPolicy.MIN_HORIZONTAL_PROGRESS < 1.0D);
        check("wait budget covers the actuator inter-block cadence",
                TunnelPlanPolicy.PHASE_WAIT_TICKS > 40 && TunnelPlanPolicy.MOVE_WAIT_TICKS > 0);

        System.out.println("TUNNEL_PLAN_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static boolean allCardinalAndUnique(int[][] dirs) {
        if (dirs == null || dirs.length != 4) {
            return false;
        }
        for (int i = 0; i < dirs.length; i++) {
            if (dirs[i] == null || dirs[i].length != 2) {
                return false;
            }
            int dx = Math.abs(dirs[i][0]);
            int dz = Math.abs(dirs[i][1]);
            if (dx + dz != 1) {
                return false;
            }
            for (int j = i + 1; j < dirs.length; j++) {
                if (dirs[i][0] == dirs[j][0] && dirs[i][1] == dirs[j][1]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
