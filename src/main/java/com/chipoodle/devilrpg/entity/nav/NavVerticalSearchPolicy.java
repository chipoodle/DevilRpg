package com.chipoodle.devilrpg.entity.nav;

/** Bounded vertical probes for one horizontal neighbor. */
public final class NavVerticalSearchPolicy {
    private NavVerticalSearchPolicy() { }

    /**
     * Ordinary edges rise at most one block. BUILD consumes a same-level candidate and produces
     * feet+1, so candidates above feet+1 cannot be valid single edges. Same-column movement is
     * downward only; vertical ascent requires an explicit STEP_UP/BUILD column.
     */
    public static int[] levels(boolean sameColumn, int verticalRange, int maxSafeDrop) {
        int up = verticalRange >= 1 ? 1 : 0;
        int down = Math.max(0, Math.min(verticalRange, maxSafeDrop));
        if (sameColumn) {
            int[] out = new int[down];
            for (int i = 0; i < down; i++) out[i] = -(i + 1);
            return out;
        }
        int[] out = new int[down + 1 + up];
        int at = 0;
        for (int dy = -down; dy <= up; dy++) out[at++] = dy;
        return out;
    }
}
