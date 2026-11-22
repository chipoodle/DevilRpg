package com.chipoodle.devilrpg.entity.nav;

/** A2 · contrato del escalón de ascenso (despejar, colocar, subir). */
public final class NavClimbStepPolicyTest {
    public static void main(String[] args) {
        check("clear phase while the face is solid",
                NavClimbStepPolicy.phase(true, false, false, false) == NavClimbStepPolicy.Phase.CLEAR);
        check("clear phase while the head is solid",
                NavClimbStepPolicy.phase(false, true, false, true) == NavClimbStepPolicy.Phase.CLEAR);
        check("clear phase while the top is solid",
                NavClimbStepPolicy.phase(false, false, true, false) == NavClimbStepPolicy.Phase.CLEAR);
        check("place phase when the whole face is clear and no step is placed",
                NavClimbStepPolicy.phase(false, false, false, false) == NavClimbStepPolicy.Phase.PLACE);
        check("move phase when the step is already placed",
                NavClimbStepPolicy.phase(false, false, false, true) == NavClimbStepPolicy.Phase.MOVE);

        check("air is clearable", NavClimbStepPolicy.clearable(false, false, false));
        check("breakable unprotected block is clearable", NavClimbStepPolicy.clearable(true, true, false));
        check("unbreakable block is not clearable", !NavClimbStepPolicy.clearable(true, false, false));
        check("protected block is not clearable even if breakable",
                !NavClimbStepPolicy.clearable(true, true, true));

        check("step is placeable on a cleared face with support",
                NavClimbStepPolicy.placeable(true, true, true, true, false, false));
        check("step is not placeable without support",
                !NavClimbStepPolicy.placeable(true, true, true, false, false, false));
        check("step is not placeable inside the mob AABB",
                !NavClimbStepPolicy.placeable(true, true, true, true, true, false));
        check("step is not placeable on a protected cell",
                !NavClimbStepPolicy.placeable(true, true, true, true, false, true));
        check("step is not placeable with a solid head cell",
                !NavClimbStepPolicy.placeable(true, false, true, true, false, false));

        check("landing verified only with exact grounded supported occupancy",
                NavClimbStepPolicy.landingVerified(true, true, true, true));
        check("landing not verified while airborne",
                !NavClimbStepPolicy.landingVerified(true, false, true, true));
        check("landing not verified without support",
                !NavClimbStepPolicy.landingVerified(true, true, false, true));
        check("landing not verified without AABB fit",
                !NavClimbStepPolicy.landingVerified(true, true, true, false));
        check("landing not verified in another cell",
                !NavClimbStepPolicy.landingVerified(false, true, true, true));
        System.out.println("NAV_CLIMB_STEP_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
