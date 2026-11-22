package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · la celda de pie se valida por colisión: una capa de nieve de 1 (colisión vacía) no la invalida.
 */
public final class NavFeetCellPolicyTest {
    public static void main(String[] args) {
        check("snow layer (no collision) is a valid stand",
                NavFeetCellPolicy.isValid(true, false, false, false, false, false, true));
        check("tall grass / fern (no collision) is a valid stand",
                NavFeetCellPolicy.isValid(true, false, false, false, false, false, true));
        check("solid block in the feet cell is rejected",
                !NavFeetCellPolicy.isValid(true, false, true, false, false, false, true));
        check("solid block in the head cell is rejected",
                !NavFeetCellPolicy.isValid(true, false, false, true, false, false, true));
        check("fluid in the feet cell is rejected",
                !NavFeetCellPolicy.isValid(true, false, false, false, true, false, true));
        check("hazard in the head cell is rejected",
                !NavFeetCellPolicy.isValid(true, false, false, false, false, true, true));
        check("missing support is rejected",
                !NavFeetCellPolicy.isValid(true, false, false, false, false, false, false));
        check("protected cell is rejected even when physically valid",
                !NavFeetCellPolicy.isValid(true, true, false, false, false, false, true));
        check("unloaded chunk is rejected",
                !NavFeetCellPolicy.isValid(false, false, false, false, false, false, true));
        System.out.println("NAV_FEET_CELL_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
