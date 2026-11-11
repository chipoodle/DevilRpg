package com.chipoodle.devilrpg.entity.goal;

public final class ForestryTaskControllerTest {

    private ForestryTaskControllerTest() {
    }

    public static void main(String[] args) {
        assignmentOwnsNavigation();
        reassignmentKeepsTargetUntilComplete();
        completeReturnsToScouting();
        completeIsIdempotent();
        workersAreIndependent();
        System.out.println("FORESTRY_TASK_CONTROLLER_TEST: OK");
    }

    private static void assignmentOwnsNavigation() {
        ForestryTaskController c = new ForestryTaskController();
        check(c.state() == ForestryTaskController.State.SCOUTING, "inicio no es SCOUTING");
        c.assign(10, 64, -3);
        check(c.hasAssignment(), "asignación perdida");
        check(c.ownsNavigation(), "la asignación no posee navegación");
        c.harvesting();
        check(c.state() == ForestryTaskController.State.HARVESTING, "no pasa a HARVESTING");
        c.replanting();
        check(c.state() == ForestryTaskController.State.REPLANTING, "no pasa a REPLANTING");
    }

    private static void reassignmentKeepsTargetUntilComplete() {
        ForestryTaskController c = new ForestryTaskController();
        c.assign(1, 63, 2);
        c.travelling();
        check(c.targetX() == 1 && c.targetY() == 63 && c.targetZ() == 2, "objetivo no retenido");
        c.assign(9, 65, 8);
        check(c.targetX() == 9 && c.targetZ() == 8, "reasignación no actualiza objetivo");
    }

    private static void completeReturnsToScouting() {
        ForestryTaskController c = new ForestryTaskController();
        c.assign(1, 63, 2);
        c.complete();
        check(c.state() == ForestryTaskController.State.SCOUTING, "complete no vuelve a SCOUTING");
        check(!c.hasAssignment() && !c.ownsNavigation(), "complete conserva asignación");
    }

    private static void completeIsIdempotent() {
        ForestryTaskController c = new ForestryTaskController();
        c.assign(4, 70, 9);
        c.complete();
        c.complete();
        check(c.state() == ForestryTaskController.State.SCOUTING, "complete repetido corrompe estado");
        check(!c.hasAssignment() && !c.ownsNavigation(), "complete repetido deja asignación");
    }

    private static void workersAreIndependent() {
        ForestryTaskController a = new ForestryTaskController();
        ForestryTaskController b = new ForestryTaskController();
        a.assign(1, 63, 2);
        check(b.state() == ForestryTaskController.State.SCOUTING, "dos trabajadores comparten asignación");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
