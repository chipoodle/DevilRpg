package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Deterministic policy test for the bounded DROP edge-step. */
public final class NavDropEdgeStepPolicyTest {
    private static int assertions;

    public static void main(String[] args) {
        BlockPos landing = new BlockPos(1, 119, 1);
        AABB supported = new AABB(0.2, 120.0, 0.2, 0.8, 121.95, 0.8);
        AABB off = new AABB(1.2, 120.0, 1.2, 1.8, 121.95, 1.8);

        comprobar("start only for DROP",
                !NavDropEdgeStepPolicy.shouldStart(false, true, true, false, 1, 3,
                        true, true, true, true));
        comprobar("start needs finished path",
                !NavDropEdgeStepPolicy.shouldStart(true, false, true, false, 1, 3,
                        true, true, true, true));
        comprobar("start needs grounded",
                !NavDropEdgeStepPolicy.shouldStart(true, true, false, false, 1, 3,
                        true, true, true, true));
        comprobar("no start on landing",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, true, 0, 3,
                        true, true, true, true));
        comprobar("accept a valid vertical-only DROP at zero horizontal distance",
                NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 0, 3,
                        true, true, true, true));
        comprobar("reject negative horizontal distance",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, false, -1, 3,
                        true, true, true, true));
        comprobar("vertical-only DROP still requires grounded takeoff",
                !NavDropEdgeStepPolicy.shouldStart(true, true, false, false, 0, 3,
                        true, true, true, true));
        comprobar("vertical-only DROP still requires supported and fitting landing",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 0, 3,
                        true, true, false, true)
                        && !NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 0, 3,
                                true, true, true, false));
        comprobar("reject out of horizontal range",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 4, 3,
                        true, true, true, true));
        comprobar("reject unsupported takeoff",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 1, 3,
                        false, true, true, true));
        comprobar("reject unsupported landing",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 1, 3,
                        true, true, false, true));
        comprobar("accept valid adjacent drop",
                NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 1, 3,
                        true, true, true, true));
        comprobar("still supported while AABB overlaps platform",
                !NavDropEdgeStepPolicy.fullyOffSupport(supported, p -> p.equals(new BlockPos(0, 120, 0))));
        comprobar("fully off support when no column is supported",
                NavDropEdgeStepPolicy.fullyOffSupport(off, p -> false));
        comprobar("timeout is bounded", !NavDropEdgeStepPolicy.timedOut(60)
                && NavDropEdgeStepPolicy.timedOut(61));
        comprobar("no-progress is bounded", !NavDropEdgeStepPolicy.noProgress(20)
                && NavDropEdgeStepPolicy.noProgress(21));
        System.out.println("NAV_DROP_EDGE_STEP_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
