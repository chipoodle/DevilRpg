package com.chipoodle.devilrpg.agent;

public final class NavigationGatewayPolicyTest {

    public static void main(String[] args) {
        ForestryNavigationGateway.Mode off = ForestryNavigationGateway.Mode.OFF;
        ForestryNavigationGateway.Mode audit = ForestryNavigationGateway.Mode.AUDIT;
        ForestryNavigationGateway.Mode enforce = ForestryNavigationGateway.Mode.ENFORCE;

        check(NavigationGatewayPolicy.movement(enforce, true) == NavigationGatewayPolicy.Action.DELEGATE, "A1 authorized");
        check(NavigationGatewayPolicy.movement(enforce, false) == NavigationGatewayPolicy.Action.DENY, "A2 missing lease");
        check(NavigationGatewayPolicy.movement(enforce, false) == NavigationGatewayPolicy.Action.DENY, "A3 wrong task");
        check(NavigationGatewayPolicy.movement(enforce, false) == NavigationGatewayPolicy.Action.DENY, "A4 expired lease");
        check(NavigationGatewayPolicy.movement(enforce, false) == NavigationGatewayPolicy.Action.DENY, "A5 wrong agent");
        check(NavigationGatewayPolicy.stop(enforce, false, false, true, false) == NavigationGatewayPolicy.Action.DENY, "A6 stale release");
        check(NavigationGatewayPolicy.movement(off, false) == NavigationGatewayPolicy.Action.DELEGATE, "A7 OFF");
        check(NavigationGatewayPolicy.movement(audit, false) == NavigationGatewayPolicy.Action.DELEGATE, "A8 AUDIT delegates");

        check(NavigationGatewayPolicy.stop(enforce, true, false, true, true) == NavigationGatewayPolicy.Action.DELEGATE, "owner stop");
        check(NavigationGatewayPolicy.stop(enforce, false, true, true, true) == NavigationGatewayPolicy.Action.DENY, "new owner protected");
        check(NavigationGatewayPolicy.stop(enforce, false, false, false, false) == NavigationGatewayPolicy.Action.CLEANUP, "unattributed cleanup");
        check(NavigationGatewayPolicy.stop(enforce, false, false, true, true) == NavigationGatewayPolicy.Action.CLEANUP, "own stale cleanup");

        System.out.println("NAVIGATION_GATEWAY_POLICY_TEST: OK");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
