package com.chipoodle.devilrpg.entity.nav;

public final class NavCombatIntentPolicyTest {
    public static void main(String[] args) {
        comprobar("inside melee holds", NavCombatIntentPolicy.holdInMelee(4.0D));
        comprobar("outside melee does not hold", !NavCombatIntentPolicy.holdInMelee(4.01D));
        comprobar("new destination updates", NavCombatIntentPolicy.tacticalDestinationChanged(
                1, 2, 3, 1, 2, 4));
        comprobar("same destination is idempotent", !NavCombatIntentPolicy.tacticalDestinationChanged(
                1, 2, 3, 1, 2, 3));
        comprobar("near target inside tactical leash", NavCombatIntentPolicy.withinTacticalLeash(4.0D));
        comprobar("far target outside tactical leash", !NavCombatIntentPolicy.withinTacticalLeash(4.01D));
        System.out.println("NAV_COMBAT_INTENT_POLICY_TEST: OK (6 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
