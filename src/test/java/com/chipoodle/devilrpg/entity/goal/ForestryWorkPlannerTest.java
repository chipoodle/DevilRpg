package com.chipoodle.devilrpg.entity.goal;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ForestryWorkPlannerTest {

    private ForestryWorkPlannerTest() {
    }

    public static void main(String[] args) {
        discoversColumnAGridWouldMiss();
        batchesAreBoundedAndUnique();
        frontierExpandsAndFinishes();
        resetStartsANewWorkArea();
        System.out.println("FORESTRY_WORK_PLANNER_TEST: OK");
    }

    private static void discoversColumnAGridWouldMiss() {
        ForestryWorkPlanner planner = new ForestryWorkPlanner();
        planner.reset(-7, 96);
        boolean found = false;
        for (int batch = 0; batch < 200 && !found; batch++) {
            for (ForestryWorkPlanner.Column c : planner.nextColumns(64, 102)) {
                if (c.x() == 33 && c.z() == 96) {
                    found = true;
                }
            }
        }
        check(found, "la frontera de un bloque debe visitar x=33");
    }

    private static void batchesAreBoundedAndUnique() {
        ForestryWorkPlanner planner = new ForestryWorkPlanner();
        planner.reset(0, 0);
        Set<ForestryWorkPlanner.Column> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            List<ForestryWorkPlanner.Column> batch = planner.nextColumns(64, 102);
            check(batch.size() <= 64, "lote mayor de 64");
            for (ForestryWorkPlanner.Column c : batch) {
                check(seen.add(c), "columna repetida " + c);
            }
        }
        check(!planner.isFinished(), "no debe terminar en 20 lotes");
    }

    private static void frontierExpandsAndFinishes() {
        ForestryWorkPlanner planner = new ForestryWorkPlanner();
        planner.reset(0, 0);
        Set<ForestryWorkPlanner.Column> seen = new HashSet<>();
        int batches = 0;
        while (!planner.isFinished() && batches < 10000) {
            seen.addAll(planner.nextColumns(256, 3));
            batches++;
        }
        check(planner.isFinished(), "la frontera debe terminar");
        check(seen.contains(new ForestryWorkPlanner.Column(3, 3)), "falta esquina 3,3");
        check(seen.contains(new ForestryWorkPlanner.Column(-3, -3)), "falta esquina -3,-3");
        check(seen.size() == 49, "anillos 0..3 deben ser 49 columnas, son " + seen.size());
    }

    private static void resetStartsANewWorkArea() {
        ForestryWorkPlanner planner = new ForestryWorkPlanner();
        planner.reset(0, 0);
        planner.nextColumns(10, 2);
        planner.reset(100, -50);
        List<ForestryWorkPlanner.Column> batch = planner.nextColumns(1, 2);
        check(batch.equals(List.of(new ForestryWorkPlanner.Column(100, -50))), "reset no reinicia el origen");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
