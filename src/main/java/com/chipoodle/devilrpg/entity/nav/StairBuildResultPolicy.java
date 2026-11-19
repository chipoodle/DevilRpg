package com.chipoodle.devilrpg.entity.nav;

/** Pure classification of the stair builder's result and its cadence side effect. */
public final class StairBuildResultPolicy {
    private StairBuildResultPolicy() {
    }

    public static StairBuildResult clasificar(boolean result, boolean stairPending, boolean accessCleared,
                                               int cooldownBefore, int cooldownAfter) {
        boolean cadenceWait = !result && cooldownBefore > 0
                && cooldownAfter == cooldownBefore - 1;
        if (cadenceWait) {
            return StairBuildResult.CADENCE_WAIT;
        }
        if (result && stairPending) {
            return StairBuildResult.STAIR_PLACED;
        }
        if (result && accessCleared) {
            return StairBuildResult.ACCESS_CLEARED;
        }
        if (!result) {
            return StairBuildResult.NO_PROGRESS;
        }
        return StairBuildResult.FAILED;
    }
}
