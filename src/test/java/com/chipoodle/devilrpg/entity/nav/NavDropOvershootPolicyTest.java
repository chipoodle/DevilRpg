package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · DROP que aterriza más abajo: se acepta el aterrizaje real solo si es físicamente seguro y el exceso
 * vertical cabe en la caída segura.
 */
public final class NavDropOvershootPolicyTest {
    public static void main(String[] args) {
        // Caso medido IDX5: planificado 597,74,469; real 597,71,470 (3 por debajo).
        check("idx5 measured overshoot landing is accepted",
                NavDropOvershootPolicy.acceptRealLanding(true, true, true, true,
                        597, 71, 470, 597, 74, 469, 3));
        check("landing two blocks below is accepted within safe drop",
                NavDropOvershootPolicy.acceptRealLanding(true, true, true, true,
                        100, 68, 200, 100, 70, 200, 3));
        check("excess beyond safe drop is rejected",
                !NavDropOvershootPolicy.acceptRealLanding(true, true, true, true,
                        100, 66, 200, 100, 70, 200, 3));
        check("landing ABOVE the planned cell is not an overshoot",
                !NavDropOvershootPolicy.acceptRealLanding(true, true, true, true,
                        100, 72, 200, 100, 70, 200, 3));
        check("horizontal drift greater than one is rejected",
                !NavDropOvershootPolicy.acceptRealLanding(true, true, true, true,
                        102, 69, 200, 100, 70, 200, 3));
        check("not grounded is rejected",
                !NavDropOvershootPolicy.acceptRealLanding(false, true, true, true,
                        100, 69, 200, 100, 70, 200, 3));
        check("unsupported landing is rejected",
                !NavDropOvershootPolicy.acceptRealLanding(true, true, false, true,
                        100, 69, 200, 100, 70, 200, 3));
        check("invalid feet cell is rejected",
                !NavDropOvershootPolicy.acceptRealLanding(true, false, true, true,
                        100, 69, 200, 100, 70, 200, 3));
        check("real AABB that does not fit is rejected",
                !NavDropOvershootPolicy.acceptRealLanding(true, true, true, false,
                        100, 69, 200, 100, 70, 200, 3));
        check("zero safe drop never accepts a lower landing",
                !NavDropOvershootPolicy.acceptRealLanding(true, true, true, true,
                        100, 69, 200, 100, 70, 200, 0));
        System.out.println("NAV_DROP_OVERSHOOT_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
