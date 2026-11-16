package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

public final class NavRecoveryControllerTest {
    public static void main(String[] args) {
        NavRecoveryController controller = new NavRecoveryController();
        BlockPos a = new BlockPos(0, 64, 0);
        BlockPos b = new BlockPos(1, 64, 0);
        BlockPos c = new BlockPos(2, 64, 0);
        controller.recordReached(a);
        controller.recordReached(b);
        controller.recordReached(c);
        check("first failure only replans", controller.onNoProgress() == NavRecoveryController.Decision.REPLAN);
        check("second consecutive failure requests backtrack",
                controller.onNoProgress() == NavRecoveryController.Decision.BACKTRACK);
        check("chooses last physically reached cell, not current",
                b.equals(controller.chooseBacktrack(c, p -> true)));
        check("skips adjacent history and selects a useful older cell",
                a.equals(controller.chooseBacktrack(c, 2.25D, p -> true)));
        check("skips nearby cells even when the newest cell is valid",
                a.equals(controller.chooseBacktrack(c, 2.25D, p -> p.equals(a) || p.equals(b))));
        controller.markBacktrack(b);
        check("does not repeatedly backtrack in same failure cycle",
                controller.onNoProgress() == NavRecoveryController.Decision.REPLAN);
        controller.onMissionProgress();
        check("invalid historical cell skipped after rearm",
                a.equals(controller.chooseBacktrack(c, p -> p.equals(a))));
        check("mission progress rearms two-failure sequence",
                controller.onNoProgress() == NavRecoveryController.Decision.REPLAN
                        && controller.onNoProgress() == NavRecoveryController.Decision.BACKTRACK);
        for (int i = 0; i < 24; i++) {
            controller.recordReached(new BlockPos(100 + i, 64, 0));
        }
        check("physical history remains bounded at sixteen cells",
                controller.historySize() == NavRecoveryController.MAX_HISTORY);
        controller.reset();
        check("reset clears history", controller.historySize() == 0);
        System.out.println("NAV_RECOVERY_CONTROLLER_TEST: OK (10 assertions)");
    }
    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
    }
}
