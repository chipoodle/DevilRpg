package com.chipoodle.devilrpg.entity.goal;

public final class ForestryTaskPolicyTest {
    private static int fallos;
    public static void main(String[] args) {
        check(ForestryTaskPolicy.consistent("TALAR", "TRAVELLING", true), "TALAR/TRAVELLING");
        check(ForestryTaskPolicy.consistent("TALAR", "HARVESTING", true), "TALAR/HARVESTING");
        check(!ForestryTaskPolicy.consistent("TALAR", "GROWING", true), "TALAR/GROWING rechazado");
        check(ForestryTaskPolicy.consistent("PLANTAR", "REPLANTING", true), "PLANTAR");
        check(ForestryTaskPolicy.consistent("ABONAR", "GROWING", true), "ABONAR");
        check(ForestryTaskPolicy.consistent("ENTREGAR", "DELIVERING", true), "ENTREGAR");
        check(ForestryTaskPolicy.consistent("SCOUTING", "SCOUTING", false), "SCOUTING sin target");
        check(!ForestryTaskPolicy.consistent("SCOUTING", "SCOUTING", true), "SCOUTING con target");
        check(!ForestryTaskPolicy.consistent("TALAR", "TRAVELLING", false), "target obligatorio");
        ForestryTaskController c = new ForestryTaskController();
        c.assign(1, 2, 3);
        check(c.state() == ForestryTaskController.State.TRAVELLING, "assign -> TRAVELLING");
        c.harvesting();
        check(c.state() == ForestryTaskController.State.HARVESTING, "harvesting -> HARVESTING");
        c.harvesting();
        check(c.state() == ForestryTaskController.State.HARVESTING, "harvesting idempotent");
        c.replanting();
        check(c.state() == ForestryTaskController.State.REPLANTING, "replanting -> REPLANTING");
        c.replanting();
        check(c.state() == ForestryTaskController.State.REPLANTING, "replanting idempotent");
        check(ForestryTaskPolicy.consistent("PLANTAR", "REPLANTING", true), "PLANTAR/REPLANTING policy");
        c.scouting();
        check(c.state() == ForestryTaskController.State.SCOUTING, "complete -> SCOUTING");
        ForestryWorkPlanner p = new ForestryWorkPlanner();
        p.restore(10, -5, 2, 3, false);
        check(p.originX() == 10 && p.originZ() == -5 && p.ring() == 2 && p.cursor() == 3, "planner restore");
        p.restore(0, 0, -1, -4, false);
        check(p.ring() == 0 && p.cursor() == 0, "planner bounds");
        if (fallos > 0) { System.out.println("FORESTRY_TASK_POLICY_TEST: FAIL (" + fallos + ")"); System.exit(1); }
        System.out.println("FORESTRY_TASK_POLICY_TEST: OK");
    }
    private static void check(boolean ok, String name) { if (!ok) { fallos++; System.out.println("  FALLO: " + name); } }
}
