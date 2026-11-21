package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

public final class NavBuildObjectivePolicyTest {
    public static void main(String[] args) {
        BlockPos feet = new BlockPos(13, 66, -8);
        BlockPos placement = feet.below();

        check("exact placement directly below feet is valid",
                NavBuildObjectivePolicy.isValid(feet, placement));
        check("missing placement is rejected",
                !NavBuildObjectivePolicy.isValid(feet, null));
        check("missing feet cell is rejected",
                !NavBuildObjectivePolicy.isValid(null, placement));
        check("placement one block too low is rejected",
                !NavBuildObjectivePolicy.isValid(feet, placement.below()));
        check("placement one block too high is rejected",
                !NavBuildObjectivePolicy.isValid(feet, placement.above()));
        check("lateral placement is rejected",
                !NavBuildObjectivePolicy.isValid(feet, placement.east()));
        check("same cell is not a placement support",
                !NavBuildObjectivePolicy.isValid(feet, feet));
        check("a different valid feet-placement pair remains independent",
                NavBuildObjectivePolicy.isValid(new BlockPos(20, 71, 20),
                        new BlockPos(20, 70, 20)));

        System.out.println("NAV_BUILD_OBJECTIVE_POLICY_TEST: OK (8 assertions)");
    }

    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
    }
}
