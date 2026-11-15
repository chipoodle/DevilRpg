package com.chipoodle.devilrpg.entity.nav;

public final class NavWaypointWindowPolicyTest {
    public static void main(String[] args) {
        comprobar("same-level objective stays local", NavWaypointWindowPolicy.directoAlcanzable(0, 3));
        comprobar("safe natural drop stays local", NavWaypointWindowPolicy.directoAlcanzable(-3, 3));
        comprobar("deep drop requires global route", !NavWaypointWindowPolicy.directoAlcanzable(-24, 3));
        comprobar("drop beyond safe limit requires global route", !NavWaypointWindowPolicy.directoAlcanzable(-4, 3));
        comprobar("legal jump-up stays local", NavWaypointWindowPolicy.directoAlcanzable(2, 3));
        comprobar("large climb requires global route", !NavWaypointWindowPolicy.directoAlcanzable(3, 3));
        System.out.println("NAV_WAYPOINT_WINDOW_POLICY_TEST: OK (6 assertions)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) throw new AssertionError(nombre);
    }
}
